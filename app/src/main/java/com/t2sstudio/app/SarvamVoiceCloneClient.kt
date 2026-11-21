package com.t2sstudio.app

import android.util.Base64
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Sarvam Instant Voice Cloning client. Provider credentials remain in the authenticated Apps Script gateway. */
object SarvamVoiceCloneClient {
    // Replace this placeholder with the deployed Google Apps Script /exec URL.
    private const val SCRIPT_URL = "https://script.google.com/macros/s/PASTE_DEPLOYMENT_ID/exec"

    private fun centralCall(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> {
        require(!SCRIPT_URL.contains("PASTE_DEPLOYMENT_ID")) {
            "T2S shared Sarvam gateway is not configured yet. Deploy the Apps Script gateway and add its /exec URL."
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Sign in to T2S before using shared Sarvam voices.")
        val token = com.google.android.gms.tasks.Tasks.await(user.getIdToken(false)).token
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
        repeat(5) { attempt ->
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

    fun createCentralVoice(sample: File, name: String, language: String, gender: String): String {
        require(sample.isFile && sample.length() in 1024L..6_000_000L) {
            "Voice sample must be between 1 KB and 6 MB for secure upload."
        }
        val mimeType = when (sample.extension.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            else -> error("Use WAV, MP3, FLAC, M4A or AAC audio.")
        }
        val locale = when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" }
        val result = centralCall("createCentralSarvamVoice", mapOf(
            "name" to name,
            "language" to locale,
            "gender" to gender,
            "mimeType" to mimeType,
            "sampleBase64" to Base64.encodeToString(sample.readBytes(), Base64.NO_WRAP)
        ))
        return result["voiceId"]?.toString()?.takeIf { it.startsWith("svc-") }
            ?: error("T2S server did not return a saved Sarvam voice ID.")
    }

    fun generateCentral(voiceId: String, text: String, language: String, output: File) {
        require(voiceId.startsWith("svc-")) { "This profile has not been registered with Sarvam." }
        require(text.isNotBlank() && text.length <= 1000) { "Enter 1–1,000 characters." }
        val result = centralCall("generateCentralSarvamClone", mapOf(
            "voiceId" to voiceId,
            "text" to text,
            "language" to if (language == "te") "te-IN" else "en-IN"
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
