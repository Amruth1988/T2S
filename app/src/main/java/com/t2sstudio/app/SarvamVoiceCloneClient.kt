package com.t2sstudio.app

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

/** Sarvam Instant Voice Cloning client. Provider credentials remain in Firebase Functions Secret Manager. */
object SarvamVoiceCloneClient {
    // Existing deployed Firebase-Spark-compatible Apps Script gateway.
    private const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzreX70ong9CQwtGMZ4tcSSLLY3iZ28MFL0ny6L6pitmRLUq6duHCJXiYZ_O2Do8wPP/exec"

    private fun centralCall(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Sign in to T2S before using shared Sarvam voices.")
        val token = Tasks.await(user.getIdToken(false)).token
            ?: error("Could not verify your T2S sign-in. Sign in again.")
        val action = when (name) {
            "listCentralSarvamVoices" -> "list"
            "createCentralSarvamVoice" -> "create"
            "generateCentralSarvamClone" -> "generate"
            else -> error("Unsupported shared Sarvam operation.")
        }
        val payload = JSONObject().put("action", action).put("idToken", token)
        data.forEach { (key, value) -> payload.put(key, value) }

        var url = URL(SCRIPT_URL)
        var method = "POST"
        repeat(5) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30000
                readTimeout = 120000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (method == "POST") connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("T2S gateway redirect did not include a destination.")
                    url = URL(url, location)
                    method = "GET"
                    return@repeat
                }
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = runCatching { JSONObject(body) }.getOrElse {
                    error("T2S gateway returned an invalid response (HTTP $status).")
                }
                if (status !in 200..299) error("T2S gateway request failed (HTTP $status).")
                if (result.has("error")) error(result.optString("error", "Shared Sarvam request failed."))
                val map = mutableMapOf<String, Any?>()
                result.keys().forEach { key ->
                    map[key] = if (result.isNull(key)) null else result.get(key)
                }
                return map
            } finally {
                connection.disconnect()
            }
        }
        error("T2S gateway redirect limit exceeded.")
    }

    fun testCentralConnection(): String {
        val result = centralCall("listCentralSarvamVoices")
        require(result.containsKey("voices")) { "T2S server did not return the Sarvam voice catalogue." }
        return "T2S shared Sarvam connection is ready."
    }

    /**
     * Android recordings are saved as AAC inside an MP4/M4A container. Sarvam's
     * create endpoint accepts AAC elementary audio but not audio/mp4 containers,
     * so decode container formats to standard PCM WAV before upload.
     */
    private fun convertToWav(source: File): File {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val pcm = ByteArrayOutputStream()
        var sampleRate = 44100
        var channels = 1
        var inputDone = false
        var outputDone = false
        try {
            extractor.setDataSource(source.absolutePath)
            var track = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    track = i
                    inputFormat = format
                    break
                }
            }
            require(track >= 0) { "The selected recording contains no decodable audio track." }
            val audioFormat = inputFormat ?: error("The selected recording contains no decodable audio track.")
            extractor.selectTrack(track)
            sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            decoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var iterations = 0
            while (!outputDone && iterations++ < 100000) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                            ?: error("Audio decoder did not provide an input buffer.")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val chunk = ByteArray(info.size)
                            output.get(chunk)
                            pcm.write(chunk)
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            require(outputDone && pcm.size() > 44) { "Could not decode the recording to WAV. Please record again." }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
        val raw = pcm.toByteArray()
        val wav = File.createTempFile("sarvam_voice_", ".wav", source.parentFile)
        FileOutputStream(wav).use { out ->
            fun writeInt(value: Int) {
                out.write(value and 0xff); out.write((value shr 8) and 0xff)
                out.write((value shr 16) and 0xff); out.write((value shr 24) and 0xff)
            }
            fun writeShort(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff) }
            out.write("RIFF".toByteArray(Charsets.US_ASCII)); writeInt(36 + raw.size)
            out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII)); writeInt(16)
            writeShort(1); writeShort(channels); writeInt(sampleRate)
            writeInt(sampleRate * channels * 2); writeShort(channels * 2); writeShort(16)
            out.write("data".toByteArray(Charsets.US_ASCII)); writeInt(raw.size); out.write(raw)
        }
        return wav
    }

    fun createCentralVoice(sample: File, name: String, language: String, gender: String): String {
        require(sample.isFile && sample.length() >= 1024L) { "Voice sample is missing or empty." }
        val uploadSample = if (sample.extension.lowercase() in setOf("wav", "mp3", "flac", "aac")) {
            sample
        } else {
            convertToWav(sample)
        }
        try {
            require(uploadSample.length() in 1024L..6_000_000L) {
                "Voice sample must be between 1 KB and 6 MB after audio preparation."
            }
            val mimeType = when (uploadSample.extension.lowercase()) {
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "aac" -> "audio/aac"
                else -> error("Could not prepare the recording in a Sarvam-supported audio format.")
            }
            val locale = when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" }
            val normalizedGender = when (gender.trim().lowercase()) {
                "male" -> "Male"
                "female" -> "Female"
                else -> "Other"
            }
            val result = centralCall("createCentralSarvamVoice", mapOf(
                "name" to name,
                "language" to locale,
                "gender" to normalizedGender,
                "mimeType" to mimeType,
                "sampleBase64" to Base64.encodeToString(uploadSample.readBytes(), Base64.NO_WRAP)
            ))
            return result["voiceId"]?.toString()?.takeIf { it.startsWith("svc-") }
                ?: error("T2S server did not return a saved Sarvam voice ID.")
        } finally {
            if (uploadSample != sample) uploadSample.delete()
        }
    }

    fun generateCentral(voiceId: String, text: String, language: String, output: File) {
        require(voiceId.startsWith("svc-")) { "This profile has not been registered with Sarvam." }
        require(text.isNotBlank() && text.length <= 1000) { "Enter 1–1,000 characters." }
        val result = centralCall("generateCentralSarvamClone", mapOf(
            "voiceId" to voiceId,
            "text" to text,
            "language" to when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" }
        ))
        val encoded = result["audioBase64"]?.toString().orEmpty()
        require(encoded.isNotBlank()) { "T2S server returned no audio." }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size > 1024 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Sarvam returned invalid WAV audio." }
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)
    }

}
