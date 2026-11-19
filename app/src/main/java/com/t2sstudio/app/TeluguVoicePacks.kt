package com.t2sstudio.app

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TeluguVoiceGroup { MALE, FEMALE, PUROHIT }

data class TeluguVoicePack(
    val id: String,
    val displayName: String,
    val group: TeluguVoiceGroup,
    val modelFile: String,
    val configFile: String,
    val modelUrl: String,
    val configUrl: String,
    val approximateBytes: Long,
    val license: String = "Check the individual model card"
) {
    fun directory(context: Context) = File(context.filesDir, "voice_models/telugu/$id")
    fun model(context: Context) = File(directory(context), modelFile)
    fun config(context: Context) = File(directory(context), configFile)
    fun isDownloaded(context: Context) =
        model(context).length() > 10_000_000L && config(context).length() > 1_000L
}

/** Raw Piper model downloads only. These files are NOT runtime-ready until Sherpa conversion creates tokens.txt and model metadata. */
object TeluguVoicePacks {
    private const val BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN"

    val all = listOf(
        TeluguVoicePack(
            id = "venkatesh", displayName = "Venkatesh", group = TeluguVoiceGroup.MALE,
            modelFile = "te_IN-venkatesh-medium.onnx",
            configFile = "te_IN-venkatesh-medium.onnx.json",
            modelUrl = "$BASE/venkatesh/medium/te_IN-venkatesh-medium.onnx",
            configUrl = "$BASE/venkatesh/medium/te_IN-venkatesh-medium.onnx.json",
            approximateBytes = 63_516_050L
        ),
        TeluguVoicePack(
            id = "padmavathi", displayName = "Padmavathi", group = TeluguVoiceGroup.FEMALE,
            modelFile = "te_IN-padmavathi-medium.onnx",
            configFile = "te_IN-padmavathi-medium.onnx.json",
            modelUrl = "$BASE/padmavathi/medium/te_IN-padmavathi-medium.onnx",
            configUrl = "$BASE/padmavathi/medium/te_IN-padmavathi-medium.onnx.json",
            approximateBytes = 63_516_050L
        ),
        TeluguVoicePack(
            id = "maya", displayName = "Maya", group = TeluguVoiceGroup.FEMALE,
            modelFile = "te_IN-maya-medium.onnx",
            configFile = "te_IN-maya-medium.onnx.json",
            modelUrl = "$BASE/maya/medium/te_IN-maya-medium.onnx",
            configUrl = "$BASE/maya/medium/te_IN-maya-medium.onnx.json",
            approximateBytes = 62_950_044L
        )
    )

    fun downloaded(context: Context) = all.filter { it.isDownloaded(context) }

    data class InstalledConvertedVoice(
        val id: String,
        val displayName: String,
        val group: TeluguVoiceGroup,
        val directory: File,
        val model: File,
        val tokens: File
    )

