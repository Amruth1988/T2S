package com.t2sstudio.app

import android.content.Context
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
