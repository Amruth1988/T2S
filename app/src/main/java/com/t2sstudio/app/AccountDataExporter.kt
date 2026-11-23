package com.t2sstudio.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Environment
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exports cloud metadata plus this account's local projects, audio and voice samples. */
object AccountDataExporter {
    fun export(context: Context, uid: String, destination: Uri, onComplete: (Result<Int>) -> Unit) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Sign in before exporting account data.")))
            return
        }
        val db = FirebaseFirestore.getInstance()
        val user = db.collection("users").document(uid).get()
        val projects = db.collection("users").document(uid).collection("projects").get()
        val scripts = db.collection("users").document(uid).collection("scripts").get()
        val voices = db.collection("users").document(uid).collection("voiceProfiles").get()
        val settings = db.collection("users").document(uid).collection("settings").get()
        val audioLibrary = db.collection("users").document(uid).collection("audioLibrary").get()
        val devices = db.collection("users").document(uid).collection("devices").get()
        val usageStats = db.collection("users").document(uid).collection("usageStats").get()
        val feedback = db.collection("users").document(uid).collection("feedback").get()

        Tasks.whenAllSuccess<Any>(listOf(user, projects, scripts, voices, settings, audioLibrary, devices, usageStats, feedback))
            .addOnSuccessListener { results ->
                val executor = Executors.newSingleThreadExecutor()
                executor.execute {
                    val result = runCatching {
                        val profile = results[0] as DocumentSnapshot
                        val cloudProjects = results[1] as QuerySnapshot
                        val cloudScripts = results[2] as QuerySnapshot
                        val cloudVoices = results[3] as QuerySnapshot
                        val cloudSettings = results[4] as QuerySnapshot
                        val cloudAudioLibrary = results[5] as QuerySnapshot
                        val cloudDevices = results[6] as QuerySnapshot
                        val cloudUsageStats = results[7] as QuerySnapshot
                        val cloudFeedback = results[8] as QuerySnapshot
                        val accountHash = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(uid.toByteArray(Charsets.UTF_8))
                            .joinToString("") { "%02x".format(it) }
                        val musicRoot = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                        val accountAudioRoot = File(musicRoot, "T2SStudio/accounts/$accountHash").canonicalFile
                        var entries = 0
                        fun safeEntryId(value: String, index: Int): String =
                            value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
                                .ifBlank { "item_$index" }

                        context.contentResolver.openOutputStream(destination, "wt")?.use { raw ->
                            ZipOutputStream(raw.buffered()).use { zip ->
                                fun addText(path: String, value: String) {
                                    zip.putNextEntry(ZipEntry(path))
                                    zip.write(value.toByteArray(Charsets.UTF_8))
                                    zip.closeEntry()
                                    entries++
                                }
                                fun jsonValue(value: Any?): Any? = when (value) {
                                    null -> JSONObject.NULL
                                    is Timestamp -> value.toDate().time
                                    is Date -> value.time
                                    is Map<*, *> -> JSONObject().apply {
                                        value.forEach { (key, item) -> if (key is String) put(key, jsonValue(item)) }
                                    }
                                    is Iterable<*> -> JSONArray().apply { value.forEach { put(jsonValue(it)) } }
                                    is Array<*> -> JSONArray().apply { value.forEach { put(jsonValue(it)) } }
                                    is Number, is Boolean, is String -> value
                                    else -> value.toString()
                                }
                                fun snapshotJson(snapshot: QuerySnapshot): JSONArray = JSONArray().apply {
                                    snapshot.documents.forEach { doc ->
                                        put(JSONObject().put("id", doc.id).put("data", jsonValue(doc.data)))
                                    }
                                }
                                addText("cloud/profile.json", JSONObject().put("uid", uid)
                                    .put("data", jsonValue(profile.data)).toString(2))
                                addText("cloud/projects.json", snapshotJson(cloudProjects).toString(2))
                                addText("cloud/scripts.json", snapshotJson(cloudScripts).toString(2))
                                addText("cloud/voice_profiles.json", snapshotJson(cloudVoices).toString(2))
                                addText("cloud/settings.json", snapshotJson(cloudSettings).toString(2))
                                addText("cloud/audio_library.json", snapshotJson(cloudAudioLibrary).toString(2))
                                addText("cloud/devices.json", snapshotJson(cloudDevices).toString(2))
                                addText("cloud/usage_stats.json", snapshotJson(cloudUsageStats).toString(2))
                                addText("cloud/feedback.json", snapshotJson(cloudFeedback).toString(2))

                                val localProjects = JSONArray().apply {
                                    ProjectStore.load(context, uid).forEach { project ->
                                        put(JSONObject().put("id", project.id).put("name", project.name)
                                            .put("createdAt", project.createdAt))
                                    }
                                }
                                addText("local/projects.json", localProjects.toString(2))

                                val selectedSavedVoiceId = context.getSharedPreferences(
                                    "t2s_voice_selection", Context.MODE_PRIVATE
                                ).getString("selected_saved_voice_id_$uid", null)
                                addText("local/preferences.json", JSONObject()
                                    .put("selectedSavedVoiceId", selectedSavedVoiceId ?: JSONObject.NULL)
                                    .toString(2))

                                val localAudio = JSONArray()
                                AudioHistoryStore.load(context, uid).forEachIndexed { index, record ->
                                    val file = runCatching { File(record.filePath).canonicalFile }.getOrNull()
                                    val insideAccount = file != null && file.path.startsWith(accountAudioRoot.path + File.separator)
                                    val entryName = if (insideAccount && file!!.isFile) {
                                        val ext = file.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
                                        "local/audio/${index}_${safeEntryId(record.id, index)}.$ext"
                                    } else null
                                    localAudio.put(JSONObject().apply {
                                        put("id", record.id)
                                        put("file", entryName ?: JSONObject.NULL)
                                        put("textPreview", record.textPreview)
                                        put("scriptContent", record.scriptContent)
                                        put("language", record.language)
                                        put("createdAt", record.createdAt)
                                        put("projectId", record.projectId)
                                        put("projectName", record.projectName)
                                        put("voiceSource", record.voiceSource)
                                        put("voiceName", record.voiceName)
                                        put("voiceGender", record.voiceGender)
                                    })
                                    if (entryName != null && file != null) {
                                        zip.putNextEntry(ZipEntry(entryName))
                                        file.inputStream().buffered().use { it.copyTo(zip) }
                                        zip.closeEntry()
                                        entries++
                                    }
                                }
                                addText("local/audio_history.json", localAudio.toString(2))

                                val localVoices = JSONArray()
                                MyVoicesClient.list(context, uid).forEachIndexed { index, voice ->
                                    val sample = voice.localSamplePath?.let { runCatching { File(it).canonicalFile }.getOrNull() }
                                    val allowedRoot = File(context.noBackupFilesDir, "accounts/$accountHash/voices/samples").canonicalFile
                                    val allowed = sample != null && sample.path.startsWith(allowedRoot.path + File.separator) &&
                                        sample.isFile && sample.length() > 0
                                    val ext = sample?.extension?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
                                    val sampleEntry = if (allowed) {
                                        "local/voice_samples/${index}_${safeEntryId(voice.id, index)}.$ext"
                                    } else null
                                    localVoices.put(JSONObject().apply {
                                        put("id", voice.id)
                                        put("name", voice.name)
                                        put("language", voice.language)
                                        put("gender", voice.gender)
                                        put("sarvamVoiceId", voice.sarvamVoiceId)
                                        put("firebaseSynced", voice.firebaseSynced)
                                        put("sample", sampleEntry ?: JSONObject.NULL)
                                    })
                                    if (sampleEntry != null && sample != null) {
                                        zip.putNextEntry(ZipEntry(sampleEntry))
                                        sample.inputStream().buffered().use { it.copyTo(zip) }
                                        zip.closeEntry()
                                        entries++
                                    }
                                }
                                addText("local/voice_profiles.json", localVoices.toString(2))
                                addText("manifest.json", JSONObject()
                                    .put("format", "T2S Studio account export")
                                    .put("version", 1)
                                    .put("exportedAt", System.currentTimeMillis())
                                    .put("containsCloudMetadata", true)
                                    .put("containsLocalAudioAndVoiceSamples", true)
                                    .put("includesLocalVoiceSelection", true)
                                    .put("accountUidHash", accountHash)
                                    .toString(2))
                            }
                        } ?: error("Could not open the selected export destination.")
                        entries
                    }
                    Handler(Looper.getMainLooper()).post { onComplete(result) }
                    executor.shutdown()
                }
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }
}
