package com.t2sstudio.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SavedUserVoice(val id: String, val name: String, val model: String, val previewUrl: String?)

object MyVoicesClient {
    private const val VOICES_URL = "https://tts.ai/api/v1/user-voices/"

    fun list(apiKey: String): List<SavedUserVoice> {
        val connection = URL(VOICES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            if (connection.responseCode !in 200..299) error("Could not load saved voices (HTTP ${connection.responseCode})")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val array = json.optJSONArray("voices") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("public_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SavedUserVoice(id, item.optString("name", "My Voice"), item.optString("model_name", "chatterbox"),
                    item.optString("preview_url").takeIf { it.startsWith("https://") })
            }
        } finally { connection.disconnect() }
    }

    fun create(context: Context, apiKey: String, name: String, audioUri: Uri, model: String): SavedUserVoice {
        val mime = context.contentResolver.getType(audioUri) ?: "audio/wav"
        val extension = when {
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("flac") -> "flac"
            mime.contains("mp4") || mime.contains("aac") -> "m4a"
            else -> "wav"
        }
        val temp = File(context.cacheDir, "voice_sample_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(audioUri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: error("Could not read selected audio file")
        if (temp.length() > 20L * 1024 * 1024) { temp.delete(); error("Voice sample must be 20 MB or smaller.") }
        if (temp.length() < 1024) { temp.delete(); error("Voice sample is too short or empty.") }

        val boundary = "----T2SVoice${UUID.randomUUID().toString().replace("-", "")}"
        val connection = URL(VOICES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                fun field(key: String, value: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n".toByteArray())
                }
                field("name", name)
                field("model_name", model)
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"reference_audio\"; filename=\"voice_sample.$extension\"\r\n".toByteArray())
                out.write("Content-Type: $mime\r\n\r\n".toByteArray())
                temp.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val response = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = response?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("Voice save failed (HTTP ${connection.responseCode}): $body")
            val json = JSONObject(body)
            val item = json.optJSONObject("voice") ?: json
            val id = item.optString("public_id").takeIf { it.isNotBlank() } ?: error("Provider did not return a saved voice ID.")
            return SavedUserVoice(id, item.optString("name", name), item.optString("model_name", model),
                item.optString("preview_url").takeIf { it.startsWith("https://") })
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }
}

fun storedTtsApiKey(context: Context): String = BuildConfig.TTS_API_KEY
