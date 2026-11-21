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
    
    private const val BACKUP_VERSION = 1
    private const val MAX_BACKUP_BYTES = 100L * 1024L * 1024L

    private fun writeProfiles(context: Context, voices: List<SavedUserVoice>) {
        val array = JSONArray()
        voices.forEach { voice ->
            array.put(JSONObject().apply {
                put("id", voice.id)
                put("name", voice.name)
                put("language", voice.language)
                put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath)
                put("sarvamVoiceId", voice.sarvamVoiceId)
            })
        }
        val target = localProfilesFile(context)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(array.toString())
        if (target.exists() && !target.delete()) {
            temp.delete()
            error("Could not replace the voice profile index.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Could not save the restored voice profile index.")
        }
    }

    fun exportBackup(context: Context, destination: Uri): Int {
        val voices = list(context)
        context.contentResolver.openOutputStream(destination, "wt")?.use { raw ->
            java.util.zip.ZipOutputStream(raw.buffered()).use { zip ->
                val manifest = JSONObject().put("version", BACKUP_VERSION)
                val entries = JSONArray()
                voices.forEachIndexed { index, voice ->
                    val sample = voice.localSamplePath?.let(::File)
                    val entryName = if (sample != null && sample.exists() && sample.length() > 1024L) {
                        val ext = sample.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
                        "samples/voice_$index.$ext"
                    } else null
                    entries.put(JSONObject().apply {
                        put("id", voice.id)
                        put("name", voice.name)
                        put("language", voice.language)
                        put("gender", voice.gender)
                        put("sarvamVoiceId", voice.sarvamVoiceId)
                        put("sampleEntry", entryName ?: JSONObject.NULL)
                    })
                    if (entryName != null && sample != null) {
                        zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                        sample.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                manifest.put("voices", entries)
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } ?: error("Could not create the backup file.")
        return voices.size
    }

    fun importBackup(context: Context, source: Uri): Pair<Int, Int> {
        val files = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        context.contentResolver.openInputStream(source)?.use { raw ->
            java.util.zip.ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    count++
                    require(count <= 60) { "Backup contains too many files." }
                    if (!entry.isDirectory) {
                        val name = entry.name
                        require(name == "manifest.json" ||
                            (name.startsWith("samples/") && !name.removePrefix("samples/").contains("/") &&
                                name.removePrefix("samples/").matches(Regex("[A-Za-z0-9_.-]{1,80}")))) {
                            "Backup contains an invalid file path."
                        }
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            totalBytes += n
                            require(totalBytes <= MAX_BACKUP_BYTES) { "Backup is larger than 100 MB." }
                            bytes.write(buffer, 0, n)
                        }
                        require(!files.containsKey(name)) { "Backup contains duplicate file entries." }
                        files[name] = bytes.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Could not open the selected backup.")
        val manifestBytes = files["manifest.json"] ?: error("This is not a valid T2S voice backup.")
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        require(manifest.optInt("version") == BACKUP_VERSION) { "Unsupported T2S backup version." }
        val items = manifest.optJSONArray("voices") ?: error("Backup has no voice list.")
        require(items.length() <= 50) { "Backup contains too many voice profiles." }

        val current = list(context).toMutableList()
        val existingIds = current.map { it.id }.toMutableSet()
        val sampleDir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val createdFiles = mutableListOf<File>()
        var imported = 0
        var skipped = 0
        try {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val originalId = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                if (existingIds.contains(originalId)) {
                    skipped++
                    continue
                }
                val sampleEntry = item.optString("sampleEntry").takeIf { it.isNotBlank() && it != "null" }
                var localPath: String? = null
                if (sampleEntry != null) {
                    require(sampleEntry.startsWith("samples/") && !sampleEntry.removePrefix("samples/").contains("/")) {
                        "Backup sample path is invalid."
                    }
                    val bytes = files[sampleEntry] ?: error("A voice recording is missing from the backup.")
                    require(bytes.size in 1025..(20 * 1024 * 1024)) { "A voice recording has an invalid size." }
                    val extension = sampleEntry.substringAfterLast('.', "audio")
                        .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
                    val sample = File(sampleDir, "restore_${UUID.randomUUID()}.$extension")
                    sample.writeBytes(bytes)
                    createdFiles.add(sample)
                    localPath = sample.absolutePath
                }
                val importedId = if (existingIds.contains(originalId)) "restore-${UUID.randomUUID()}" else originalId
                current.add(SavedUserVoice(
                    id = importedId,
                    name = item.optString("name", "My Voice").ifBlank { "My Voice" },
                    localSamplePath = localPath,
                    language = item.optString("language", "en").ifBlank { "en" },
                    gender = item.optString("gender", "Unknown").ifBlank { "Unknown" },
                    sarvamVoiceId = item.optString("sarvamVoiceId", "")
                ))
                existingIds.add(importedId)
                imported++
            }
            writeProfiles(context, current)
        } catch (error: Exception) {
            createdFiles.forEach { it.delete() }
            throw error
        }
        return imported to skipped
    }

    fun list(context: Context): List<SavedUserVoice> {
        val file = localProfilesFile(context)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val samplePath = item.optString("localSamplePath").takeIf { it.isNotBlank() }
            // Keep profile metadata visible even when its recording is missing.
            // Hiding it here made profiles disappear without explaining why.
            SavedUserVoice(
                id = id,
                name = item.optString("name", "My Voice"),
                localSamplePath = samplePath?.let { File(it).absolutePath },
                language = item.optString("language", "en").ifBlank { "en" },
                gender = item.optString("gender", "Unknown").ifBlank { "Unknown" },
                sarvamVoiceId = item.optString("sarvamVoiceId", "")
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
                    put("localSamplePath", existing.localSamplePath); put("sarvamVoiceId", existing.sarvamVoiceId)
                })
            }
            profiles.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language); put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath); put("sarvamVoiceId", voice.sarvamVoiceId)
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
                put("localSamplePath", voice.localSamplePath); put("sarvamVoiceId", voice.sarvamVoiceId)
            })
        }
        localProfilesFile(context).writeText(array.toString())
        target?.localSamplePath?.let { File(it).delete() }
    }
}
