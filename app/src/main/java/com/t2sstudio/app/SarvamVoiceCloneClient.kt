package com.t2sstudio.app

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

/** Sarvam Instant Voice Cloning client. Provider credentials remain in Firebase Functions Secret Manager. */
object SarvamVoiceCloneClient {
    private fun centralCall(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> {
        FirebaseAuth.getInstance().currentUser
            ?: error("Sign in to T2S before using shared Sarvam voices.")
        val supported = setOf(
            "listCentralSarvamVoices",
            "createCentralSarvamVoice",
            "generateCentralSarvamClone"
        )
        require(name in supported) { "Unsupported shared Sarvam operation." }
        val result = Tasks.await(
            FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable(name)
                .call(data)
        )
        return result.data as? Map<*, *>
            ?: error("T2S server returned an invalid Sarvam response.")
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
