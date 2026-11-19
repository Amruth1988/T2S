package com.t2sstudio.app

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineTtsVoice(val id: String, val name: String, val language: String, val gender: String, val model: String? = null, val previewUrl: String? = null)

object TtsAiClient {

    private fun isAudioBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val head = String(bytes, 0, minOf(bytes.size, 12), Charsets.US_ASCII)
        return head.startsWith("ID3") || head.startsWith("RIFF") || head.startsWith("OggS") ||
            head.startsWith("fLaC") || ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0)
    }

    private const val BASE = "https://api.tts.ai/v1"

    fun listVoices(language: String): List<OnlineTtsVoice> {
        val encoded = URLEncoder.encode(language, "UTF-8")
        val connection = URL(BASE + "/voices/?language=" + encoded).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode !in 200..299) error("Voice catalogue unavailable")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val voices = json.optJSONArray("voices") ?: return emptyList()
            return (0 until voices.length()).mapNotNull { index ->
                val item = voices.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                OnlineTtsVoice(id, item.optString("name", id), item.optString("language", language), item.optString("gender", "unknown"), item.optString("model").takeIf { it.isNotBlank() }, item.optString("preview_url").takeIf { it.startsWith("https://") })
            }
        } finally {
            connection.disconnect()
        }
    }

    fun downloadPreview(previewUrl: String, output: File) {
        val initial = URL(previewUrl)
        require(initial.protocol == "https") {
            "Voice preview URL must use secure HTTPS"
        }
        val connection = initial.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "audio/mpeg,audio/wav,audio/*;q=0.9,*/*;q=0.1")
            connection.setRequestProperty("User-Agent", "T2S-Studio-Android")
            val code = connection.responseCode
            if (code !in 200..299) error("Voice preview download failed (HTTP $code)")
            val finalUrl = URL(connection.url.toString())
            require(finalUrl.protocol == "https") {
                "Voice preview redirected to an insecure URL"
            }
            output.parentFile?.mkdirs()
            connection.inputStream.use { input -> output.outputStream().use { out -> input.copyTo(out) } }
            if (!output.exists() || output.length() < 1024) error("Voice preview audio is empty or incomplete")
            val header = output.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            val isWav = header.size >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
            val isMp3 = header.size >= 3 && (String(header, 0, 3, Charsets.US_ASCII) == "ID3" ||
                ((header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0))
            val isOgg = header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "OggS"
            val isFlac = header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "fLaC"
            if (!isWav && !isMp3 && !isOgg && !isFlac) error("Voice preview URL did not return a supported audio file")
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun generate(text: String, language: String, voice: String, speed: Float, output: File, apiKey: String = "", model: String? = null, format: String = "wav", userVoiceId: String? = null) {
        require(text.length <= 500) { "Online free requests are limited to 500 characters." }
        val payload = JSONObject().apply {
            put("text", text)
            put("language", language)
            // TTS.ai documents saved-voice public_id as the normal "voice" value.
            put("voice", userVoiceId?.takeIf { it.isNotBlank() } ?: voice)
            put("format", when (format.lowercase()) { "mp3" -> "mp3"; "flac" -> "flac"; "ogg" -> "ogg"; else -> "wav" })
            put("speed", speed.toDouble())
            if (!model.isNullOrBlank()) put("model", model)
        }.toString()
        val submit = URL(BASE + "/tts/").openConnection() as HttpURLConnection
        val uuid: String
        try {
            submit.requestMethod = "POST"
            submit.connectTimeout = 15_000
            submit.readTimeout = 120_000
            submit.doOutput = true
            submit.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            submit.setRequestProperty("Accept", "audio/mpeg,audio/wav,audio/*;q=0.9,application/json")
            if (apiKey.isNotBlank()) submit.setRequestProperty("Authorization", "Bearer $apiKey")
            submit.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = submit.responseCode
            if (code !in 200..299) {
                val message = submit.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(message.ifBlank { "Online TTS request failed ($code)" })
            }
            val contentType = submit.contentType.orEmpty().lowercase()
            val bytes = submit.inputStream.use { it.readBytes() }
            require(bytes.isNotEmpty()) { "Online TTS returned an empty response" }
            if (contentType.startsWith("audio/") || isAudioBytes(bytes)) {
                output.parentFile?.mkdirs()
                output.writeBytes(bytes)
                if (output.length() < 44) error("Online TTS audio was incomplete")
                return
            }
            val responseText = bytes.toString(Charsets.UTF_8)
            val responseJson = runCatching { JSONObject(responseText) }.getOrElse {
                error("Online TTS returned an unexpected response: " + responseText.take(240))
            }
            uuid = responseJson.optString("uuid").takeIf { it.isNotBlank() }
                ?: responseJson.optString("job_id").takeIf { it.isNotBlank() }
                ?: error(responseJson.optString("error").ifBlank { "Online TTS returned neither audio nor a job ID" })
        } finally {
            submit.disconnect()
        }

        var resultUrl: String? = null
        for (attempt in 0 until 60) {
            Thread.sleep(1_500)
            val poll = URL(BASE + "/speech/results/?uuid=" + URLEncoder.encode(uuid, "UTF-8"))
                .openConnection() as HttpURLConnection
            try {
                poll.requestMethod = "GET"
                poll.connectTimeout = 10_000
                poll.readTimeout = 15_000
                if (poll.responseCode !in 200..299) {
                    val message = poll.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error(message.ifBlank { "Could not check online speech status (HTTP ${poll.responseCode})" })
                }
                val result = JSONObject(poll.inputStream.bufferedReader().use { it.readText() })
                when (result.optString("status").lowercase()) {
                    "completed", "success", "succeeded" -> {
                        resultUrl = result.optString("result_url").takeIf { it.isNotBlank() }
                        if (resultUrl != null) break
                        error("Online speech completed but returned no audio URL")
                    }
                    "failed", "error" -> error(result.optString("error").ifBlank { "Online speech generation failed" })
                }
            } finally {
                poll.disconnect()
            }
        }
        val downloadUrl = resultUrl ?: error("Online speech generation timed out")
        val parsedDownloadUrl = URL(downloadUrl)
        // TTS.ai returns a signed result_url that may point to its object-storage/CDN
        // host rather than api.tts.ai. Trust the HTTPS URL supplied by the authenticated
        // result response, but reject cleartext URLs, user-info and missing hosts.
        require(parsedDownloadUrl.protocol.equals("https", ignoreCase = true) &&
            parsedDownloadUrl.host.isNotBlank() && parsedDownloadUrl.userInfo == null) {
            "TTS service returned an invalid or insecure audio URL"
        }
        val download = parsedDownloadUrl.openConnection() as HttpURLConnection
        try {
            download.instanceFollowRedirects = true
            download.connectTimeout = 15_000
            download.readTimeout = 60_000
            if (download.responseCode !in 200..299) error("Could not download online speech audio (HTTP ${download.responseCode})")
            val finalDownloadUrl = URL(download.url.toString())
            require(finalDownloadUrl.protocol.equals("https", ignoreCase = true) &&
                finalDownloadUrl.host.isNotBlank() && finalDownloadUrl.userInfo == null) {
                "Audio download redirected to an invalid or insecure URL"
            }
            output.parentFile?.mkdirs()
            download.inputStream.use { input -> output.outputStream().use { out -> input.copyTo(out) } }
            if (!output.exists() || output.length() < 44) error("Online speech audio was empty")
            val header = output.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            if (!isAudioBytes(header)) error("Online speech download did not contain a supported audio file")
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            download.disconnect()
        }
    }
}
