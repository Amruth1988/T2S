package com.t2sstudio.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Stores the optional self-hosted voice-cloning server URL (not an API credential). */
object VoiceCloneServerSettings {
    private const val PREFS = "t2s_voice_clone_settings"
    private const val ENDPOINT = "server_url"

    fun get(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ENDPOINT, "").orEmpty()

    fun save(context: Context, value: String) {
        val clean = value.trim().trimEnd('/')
        if (clean.isNotEmpty()) {
            val parsed = URL(clean)
            require(parsed.protocol == "https" || parsed.protocol == "http") {
                "Server URL must start with https:// or http://."
            }
            require(parsed.host.isNotBlank()) { "Enter a valid server URL." }
            require(parsed.protocol == "https" || parsed.host == "localhost" || parsed.host == "127.0.0.1") {
                "Use HTTPS for a remote server. Plain HTTP is allowed only for localhost."
            }
        }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ENDPOINT, clean).commit()) { "Could not save the voice-cloning server URL." }
    }
}

/**
 * Client for the documented OmniVoiceTTS self-hosted API.
 * Uses POST /tts/generate-upload with multipart fields:
 *   audio = reference sample
 *   request = JSON generation options
 */
object VoiceCloneClient {
    fun generate(
        serverUrl: String,
        text: String,
        language: String,
        referenceAudio: File,
        output: File,
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 300_000,
    ) {
        require(text.isNotBlank()) { "Enter text to generate speech." }
        require(text.length <= 3000) { "Voice cloning supports up to 3,000 characters per request." }
        require(language == "te" || language == "en") { "Voice cloning currently supports Telugu and English." }
        require(referenceAudio.isFile && referenceAudio.length() > 1024L) {
            "The saved voice recording is missing or empty. Re-record it in My Voices."
        }
        val base = serverUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Configure your self-hosted voice-cloning server in My Voices first."
        }
        val parsedBase = URL(base)
        require(parsedBase.protocol == "https" || parsedBase.host == "localhost" || parsedBase.host == "127.0.0.1") {
            "Use HTTPS for a remote server. Plain HTTP is allowed only for localhost."
        }

        val boundary = "T2SVoiceClone" + System.currentTimeMillis().toString()
        val connection = URL("$base/tts/generate-upload").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "audio/wav")

            val requestJson = JSONObject().apply {
                put("text", text)
                put("language", if (language == "te") "Telugu" else "English")
                put("output_format", "wav")
            }.toString()

            BufferedOutputStream(connection.outputStream).use { out ->
                fun write(value: String) = out.write(value.toByteArray(Charsets.UTF_8))
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"request\"\r\n")
                write("Content-Type: application/json; charset=utf-8\r\n\r\n")
                write(requestJson)
                write("\r\n--$boundary\r\n")
                val filename = referenceAudio.name.replace("\"", "")
                val mime = when (referenceAudio.extension.lowercase()) {
                    "wav" -> "audio/wav"
                    "mp3" -> "audio/mpeg"
                    "m4a", "mp4" -> "audio/mp4"
                    "flac" -> "audio/flac"
                    "ogg" -> "audio/ogg"
                    else -> "application/octet-stream"
                }
                write("Content-Disposition: form-data; name=\"audio\"; filename=\"$filename\"\r\n")
                write("Content-Type: $mime\r\n\r\n")
                referenceAudio.inputStream().use { input -> input.copyTo(out) }
                write("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(explainServerError(code, detail))
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            require(bytes.size > 1024 &&
                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
            ) { "Voice server did not return a valid WAV file. Check that the configured server supports /tts/generate-upload." }
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun checkServer(serverUrl: String): String {
        val base = serverUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) { "Enter a server URL first." }
        val parsedBase = URL(base)
        require(parsedBase.protocol == "https" || parsedBase.host == "localhost" || parsedBase.host == "127.0.0.1") {
            "Use HTTPS for a remote server. Plain HTTP is allowed only for localhost."
        }
        val connection = URL("$base/tts/ping").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..299) {
                explainServerError(code, body)
            }
            return "OmniVoiceTTS server is reachable. Model readiness is checked when speech is generated."
        } finally {
            connection.disconnect()
        }
    }

    private fun explainServerError(code: Int, detail: String): String {
        val clean = detail.trim().take(500)
        return when (code) {
            404 -> "Voice server endpoint not found (HTTP 404). Use an OmniVoiceTTS server that provides /tts/generate-upload and /tts/ping."
            413 -> "Reference recording is too large for the server upload limit."
            422 -> "Voice server rejected the request: " + clean.ifBlank { "check text, language, and reference audio." }
            503 -> "Voice server is not ready or is loading its speech model. Wait and retry."
            else -> clean.ifBlank { "Voice-cloning server failed (HTTP $code)." }
        }
    }
}