    /**
     * Imports the converted voice folders from the CI validation ZIP selected by the user.
     * Only the expected model and tokens files are extracted; ZIP paths are never trusted.
     */
    fun importConvertedArchive(context: Context, uri: Uri): List<InstalledConvertedVoice> {
        val root = File(context.filesDir, "voice_models/telugu_converted")
        check(root.exists() || root.mkdirs()) { "Could not create converted voice storage." }
        val stagingRoot = File(root, ".import-${System.currentTimeMillis()}")
        check(stagingRoot.mkdirs()) { "Could not create temporary import storage." }
        val extractedModels = mutableSetOf<String>()
        val extractedTokens = mutableSetOf<String>()
        var totalExpandedBytes = 0L
        var entryCount = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount += 1
                        check(entryCount <= 10_000) { "Voice archive contains too many entries." }
                        val normalized = entry.name
                        val archivePath = normalized.trimStart('/')
                        val pack = all.firstOrNull {
                            archivePath.startsWith("${it.id}/") ||
                                archivePath.contains("/${it.id}/") ||
                                archivePath.startsWith("build/telugu-voices/${it.id}/")
                        }
                        var extractedExpectedFile = false
                        if (pack != null && !entry.isDirectory) {
                            val name = normalized.substringAfterLast('/')
                            if (name == pack.modelFile || name == "tokens.txt") {
                                val seen = if (name == pack.modelFile) extractedModels else extractedTokens
                                check(pack.id !in seen) {
                                    "Voice archive contains a duplicate $name entry for ${pack.displayName}."
                                }
                                val maxEntryBytes = if (name == pack.modelFile) 180L * 1024 * 1024 else 2L * 1024 * 1024
                                val destinationDir = File(stagingRoot, pack.id)
                                check(destinationDir.exists() || destinationDir.mkdirs()) {
                                    "Could not create temporary voice folder."
                                }
                                val partial = File(destinationDir, "$name.part")
                                partial.delete()
                                try {
                                    val written = zip.copyEntryBounded(partial, maxEntryBytes)
                                    totalExpandedBytes += written
                                    check(totalExpandedBytes <= 500L * 1024 * 1024) {
                                        "Voice archive expands beyond the 500 MB safety limit."
                                    }
                                    check(partial.renameTo(File(destinationDir, name))) {
                                        "Could not finalize imported voice file."
                                    }
                                    if (name == pack.modelFile) extractedModels.add(pack.id)
                                    if (name == "tokens.txt") extractedTokens.add(pack.id)
                                    extractedExpectedFile = true
                                } catch (error: Exception) {
                                    partial.delete()
                                    throw error
                                }
                            }
                        }
                        if (!entry.isDirectory && !extractedExpectedFile) {
                            // Drain unselected entries through a bound too: closeEntry() alone can
                            // inflate an arbitrarily large ZIP bomb even when nothing is extracted.
                            val skippedBytes = zip.drainEntryBounded(180L * 1024 * 1024)
                            totalExpandedBytes += skippedBytes
                            check(totalExpandedBytes <= 500L * 1024 * 1024) {
                                "Voice archive expands beyond the 500 MB safety limit."
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Could not open the selected ZIP file.")

            val ready = all.filter { pack ->
                if (pack.id !in extractedModels || pack.id !in extractedTokens) return@filter false
                val dir = File(stagingRoot, pack.id)
                File(dir, pack.modelFile).let { it.isFile && it.length() > 10_000_000L } &&
                    File(dir, "tokens.txt").let { it.isFile && it.length() > 100L }
            }
            check(ready.isNotEmpty()) { "No complete converted Telugu voice packs were found in this ZIP." }

            // Treat a multi-voice archive as one installation transaction. If any
            // rename fails, remove newly installed folders and restore every prior pack.
            val installed = mutableListOf<InstalledConvertedVoice>()
            val backups = linkedMapOf<File, File>()
            val installedDirectories = mutableListOf<File>()
            try {
                for (pack in ready) {
                    val staged = File(stagingRoot, pack.id)
                    val destination = File(root, pack.id)
                    val backup = File(root, ".${pack.id}.backup-${System.nanoTime()}")
                    if (destination.exists()) {
                        check(destination.renameTo(backup)) {
                            "Could not safely replace the existing ${pack.displayName} voice."
                        }
                        backups[destination] = backup
                    }
                    check(staged.renameTo(destination)) {
                        "Could not install the complete ${pack.displayName} voice pack."
                    }
                    installedDirectories.add(destination)
                    val model = File(destination, pack.modelFile)
                    val tokens = File(destination, "tokens.txt")
                    installed.add(InstalledConvertedVoice(pack.id, pack.displayName, pack.group, destination, model, tokens))
                }
            } catch (error: Exception) {
                installedDirectories.asReversed().forEach { it.deleteRecursively() }
                var restoreFailed = false
                backups.entries.toList().asReversed().forEach { (destination, backup) ->
                    if (backup.exists() && !backup.renameTo(destination)) restoreFailed = true
                }
                if (restoreFailed) {
                    error.addSuppressed(IllegalStateException(
                        "A previous voice backup remains in converted voice storage; do not delete hidden .backup folders."
                    ))
                }
                throw error
            }
            // Backups are deleted only after every selected pack has been installed.
            backups.values.forEach { it.deleteRecursively() }
            return installed
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun ZipInputStream.drainEntryBounded(maxBytes: Long): Long {
        var readBytes = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            readBytes += count
            check(readBytes <= maxBytes) { "Voice archive entry exceeds its safety limit." }
        }
        return readBytes
    }

    private fun ZipInputStream.copyEntryBounded(destination: File, maxBytes: Long): Long {
        var written = 0L
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                written += count
                check(written <= maxBytes) { "Voice archive entry exceeds its safety limit." }
                output.write(buffer, 0, count)
            }
        }
        check(written > 100L) { "Extracted voice file is empty." }
        return written
    }

    fun installedConverted(context: Context): List<InstalledConvertedVoice> =
        all.mapNotNull { pack ->
            val dir = File(context.filesDir, "voice_models/telugu_converted/${pack.id}")
            val model = File(dir, pack.modelFile)
            val tokens = File(dir, "tokens.txt")
            if (model.isFile && model.length() > 10_000_000L && tokens.isFile && tokens.length() > 100L) {
                InstalledConvertedVoice(pack.id, pack.displayName, pack.group, dir, model, tokens)
            } else null
        }

    suspend fun download(
        context: Context,
        pack: TeluguVoicePack,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val directory = pack.directory(context)
        check(directory.exists() || directory.mkdirs()) { "Could not create Telugu voice storage." }
        downloadFile(pack.modelUrl, File(directory, pack.modelFile), pack.approximateBytes, onProgress)
        downloadFile(pack.configUrl, File(directory, pack.configFile), 1_000L) { done, total ->
            onProgress(pack.approximateBytes + done, pack.approximateBytes + total)
        }
        check(pack.isDownloaded(context)) { "The Telugu model download is incomplete." }
    }

    private fun downloadFile(
        address: String,
        destination: File,
        minimumBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        if (destination.exists() && destination.length() >= minimumBytes) return
        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "T2S-Studio-Android")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Voice download failed (HTTP ${connection.responseCode})." }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: minimumBytes
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
            check(partial.length() >= minimumBytes) { "Downloaded voice file is incomplete." }
            check(partial.renameTo(destination)) { "Could not finalize the voice download." }
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
