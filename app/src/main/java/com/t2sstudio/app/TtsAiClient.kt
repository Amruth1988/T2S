package com.t2sstudio.app

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class OnlineTtsVoice(
    val id: String,
    val name: String,
    val language: String,
    val gender: String,
    val licence: String = "",
    val attribution: String = "",
)

/**
 * Free, keyless India API Piper endpoint.
 * The provider currently allows model calls without a key until 15 Dec 2026.
 * It returns audio bytes directly; no paid account or billing setup is used.
 */
object TtsAiClient {
    private const val BASE = "https://api.dharun.dev/v1"

    private fun isAudioBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val head = String(bytes, 0, minOf(bytes.size, 12), Charsets.US_ASCII)
        return head.startsWith("ID3") || head.startsWith("RIFF") || head.startsWith("OggS") ||
            head.startsWith("fLaC") || ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0)
    }

    private fun validateAudio(file: File, format: String) {
        require(file.exists() && file.length() > 1024L) { "TTS service returned an empty or incomplete audio file." }
        val header = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
        val valid = when (format.lowercase(Locale.ROOT)) {
            "mp3" -> header.size >= 3 && (String(header, 0, 3, Charsets.US_ASCII) == "ID3" ||
                ((header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0))
            "wav" -> header.size >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
            "ogg" -> header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "OggS"
            else -> isAudioBytes(header)
        }
        require(valid) { "TTS service returned audio that does not match the requested $format format." }
    }

    fun listVoices(language: String): List<OnlineTtsVoice> {
        val connection = URL("$BASE/tts/voices").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(if (code == 401) "Free online TTS now requires a free India API key. No paid billing is required." else detail.ifBlank { "Voice catalogue unavailable (HTTP $code)." })
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val voices = json.optJSONArray("data") ?: return emptyList()
            return (0 until voices.length()).mapNotNull { index ->
                val item = voices.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val locale = item.optString("language")
                if (!locale.substringBefore('-').equals(language, ignoreCase = true)) return@mapNotNull null
                val nonCommercial = item.optBoolean("non_commercial", false)
                if (nonCommercial) return@mapNotNull null
                OnlineTtsVoice(
                    id = id,
                    name = item.optString("name").ifBlank { item.optString("speaker", id) },
                    language = locale,
                    gender = item.optString("gender", "Voice"),
                    licence = item.optString("licence"),
                    attribution = item.optString("attribution"),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    fun generate(
        text: String,
        voice: String,
        speed: Float,
        output: File,
        format: String = "mp3",
        apiKey: String? = null,
    ) {
        require(text.isNotBlank()) { "Enter text to generate speech." }
        require(text.length <= 3000) { "Free online TTS supports up to 3,000 characters per request." }
        require(voice.isNotBlank()) { "Choose an online voice first." }
        val safeFormat = when (format.lowercase(Locale.ROOT)) {
            "mp3" -> "mp3"
            "wav" -> "wav"
            "ogg" -> "ogg"
            else -> "mp3"
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("voice", voice)
            put("format", safeFormat)
            put("speed", speed.coerceIn(0.5f, 2f).toDouble())
        }.toString()
        val connection = URL("$BASE/tts").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            apiKey?.trim()?.takeIf { it.startsWith("india_live_") }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.setRequestProperty("Accept", when (safeFormat) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                else -> "audio/ogg"
            })
            // Optional free India API key is sent only in the Authorization header.
            // No paid billing credentials are used or stored in source code.
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(when (code) {
                    401 -> "Free online TTS requires a free India API key from 15 Dec 2026. No paid billing is required."
                    429 -> "Free online TTS rate limit reached. Please wait and try again."
                    else -> detail.ifBlank { "Online TTS failed (HTTP $code)." }
                })
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            require(bytes.size > 1024 && isAudioBytes(bytes)) { "Online TTS returned an invalid audio response." }
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
            validateAudio(output, safeFormat)
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
