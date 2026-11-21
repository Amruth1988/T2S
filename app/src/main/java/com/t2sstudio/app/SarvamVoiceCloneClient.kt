package com.t2sstudio.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Sarvam Instant Voice Cloning REST client. Client-side keys remain extractable at runtime. */
object SarvamVoiceCloneClient {
    private const val PREFS = "t2s_sarvam_settings"
    private const val ALIAS = "t2s_sarvam_api_key_v1"
    private const val BASE = "https://api.sarvam.ai"

    fun saveApiKey(context: Context, value: String) {
        val key = value.trim()
        require(key.startsWith("sk_") && key.length >= 20) { "Enter a valid Sarvam API key." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("key", packed).commit())
    }

    fun hasApiKey(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("key", null).orEmpty().isNotBlank()

    private fun apiKey(context: Context): String {
        val packed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("key", null)
            ?: error("Add your Sarvam API key in My Voices first.")
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun createVoice(context: Context, sample: File, name: String, language: String, gender: String): String {
        require(sample.isFile && sample.length() > 1024L) { "Voice recording is missing or empty." }
        require(language in setOf("te", "en", "hi"))
        val boundary = "T2SCreate" + System.currentTimeMillis()
        val c = connection(context, "/voices/create", boundary)
        try {
            c.outputStream.buffered().use { out ->
                fun field(k: String, v: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n".toByteArray())
                }
                field("name", name.take(100))
                field("language", when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" })
                if (gender == "Male" || gender == "Female") field("gender", gender.lowercase())
                field("remove_background_noise", "true")
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.${sample.extension}\"\r\nContent-Type: ${mime(sample)}\r\n\r\n".toByteArray())
                sample.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val json = response(c)
            return json.optJSONObject("data")?.optString("voice_id")?.takeIf { it.startsWith("svc-") }
                ?: error("Sarvam did not return a saved voice ID.")
        } finally { c.disconnect() }
    }

    fun generate(context: Context, voiceId: String, text: String, language: String, output: File) {
        require(voiceId.startsWith("svc-")) { "This profile has not been registered with Sarvam. Save it again." }
        require(text.isNotBlank() && text.length <= 1000) { "Enter 1–1,000 characters for Sarvam voice cloning." }
        require(language == "te" || language == "en") { "Sarvam cloning supports Telugu and English here." }
        val boundary = "T2SGenerate" + System.currentTimeMillis()
        val c = connection(context, "/voices/clone", boundary)
        try {
            c.outputStream.buffered().use { out ->
                fun field(k: String, v: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n".toByteArray())
                }
                field("voice_id", voiceId)
                field("text", text)
                field("language_code", if (language == "te") "te-IN" else "en-IN")
                out.write("--$boundary--\r\n".toByteArray())
            }
            val json = response(c)
            val audio = json.optString("audio_b64").ifBlank { json.optString("audio") }
            require(audio.isNotBlank()) { "Sarvam returned no audio." }
            val bytes = Base64.decode(audio, Base64.DEFAULT)
            require(bytes.size > 1024 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Sarvam returned an invalid WAV file." }
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
        } catch (e: Exception) { output.delete(); throw e }
        finally { c.disconnect() }
    }

    private fun connection(context: Context, path: String, boundary: String): HttpURLConnection {
        return (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 180_000; doOutput = true
            setRequestProperty("api-subscription-key", apiKey(context))
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
    }

    private fun response(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
            when (code) {
                401 -> error("Sarvam API key rejected (401). Check the key in My Voices.")
                402 -> error("Sarvam free credits are exhausted or voice cloning is not enabled (402). Stopped without requesting a top-up.")
                403 -> error("Sarvam denied this request (403). Check voice-cloning access and account limits.")
                429 -> error("Sarvam rate limit reached. Wait and retry.")
                else -> error("Sarvam request failed (HTTP $code): ${detail.ifBlank { body.take(200) }}")
            }
        }
        return JSONObject(body)
    }

    private fun mime(file: File) = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"; "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"
        "m4a", "mp4" -> "audio/mp4"; "aac" -> "audio/aac"; else -> "application/octet-stream"
    }
}
