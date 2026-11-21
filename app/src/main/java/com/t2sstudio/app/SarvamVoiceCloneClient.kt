package com.t2sstudio.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
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
    // Replace this placeholder with the deployed Google Apps Script /exec URL.
    private const val SCRIPT_URL = "https://script.google.com/macros/s/PASTE_DEPLOYMENT_ID/exec"

    private fun centralCall(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> {
        require(!SCRIPT_URL.contains("PASTE_DEPLOYMENT_ID")) {
            "T2S shared Sarvam gateway is not configured yet. Deploy the Apps Script gateway and add its /exec URL."
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Sign in to T2S before using shared Sarvam voices.")
        val token = com.google.android.gms.tasks.Tasks.await(user.getIdToken(false)).token
            ?: error("Could not verify your T2S sign-in. Sign in again.")
        val action = when (name) {
            "listCentralSarvamVoices" -> "list"
            "createCentralSarvamVoice" -> "create"
            "generateCentralSarvamClone" -> "generate"
            else -> error("Unsupported shared Sarvam operation.")
        }
        val payload = JSONObject().put("action", action).put("idToken", token)
        data.forEach { (key, value) -> payload.put(key, value) }
        var url = URL(SCRIPT_URL)
        var method = "POST"
        repeat(5) { attempt ->
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30000
                readTimeout = 120000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (method == "POST") connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("T2S gateway redirect did not include a destination.")
                    url = URL(url, location)
                    method = "GET"
                    return@repeat
                }
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = runCatching { JSONObject(body) }.getOrElse {
                    error("T2S gateway returned an invalid response (HTTP $status).")
                }
                if (status !in 200..299) error("T2S gateway request failed (HTTP $status).")
                if (result.has("error")) error(result.optString("error", "Shared Sarvam request failed."))
                val map = mutableMapOf<String, Any?>()
                result.keys().forEach { key ->
                    map[key] = if (result.isNull(key)) null else result.get(key)
                }
                return map
            } finally {
                connection.disconnect()
            }
        }
        error("T2S gateway redirect limit exceeded.")
    }

    fun testCentralConnection(): String {
        val result = centralCall("listCentralSarvamVoices")
        require(result.containsKey("voices")) { "T2S server did not return the Sarvam voice catalogue." }
        return "T2S shared Sarvam connection is ready."
    }

    fun createCentralVoice(sample: File, name: String, language: String, gender: String): String {
        require(sample.isFile && sample.length() in 1024L..6_000_000L) {
            "Voice sample must be between 1 KB and 6 MB for secure upload."
        }
        val mimeType = when (sample.extension.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            else -> error("Use WAV, MP3, FLAC, M4A or AAC audio.")
        }
        val locale = when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" }
        val result = centralCall("createCentralSarvamVoice", mapOf(
            "name" to name,
            "language" to locale,
            "gender" to gender,
            "mimeType" to mimeType,
            "sampleBase64" to Base64.encodeToString(sample.readBytes(), Base64.NO_WRAP)
        ))
        return result["voiceId"]?.toString()?.takeIf { it.startsWith("svc-") }
            ?: error("T2S server did not return a saved Sarvam voice ID.")
    }

    fun generateCentral(voiceId: String, text: String, language: String, output: File) {
        require(voiceId.startsWith("svc-")) { "This profile has not been registered with Sarvam." }
        require(text.isNotBlank() && text.length <= 1000) { "Enter 1–1,000 characters." }
        val result = centralCall("generateCentralSarvamClone", mapOf(
            "voiceId" to voiceId,
            "text" to text,
            "language" to if (language == "te") "te-IN" else "en-IN"
        ))
        val encoded = result["audioBase64"]?.toString().orEmpty()
        require(encoded.isNotBlank()) { "T2S server returned no audio." }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size > 1024 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Sarvam returned invalid WAV audio." }
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)
    }

    fun saveApiKey(context: Context, value: String) {
        val key = value.trim()
        require(key.length >= 8) { "Enter the complete Sarvam API key from your Sarvam dashboard." }
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

    fun testConnection(context: Context): String {
        val connection = (URL("$BASE/voices").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("api-subscription-key", apiKey(context))
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                when (code) {
                    401 -> error("Sarvam API key rejected (401). Check the key.")
                    403 -> error("Sarvam denied access (403). Check account and voice-cloning API access.")
                    402 -> error("Sarvam account has no available credits (402). No top-up was requested.")
                    else -> error("Sarvam connection failed (HTTP $code): ${body.take(200)}")
                }
            }
            JSONObject(body)
            return "Sarvam API connected. Saved-voice endpoint responded successfully."
        } finally { connection.disconnect() }
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
