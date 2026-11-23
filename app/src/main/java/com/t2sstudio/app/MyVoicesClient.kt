package com.t2sstudio.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class SavedUserVoice(val id: String, val name: String, val localSamplePath: String? = null, val language: String = "en", val gender: String = "Unknown", val sarvamVoiceId: String = "", val firebaseSynced: Boolean = false)

object MyVoicesClient {
    private fun accountVoiceDirectory(context: Context, uid: String): File {
        require(uid.isNotBlank()) { "Sign in before accessing saved voice profiles." }
        val accountKey = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "accounts/$accountKey/voices").apply { mkdirs() }
    }

    private fun localProfilesFile(context: Context, uid: String) =
        File(accountVoiceDirectory(context, uid), "voice_profiles.json")

    private fun sampleDirectory(context: Context, uid: String) =
        File(accountVoiceDirectory(context, uid), "samples").apply { mkdirs() }
    
    private const val BACKUP_VERSION = 1
    private const val MAX_BACKUP_BYTES = 100L * 1024L * 1024L

    private fun writeProfiles(context: Context, uid: String, voices: List<SavedUserVoice>) {
        val array = JSONArray()
        voices.forEach { voice ->
            array.put(JSONObject().apply {
                put("id", voice.id)
                put("name", voice.name)
                put("language", voice.language)
                put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath)
                put("sarvamVoiceId", voice.sarvamVoiceId)
                put("firebaseSynced", voice.firebaseSynced)
            })
        }
        val target = localProfilesFile(context, uid)
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

    fun exportBackup(context: Context, uid: String, destination: Uri): Int {
        val voices = list(context, uid)
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
                        put("firebaseSynced", voice.firebaseSynced)
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

    fun importBackup(context: Context, uid: String, source: Uri): Pair<Int, Int> {
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

        val current = list(context, uid).toMutableList()
        val existingIds = current.map { it.id }.toMutableSet()
        val sampleDir = sampleDirectory(context, uid)
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
                    sarvamVoiceId = item.optString("sarvamVoiceId", ""),
                    firebaseSynced = item.optBoolean("firebaseSynced", false)
                ))
                existingIds.add(importedId)
                imported++
            }
            writeProfiles(context, uid, current)
        } catch (error: Exception) {
            createdFiles.forEach { it.delete() }
            throw error
        }
        return imported to skipped
    }

    fun list(context: Context, uid: String): List<SavedUserVoice> {
        val file = localProfilesFile(context, uid)
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
                sarvamVoiceId = item.optString("sarvamVoiceId", ""),
                firebaseSynced = item.optBoolean("firebaseSynced", false)
            )
        }
    }

    fun createLocal(context: Context, uid: String, name: String, audioUri: Uri, language: String, gender: String): SavedUserVoice {
        val cleanName = name.trim()
        require(cleanName.length in 2..40) { "Voice name must be between 2 and 40 characters." }
        require(cleanName.none { it.isISOControl() }) { "Voice name contains invalid characters." }
        val cleanLanguage = language.trim().lowercase()
        require(cleanLanguage in setOf("en", "te", "hi")) { "Select the voice sample language: English, Telugu or Hindi." }
        val cleanGender = when (gender.trim().lowercase()) {
            "male" -> "Male"
            "female" -> "Female"
            else -> error("Select Male or Female voice gender.")
        }
        require(list(context, uid).none { it.name.trim().equals(cleanName, ignoreCase = true) }) {
            "A voice profile with this name already exists. Choose a different name."
        }
        val sampleDir = sampleDirectory(context, uid)
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

            val voice = SavedUserVoice(id, cleanName, sample.absolutePath, cleanLanguage, cleanGender)
            val profiles = JSONArray()
            list(context, uid).filterNot { it.id == id }.forEach { existing ->
                profiles.put(JSONObject().apply {
                    put("id", existing.id); put("name", existing.name); put("language", existing.language); put("gender", existing.gender)
                    put("localSamplePath", existing.localSamplePath); put("sarvamVoiceId", existing.sarvamVoiceId)
                })
            }
            profiles.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language); put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath); put("sarvamVoiceId", voice.sarvamVoiceId); put("firebaseSynced", voice.firebaseSynced)
            })
            localProfilesFile(context, uid).writeText(profiles.toString())
            return voice
        } catch (error: Exception) {
            sample.delete()
            throw error
        }
    }

    fun attachSarvamVoiceId(context: Context, uid: String, id: String, voiceId: String) {
        require(voiceId.startsWith("svc-")) { "Invalid Sarvam voice ID." }
        val profiles = JSONArray()
        var found = false
        list(context, uid).forEach { voice ->
            val updated = if (voice.id == id) voice.copy(sarvamVoiceId = voiceId).also { found = true } else voice
            profiles.put(JSONObject().apply {
                put("id", updated.id); put("name", updated.name); put("language", updated.language)
                put("gender", updated.gender); put("localSamplePath", updated.localSamplePath)
                put("sarvamVoiceId", updated.sarvamVoiceId)
                put("firebaseSynced", updated.firebaseSynced)
            })
        }
        require(found) { "Saved voice profile could not be found." }
        localProfilesFile(context, uid).writeText(profiles.toString())
    }

    fun markFirebaseSynced(context: Context, uid: String, id: String) {
        val voices = list(context, uid)
        val target = voices.firstOrNull { it.id == id } ?: error("Saved voice profile could not be found.")
        require(target.sarvamVoiceId.startsWith("svc-")) { "Register the voice with Sarvam before syncing metadata." }
        writeProfiles(context, uid, voices.map { if (it.id == id) it.copy(firebaseSynced = true) else it })
    }

    fun deleteLocal(context: Context, uid: String, id: String) {
        val current = list(context, uid)
        val target = current.firstOrNull { it.id == id }
        val remaining = current.filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach { voice ->
            array.put(JSONObject().apply {
                put("id", voice.id); put("name", voice.name); put("language", voice.language); put("gender", voice.gender)
                put("localSamplePath", voice.localSamplePath); put("sarvamVoiceId", voice.sarvamVoiceId)
            })
        }
        localProfilesFile(context, uid).writeText(array.toString())
        target?.localSamplePath?.let { File(it).delete() }
    }
}
