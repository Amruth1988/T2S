package com.t2sstudio.app

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Vakyam Raaga TTS client. Requires the user's own free-tier key. */
object VakyamTtsClient {
    private const val BASE = "https://api.vakyam.ai/v1"

    fun listVoices(language: String, apiKey: String): List<OnlineTtsVoice> {
        require(apiKey.startsWith("vak_live_")) { "Add your Vakyam API key in Profile → Online TTS access." }
        val connection = (URL("$BASE/voices?group_by=language").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) error(errorMessage(connection, code))
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val locale = when (language.lowercase(Locale.ROOT)) {
                "te" -> "te-IN"
                "hi" -> "hi-IN"
                else -> "en-IN"
            }
            val voices = root.optJSONArray(locale) ?: root.optJSONObject("data")?.optJSONArray(locale) ?: return emptyList()
            return (0 until voices.length()).mapNotNull { index ->
                val item = voices.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("voice_name").ifBlank { item.optString("name") }
                if (name.isBlank()) return@mapNotNull null
                OnlineTtsVoice(
                    id = name,
                    name = name,
                    language = locale,
                    gender = item.optString("gender", "Unknown").replaceFirstChar { it.uppercase() },
                    licence = "Vakyam free plan",
                    attribution = "Vakyam Raaga",
                    provider = "Vakyam"
                )
            }
        } finally { connection.disconnect() }
    }

    fun generate(text: String, voice: String, language: String, speed: Float, output: File, apiKey: String, format: String = "mp3") {
        require(text.isNotBlank()) { "Enter text to generate speech." }
        require(text.length <= 3000) { "Vakyam free TTS supports up to 3,000 characters per request." }
        require(apiKey.startsWith("vak_live_")) { "Add your Vakyam API key in Profile → Online TTS access." }
        val locale = when (language.lowercase(Locale.ROOT)) {
            "te" -> "te-IN"
            "hi" -> "hi-IN"
            else -> "en-IN"
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", "raaga-v1")
            put("voice", voice)
            put("language", locale)
            put("output_format", if (format.equals("wav", ignoreCase = true)) "wav" else "mp3")
            put("speed", speed.coerceIn(0.5f, 2f).toDouble())
        }
        val connection = URL("$BASE/tts/generate").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) error(errorMessage(connection, code))
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val encoded = json.optString("audio")
            require(encoded.isNotBlank()) { "Vakyam returned an empty audio response." }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val isWav = format.equals("wav", ignoreCase = true) &&
                bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
            val isMp3 = bytes.size > 1024 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()
            require(bytes.size > 1024 && (isWav || isMp3)) { "Vakyam returned invalid audio for the requested format." }
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally { connection.disconnect() }
    }

    private fun errorMessage(connection: HttpURLConnection, code: Int): String {
        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val detail = runCatching {
            val json = JSONObject(body)
            json.optString("message").ifBlank { json.optString("detail") }
        }.getOrDefault("")
        return when (code) {
            401, 403 -> detail.ifBlank { "Vakyam rejected the API key. Check Profile → Online TTS access." }
            402 -> detail.ifBlank { "Vakyam free monthly character allowance is exhausted." }
            422 -> detail.ifBlank { "Vakyam could not validate this voice and language combination." }
            429 -> detail.ifBlank { "Vakyam rate limit reached. Try again shortly." }
            else -> detail.ifBlank { "Vakyam request failed (HTTP $code)." }
        }
    }
}
