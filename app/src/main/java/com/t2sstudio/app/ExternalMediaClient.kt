package com.t2sstudio.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Provider-neutral HTTPS media catalogue and downloader.
 *
 * Call network methods from Dispatchers.IO / a background worker, never the main thread.
 * Firebase remains responsible for identity and metadata; this client only reads a public,
 * versioned static manifest and downloads public media assets.
 */
data class ExternalMediaAsset(
    val id: String,
    val title: String,
    val language: String,
    val kind: String,
    val version: Int,
    val sizeBytes: Long,
    val sha256: String,
    val urls: List<String>,
    val license: String
)

object ExternalMediaClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_MANIFEST_BYTES = 512 * 1024
    private const val MAX_ASSET_BYTES = 500L * 1024 * 1024

    /** Fetch and parse a JSON manifest from an HTTPS static-host URL. */
    fun fetchCatalogue(manifestUrl: String): List<ExternalMediaAsset> {
        val bytes = readHttps(manifestUrl, MAX_MANIFEST_BYTES)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val schemaVersion = root.optInt("schemaVersion", 0)
        require(schemaVersion == 1) { "Unsupported media catalogue version: $schemaVersion" }
        val items = root.optJSONArray("assets") ?: JSONArray()
        return (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val asset = ExternalMediaAsset(
                id = item.getString("id"),
                title = item.getString("title"),
                language = item.optString("language", "und"),
                kind = item.getString("kind"),
                version = item.getInt("version"),
                sizeBytes = item.getLong("sizeBytes"),
                sha256 = item.getString("sha256").lowercase(),
                urls = item.getJSONArray("urls").let { array ->
                    (0 until array.length()).map { array.getString(it) }
                },
                license = item.getString("license")
            )
            require(asset.id.matches(Regex("[a-zA-Z0-9._-]{1,100}"))) { "Invalid media asset ID." }
            require(asset.version > 0 && asset.sizeBytes in 1..MAX_ASSET_BYTES) { "Invalid media size or version." }
            require(asset.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid SHA-256 for ${asset.id}." }
            require(asset.urls.isNotEmpty() && asset.urls.all(::isHttpsUrl)) { "Media URLs must use HTTPS." }
            require(asset.title.isNotBlank() && asset.license.isNotBlank()) { "Media title and licence are required." }
            asset
        }.distinctBy { it.id }
    }

    /**
     * Downloads to app-private cache, verifies exact byte count and SHA-256, then atomically
     * promotes the file. Tries each HTTPS mirror in order. Existing valid cache is reused.
     */
    fun download(context: Context, asset: ExternalMediaAsset): File {
        val directory = File(context.filesDir, "external_media/${asset.id}/${asset.version}")
        check(directory.exists() || directory.mkdirs()) { "Could not create media cache." }
        val target = File(directory, "asset.bin")
        if (target.isFile && target.length() == asset.sizeBytes &&
            sha256(target).equals(asset.sha256, ignoreCase = true)) return target

        val failures = mutableListOf<String>()
        for ((index, mediaUrl) in asset.urls.withIndex()) {
            val partial = File(directory, "download-$index.part")
            try {
                require(isHttpsUrl(mediaUrl)) { "Only HTTPS media URLs are accepted." }
                val connection = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("Accept-Encoding", "identity")
                }
                try {
                    val status = connection.responseCode
                    require(status in 200..299) { "Media server returned HTTP $status." }
                    val declaredLength = connection.contentLengthLong
                    require(declaredLength == -1L || declaredLength == asset.sizeBytes) {
                        "Media size does not match the catalogue."
                    }
                    var copied = 0L
                    BufferedInputStream(connection.inputStream).use { input ->
                        FileOutputStream(partial).buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                require(copied <= asset.sizeBytes && copied <= MAX_ASSET_BYTES) {
                                    "Downloaded media exceeds its declared size."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(copied == asset.sizeBytes) { "Downloaded media is incomplete." }
                    require(sha256(partial).equals(asset.sha256, ignoreCase = true)) {
                        "Downloaded media failed SHA-256 verification."
                    }
                    if (target.exists()) check(target.delete()) { "Could not replace old cached media." }
                    check(partial.renameTo(target)) { "Could not finalize downloaded media." }
                    return target
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                partial.delete()
                failures += error.message ?: "Download failed"
            }
        }
        error("All media mirrors failed: ${failures.joinToString(" | ")}")
    }

    fun clearCachedAsset(context: Context, assetId: String) {
        require(assetId.matches(Regex("[a-zA-Z0-9._-]{1,100}")))
        File(context.filesDir, "external_media/$assetId").deleteRecursively()
    }

    private fun readHttps(url: String, maxBytes: Int): ByteArray {
        require(isHttpsUrl(url)) { "Catalogue URL must use HTTPS." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
        }
        try {
            require(connection.responseCode in 200..299) { "Catalogue server returned HTTP ${connection.responseCode}." }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxBytes) { "Catalogue exceeds the size limit." }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun isHttpsUrl(value: String): Boolean =
        runCatching { URL(value).protocol.equals("https", ignoreCase = true) && URL(value).host.isNotBlank() }
            .getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
