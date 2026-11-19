package com.t2sstudio.app

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineTtsVoice(val id: String, val name: String, val language: String, val gender: String, val model: String? = null, val previewUrl: String? = null)

object TtsAiClient {
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

    fun generate(text: String, language: String, voice: String, speed: Float, output: File, apiKey: String = "", model: String? = null, format: String = "wav") {
        require(text.length <= 500) { "Online free requests are limited to 500 characters." }
        val payload = JSONObject().apply {
            put("text", text)
            put("language", language)
            put("voice", voice)
            put("format", if (format.equals("mp3", true)) "mp3" else "wav")
            put("speed", speed.toDouble())
            if (!model.isNullOrBlank()) put("model", model)
        }.toString()
        val submit = URL(BASE + "/tts/").openConnection() as HttpURLConnection
        val uuid: String
        try {
            submit.requestMethod = "POST"
            submit.connectTimeout = 15_000
            submit.readTimeout = 30_000
            submit.doOutput = true
            submit.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) submit.setRequestProperty("Authorization", "Bearer $apiKey")
            submit.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (submit.responseCode !in 200..299) {
                val message = submit.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(message.ifBlank { "Online TTS request failed (" + submit.responseCode + ")" })
            }
            uuid = JSONObject(submit.inputStream.bufferedReader().use { it.readText() })
                .optString("uuid").takeIf { it.isNotBlank() } ?: error("Online TTS returned no job ID")
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
                if (poll.responseCode !in 200..299) error("Could not check online speech status")
                val result = JSONObject(poll.inputStream.bufferedReader().use { it.readText() })
                when (result.optString("status")) {
                    "completed" -> {
                        resultUrl = result.optString("result_url").takeIf { it.isNotBlank() }
                        if (resultUrl != null) break
                    }
                    "failed" -> error(result.optString("error").ifBlank { "Online speech generation failed" })
                }
            } finally {
                poll.disconnect()
            }
        }
        val downloadUrl = resultUrl ?: error("Online speech generation timed out")
        require(downloadUrl.startsWith("https://api.tts.ai/")) { "Unexpected audio download host" }
        val download = URL(downloadUrl).openConnection() as HttpURLConnection
        try {
            download.connectTimeout = 15_000
            download.readTimeout = 60_000
            if (download.responseCode !in 200..299) error("Could not download online speech audio")
            output.parentFile?.mkdirs()
            download.inputStream.use { input -> output.outputStream().use { out -> input.copyTo(out) } }
            if (!output.exists() || output.length() < 44) error("Online speech audio was empty")
        } finally {
            download.disconnect()
        }
    }
}
