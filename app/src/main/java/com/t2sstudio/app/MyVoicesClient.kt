package com.t2sstudio.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SavedUserVoice(val id: String, val name: String, val model: String, val previewUrl: String?, val localSamplePath: String? = null, val language: String = "en")

object MyVoicesClient {
    private const val VOICES_URL = "https://tts.ai/api/v1/user-voices/"

    private fun localProfilesFile(context: Context) = File(context.filesDir, "voice_profiles.json")

    fun list(context: Context, apiKey: String = ""): List<SavedUserVoice> {
        val file = localProfilesFile(context)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sample = item.optString("localSamplePath").takeIf { it.isNotBlank() }?.let(::File)
            if (sample == null || !sample.exists() || sample.length() <= 1024L) return@mapNotNull null
            SavedUserVoice(
                id = id,
                name = item.optString("name", "My Voice"),
                model = "Local recording",
                previewUrl = null,
                localSamplePath = sample.absolutePath,
                language = item.optString("language", "en").ifBlank { "en" }
            )
        }
    }

    fun createLocal(context: Context, name: String, audioUri: Uri, language: String): SavedUserVoice {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Enter a name for this voice profile." }
        val sampleDir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val id = "local-" + UUID.randomUUID().toString()
        val mime = context.contentResolver.getType(audioUri).orEmpty()
        val extension = when {
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("flac") -> "flac"
            mime.contains("mp4") || mime.contains("aac") -> "m4a"
            mime.contains("wav") || mime.contains("wave") -> "wav"
            else -> "audio"
        }
        val sample = File(sampleDir, "$id.$extension")
        context.contentResolver.openInputStream(audioUri)?.use { input ->
            sample.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected recording.")
        if (sample.length() < 1024L) {
            sample.delete()
            error("The recording is empty or too short.")
        }
        if (sample.length() > 20L * 1024 * 1024) {
            sample.delete()
            error("Voice recording must be 20 MB or smaller.")
        }
        val voice = SavedUserVoice(id, cleanName, "Local recording", null, sample.absolutePath, language)
        try {
            val profiles = JSONArray()
            list(context).filterNot { it.id == id }.forEach { existing ->
                profiles.put(JSONObject().apply {
                    put("id", existing.id); put("name", existing.name); put("language", existing.language)
                    put("localSamplePath", existing.localSamplePath)
                })
            }
            profiles.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language)
                put("localSamplePath", voice.localSamplePath)
            })
            localProfilesFile(context).writeText(profiles.toString())
        } catch (error: Exception) {
            sample.delete()
            throw error
        }
        return voice
    }

    fun deleteLocal(context: Context, id: String) {
        val current = list(context)
        val target = current.firstOrNull { it.id == id }
        val remaining = current.filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach { voice ->
            array.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language)
                put("localSamplePath", voice.localSamplePath)
            })
        }
        localProfilesFile(context).writeText(array.toString())
        target?.localSamplePath?.let { File(it).delete() }
    }

    @Suppress("unused")
    private fun legacyCloudList(context: Context, apiKey: String): List<SavedUserVoice> {
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
                val localSample = File(File(context.filesDir, "voice_samples"), "$id.m4a").takeIf { it.exists() && it.length() > 1024L }
                SavedUserVoice(id, item.optString("name", "My Voice"), item.optString("model_name", "chatterbox"),
                    item.optString("preview_url").takeIf { it.startsWith("https://") }, localSample?.absolutePath,
                    item.optString("language", "en").ifBlank { "en" })
            }
        } finally { connection.disconnect() }
    }

    @Suppress("unused")
    private fun legacyCloudCreate(context: Context, apiKey: String, name: String, audioUri: Uri, model: String, language: String): SavedUserVoice {
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
                field("language", language)
                field("model", model)
                field("consent_confirmed", "true")
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"file\"; filename=\"voice_sample.$extension\"\r\n".toByteArray())
                out.write("Content-Type: $mime\r\n\r\n".toByteArray())
                temp.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val response = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = response?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("Voice save failed (HTTP ${connection.responseCode}): $body")
            val json = JSONObject(body)
            val item = json.optJSONObject("voice") ?: json
            val id = item.optString("public_id").takeIf { it.isNotBlank() }
                ?: item.optString("voice_id").takeIf { it.isNotBlank() }
                ?: error("Provider did not return a saved voice ID: $body")
            val sampleDir = File(context.filesDir, "voice_samples").apply { mkdirs() }
            val savedSample = File(sampleDir, "$id.m4a")
            temp.copyTo(savedSample, overwrite = true)
            return SavedUserVoice(id, item.optString("name", name), item.optString("model_name", model),
                item.optString("preview_url").takeIf { it.startsWith("https://") }, savedSample.absolutePath,
                item.optString("language", language).ifBlank { language })
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }
}

fun storedTtsApiKey(context: Context): String = BuildConfig.TTS_API_KEY
