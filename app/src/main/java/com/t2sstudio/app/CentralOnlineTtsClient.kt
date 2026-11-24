package com.t2sstudio.app

import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class OnlineTtsVoice(
    val id: String,
    val name: String,
    val language: String,
    val gender: String,
    val licence: String,
    val attribution: String,
    val provider: String
)

/**
 * Authenticated client for centrally managed India and Vakyam credentials.
 * Provider secrets stay in Cloudflare Worker secrets and are never sent to Android.
 */
object CentralOnlineTtsClient {
    private const val BASE_URL = "https://t2s.amruth4official.workers.dev"

    private fun authorizationHeader(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Sign in to T2S Studio to use online TTS.")
        val token = Tasks.await(user.getIdToken(false)).token
            ?: error("Could not verify your T2S sign-in. Please sign in again.")
        return "Bearer $token"
    }

    private fun open(path: String, method: String, body: String? = null): HttpURLConnection {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = if (method == "POST") 120_000 else 30_000
        connection.setRequestProperty("Authorization", authorizationHeader())
        val appCheckToken = Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(false)).token
            ?: error("Could not verify this app installation. Please try again.")
        connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken)
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return connection
    }

    private fun responseBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun requestError(connection: HttpURLConnection, provider: String): Nothing {
        val body = responseBody(connection)
        val detail = runCatching {
            val json = org.json.JSONObject(body)
            json.optString("error").ifBlank { json.optString("message") }
        }.getOrDefault("")
        val code = connection.responseCode
        throw IllegalStateException(when (code) {
            401 -> detail.ifBlank { "Your T2S sign-in expired. Please sign in again." }
            429 -> detail.ifBlank { "$provider rate limit reached. Please try again later." }
            else -> detail.ifBlank { "$provider request failed (HTTP $code)." }
        })
    }

    fun listVoices(provider: String, language: String): List<OnlineTtsVoice> {
        require(provider == "India API" || provider == "Vakyam") { "Unsupported online TTS provider." }
        require(language in listOf("en", "te", "hi")) { "Unsupported voice language." }
        val route = if (provider == "Vakyam") "/v1/vakyam/voices" else "/v1/india/voices"
        val encodedLanguage = URLEncoder.encode(language, "UTF-8")
        val connection = open("$route?language=$encodedLanguage", "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) requestError(connection, provider)
            val root = org.json.JSONObject(responseBody(connection))
            val voices = root.optJSONArray("voices") ?: return emptyList()
            val locale = when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" }
            return (0 until voices.length()).mapNotNull { index ->
                val item = voices.optJSONObject(index) ?: return@mapNotNull null
                if (provider == "Vakyam") {
                    val name = item.optString("voice_name").ifBlank { item.optString("name") }
                    if (name.isBlank()) return@mapNotNull null
                    OnlineTtsVoice(
                        id = name, name = name, language = locale,
                        gender = item.optString("gender", "Unknown").replaceFirstChar { it.uppercase() },
                        licence = "Vakyam Raaga", attribution = "Vakyam", provider = "Vakyam"
                    )
                } else {
                    val id = item.optString("id")
                    val voiceLanguage = item.optString("language")
                    if (id.isBlank() || voiceLanguage.isBlank() ||
                        !voiceLanguage.substringBefore('-').equals(language, true) ||
                        item.optBoolean("non_commercial", false)) return@mapNotNull null
                    OnlineTtsVoice(
                        id = id,
                        name = item.optString("name").ifBlank { item.optString("speaker", id) },
                        language = voiceLanguage,
                        gender = item.optString("gender", "Voice"),
                        licence = item.optString("licence"),
                        attribution = item.optString("attribution"),
                        provider = "India API"
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun generate(
        provider: String,
        text: String,
        voice: String,
        language: String,
        speed: Float,
        output: File,
        format: String = "mp3"
    ) {
        require(text.isNotBlank()) { "Enter text to generate speech." }
        require(text.length <= 3000) { "Free online TTS supports up to 3,000 characters per request." }
        require(voice.isNotBlank()) { "Choose an online voice first." }
        require(provider == "India API" || provider == "Vakyam") { "Unsupported online TTS provider." }
        val safeFormat = format.lowercase(Locale.ROOT).let {
            if (it in listOf("mp3", "wav", "ogg")) it else "mp3"
        }
        val route = if (provider == "Vakyam") "/v1/vakyam/tts" else "/v1/india/tts"
        val payload = org.json.JSONObject().apply {
            put("text", text)
            put("voice", voice)
            put("language", language)
            put("speed", speed.coerceIn(0.5f, 2f).toDouble())
            put("format", safeFormat)
        }.toString()
        val connection = open(route, "POST", payload)
        try {
            val code = connection.responseCode
            if (code !in 200..299) requestError(connection, provider)
            val root = org.json.JSONObject(responseBody(connection))
            val encoded = root.optString("audioBase64")
            require(encoded.isNotBlank()) { "$provider returned an empty audio response." }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val isWav = bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
            val hasId3 = bytes.size > 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "ID3"
            val hasMpeg = bytes.size > 2 && (bytes[0].toInt() and 0xFF) == 0xFF &&
                (bytes[1].toInt() and 0xE0) == 0xE0
            val isOgg = bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "OggS"
            require(bytes.size > 1024 && when (safeFormat) {
                "wav" -> isWav
                "ogg" -> isOgg
                else -> hasId3 || hasMpeg
            }) { "$provider returned invalid audio for the requested $safeFormat format." }
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
