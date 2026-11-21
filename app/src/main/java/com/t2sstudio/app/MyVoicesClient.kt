package com.t2sstudio.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class SavedUserVoice(val id: String, val name: String, val localSamplePath: String? = null, val language: String = "en", val gender: String = "Unknown", val sarvamVoiceId: String = "")

object MyVoicesClient {
    private fun localProfilesFile(context: Context) = File(context.filesDir, "voice_profiles.json")

    fun list(context: Context): List<SavedUserVoice> {
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
                localSamplePath = sample.absolutePath,
                language = item.optString("language", "en").ifBlank { "en" },
                gender = item.optString("gender", "Unknown").ifBlank { "Unknown" },\n                sarvamVoiceId = item.optString("sarvamVoiceId", "")
            )
        }
    }

    fun createLocal(context: Context, name: String, audioUri: Uri, language: String, gender: String): SavedUserVoice {
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
        try {
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                sample.outputStream().buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= 20L * 1024 * 1024) {
                            "Voice recording must be 20 MB or smaller."
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Could not read the selected recording.")
            require(sample.length() >= 1024L) { "The recording is empty or too short." }

            val cleanGender = gender.trim().also { require(it == "Male" || it == "Female" || it == "Other") { "Select a valid voice gender." } }
            val voice = SavedUserVoice(id, cleanName, sample.absolutePath, language, cleanGender)
            val profiles = JSONArray()
            list(context).filterNot { it.id == id }.forEach { existing ->
                profiles.put(JSONObject().apply {
                    put("id", existing.id); put("name", existing.name); put("language", existing.language); put("gender", existing.gender)
                    put("localSamplePath", existing.localSamplePath)
                })
            }
            profiles.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language); put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath)
            })
            localProfilesFile(context).writeText(profiles.toString())
            return voice
        } catch (error: Exception) {
            sample.delete()
            throw error
        }
    }

    fun attachSarvamVoiceId(context: Context, id: String, voiceId: String) {
        require(voiceId.startsWith("svc-")) { "Invalid Sarvam voice ID." }
        val profiles = JSONArray()
        var found = false
        list(context).forEach { voice ->
            val updated = if (voice.id == id) voice.copy(sarvamVoiceId = voiceId).also { found = true } else voice
            profiles.put(JSONObject().apply {
                put("id", updated.id); put("name", updated.name); put("language", updated.language)
                put("gender", updated.gender); put("localSamplePath", updated.localSamplePath)
                put("sarvamVoiceId", updated.sarvamVoiceId)
            })
        }
        require(found) { "Saved voice profile could not be found." }
        localProfilesFile(context).writeText(profiles.toString())
    }

    fun deleteLocal(context: Context, id: String) {
        val current = list(context)
        val target = current.firstOrNull { it.id == id }
        val remaining = current.filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach { voice ->
            array.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language); put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath)
            })
        }
        localProfilesFile(context).writeText(array.toString())
        target?.localSamplePath?.let { File(it).delete() }
    }
}
