package com.t2sstudio.app

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Private, per-account cache for generated previews of registered My Voices.
 * Original user recordings are never copied into or deleted from this cache.
 */
object VoicePreviewCache {
    private const val CACHE_VERSION = "sarvam-preview-v1"
    private const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
    private const val MAX_CACHE_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    private val cachedFiles = ConcurrentHashMap<String, File>()
    private val generationLocks = ConcurrentHashMap<String, Any>()

    private fun accountKey(uid: String): String {
        require(uid.isNotBlank()) { "Sign in before loading generated voice previews." }
        return sha256(uid)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun directory(context: Context, account: String): File =
        File(context.noBackupFilesDir, "accounts/$account/voices/generated_preview_cache")
            .apply { mkdirs() }

    fun previewText(languageCode: String): String =
        when (languageCode.lowercase().substringBefore('-')) {
            "te", "telugu" -> "నమస్కారం. ఇది మీ వాయిస్ ప్రివ్యూ నమూనా."
            "hi", "hindi" -> "नमस्ते। यह आपकी आवाज़ का नमूना है।"
            else -> "Hello. This is a sample of your saved voice."
        }

    /**
     * Warm the in-memory index at app launch/sign-in. Audio remains in private
     * no-backup storage and is isolated by the signed-in Firebase UID.
     */
    fun preload(context: Context, uid: String): Int {
        if (uid.isBlank()) return 0
        val account = accountKey(uid)
        val folder = directory(context.applicationContext, account)
        val now = System.currentTimeMillis()
        var loaded = 0

        folder.listFiles()?.forEach { file ->
            val validFormat = file.extension.lowercase() in setOf("wav", "mp3", "m4a")
            if (!file.isFile || !validFormat || file.length() <= 44L ||
                now - file.lastModified() > MAX_CACHE_AGE_MS
            ) {
                file.delete()
                cachedFiles.remove("$account/${file.nameWithoutExtension}")
            } else {
                cachedFiles["$account/${file.nameWithoutExtension}"] = file
                loaded++
            }
        }
        prune(folder, account)
        return loaded
    }

    /**
     * Return a cached generated preview or create it once. The caller must run
     * this blocking generator on Dispatchers.IO.
     */
    fun getOrGenerate(
        context: Context,
        uid: String,
        voiceId: String,
        languageCode: String,
        text: String,
        format: String = "wav",
        generator: (File) -> Unit
    ): File {
        require(voiceId.isNotBlank()) { "A registered voice ID is required for preview caching." }
        require(text.isNotBlank()) { "Preview text cannot be empty." }
        val extension = format.lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
            ?: error("Unsupported generated preview format.")
        val account = accountKey(uid)
        val cacheId = sha256(
            listOf(CACHE_VERSION, "sarvam-central", voiceId, languageCode.lowercase(), text, extension)
                .joinToString("\u0000")
        )
        val indexKey = "$account/$cacheId"
        val folder = directory(context.applicationContext, account)
        val target = File(folder, "$cacheId.$extension")
        val lock = generationLocks.computeIfAbsent(indexKey) { Any() }

        synchronized(lock) {
            val existing = cachedFiles[indexKey] ?: target
            if (existing.isFile && existing.length() > 44L) {
                existing.setLastModified(System.currentTimeMillis())
                cachedFiles[indexKey] = existing
                return existing
            }
            cachedFiles.remove(indexKey)
            existing.delete()

            val temporary = File(folder, ".$cacheId.tmp.$extension")
            temporary.delete()
            try {
                generator(temporary)
                check(temporary.isFile && temporary.length() > 44L) {
                    "The voice service returned an empty or invalid audio preview."
                }
                if (target.exists() && !target.delete()) {
                    error("Could not replace an incomplete cached voice preview.")
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target.setLastModified(System.currentTimeMillis())
                cachedFiles[indexKey] = target
                prune(folder, account)
                return target
            } catch (error: Exception) {
                temporary.delete()
                throw error
            }
        }
    }

    private fun prune(folder: File, account: String) {
        val now = System.currentTimeMillis()
        val files = folder.listFiles()?.filter { it.isFile } ?: return

        files.filter { now - it.lastModified() > MAX_CACHE_AGE_MS }.forEach { file ->
            cachedFiles.remove("$account/${file.nameWithoutExtension}")
            file.delete()
        }

        val remaining = folder.listFiles()?.filter { it.isFile } ?: return
        var totalBytes = remaining.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return

        remaining.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= MAX_CACHE_BYTES) return@forEach
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
                cachedFiles.remove("$account/${file.nameWithoutExtension}")
            }
        }
    }
}
