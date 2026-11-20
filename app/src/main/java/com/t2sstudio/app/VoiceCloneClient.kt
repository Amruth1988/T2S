package com.t2sstudio.app

import android.content.Context
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
        }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ENDPOINT, clean).commit()) { "Could not save the voice-cloning server URL." }
    }
}

/** Multipart client for the optional, self-hosted Volt-TTS service. */
object VoiceCloneClient {
    fun generate(
        serverUrl: String,
        text: String,
        language: String,
        referenceAudio: File,
        output: File,
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 180_000,
    ) {
        require(text.isNotBlank()) { "Enter text to generate speech." }
        require(text.length <= 3000) { "Voice cloning supports up to 3,000 characters per request." }
        require(language == "te" || language == "en") { "Voice cloning currently supports Telugu and English." }
        require(referenceAudio.isFile && referenceAudio.length() > 1024L) { "The saved voice recording is missing or empty. Re-record it in My Voices." }
        val base = serverUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Configure your self-hosted voice-cloning server in My Voices first."
        }
        val boundary = "T2SVoiceClone" + System.currentTimeMillis().toString()
        val connection = (URL("$base/v1/clone/tts").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "audio/wav")
            BufferedOutputStream(connection.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.write("\r\n".toByteArray())
                }
                field("text", text)
                field("language", language)
                field("speed", "1.0")
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"audio\"; filename=\"reference.wav\"\r\n".toByteArray())
                out.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
                referenceAudio.inputStream().use { input -> input.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
                out.flush()
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(detail.ifBlank { "Voice-cloning server failed (HTTP $code)." })
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            require(bytes.size > 1024 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") {
                "Voice-cloning server returned an invalid WAV file."
            }
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
        val connection = URL("$base/health").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..299) { body.ifBlank { "Server health check failed (HTTP $code)." } }
            require(body.contains("\"status\"") && body.contains("\"ok\"")) {
                "This server does not look like a T2S voice-cloning server."
            }
            return "Voice-cloning server connected."
        } finally {
            connection.disconnect()
        }
    }
}
