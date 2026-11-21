package com.t2sstudio.app

import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import java.io.File
import java.util.Locale

/**
 * Authenticated callable client for centrally managed India and Vakyam credentials.
 * Provider secrets are never sent to or stored by the Android application.
 */
object CentralOnlineTtsClient {
    private val functions by lazy { FirebaseFunctions.getInstance("asia-south1") }

    private fun call(name: String, data: Map<String, Any?>): Map<*, *> {
        val result = Tasks.await(functions.getHttpsCallable(name).call(data))
        return result.getData() as? Map<*, *> ?: error("TTS backend returned an invalid response.")
    }

    fun listVoices(provider: String, language: String): List<OnlineTtsVoice> {
        require(provider == "India API" || provider == "Vakyam") { "Unsupported online TTS provider." }
        require(language in listOf("en", "te", "hi")) { "Unsupported voice language." }
        val function = if (provider == "Vakyam") "listCentralVakyamVoices" else "listCentralIndiaTtsVoices"
        val response = call(function, mapOf("language" to language))
        val voices = response["voices"] as? List<*> ?: return emptyList()
        val locale = when (language) { "te" -> "te-IN"; "hi" -> "hi-IN"; else -> "en-IN" }
        return voices.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            if (provider == "Vakyam") {
                val name = (item["voice_name"] as? String).orEmpty().ifBlank {
                    (item["name"] as? String).orEmpty()
                }
                if (name.isBlank()) return@mapNotNull null
                OnlineTtsVoice(
                    id = name, name = name, language = locale,
                    gender = (item["gender"] as? String ?: "Unknown").replaceFirstChar { it.uppercase() },
                    licence = "Vakyam free plan", attribution = "Vakyam Raaga", provider = "Vakyam"
                )
            } else {
                val id = item["id"] as? String ?: return@mapNotNull null
                val voiceLanguage = item["language"] as? String ?: return@mapNotNull null
                if (!voiceLanguage.substringBefore('-').equals(language, true) ||
                    item["non_commercial"] == true) return@mapNotNull null
                OnlineTtsVoice(
                    id = id,
                    name = (item["name"] as? String).orEmpty().ifBlank { item["speaker"] as? String ?: id },
                    language = voiceLanguage,
                    gender = item["gender"] as? String ?: "Voice",
                    licence = item["licence"] as? String ?: "",
                    attribution = item["attribution"] as? String ?: "",
                    provider = "India API"
                )
            }
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
        val function = if (provider == "Vakyam") "generateCentralVakyamTts" else "generateCentralIndiaTts"
        val response = call(function, mapOf(
            "text" to text, "voice" to voice, "language" to language,
            "speed" to speed.coerceIn(0.5f, 2f).toDouble(), "format" to safeFormat
        ))
        val encoded = response["audioBase64"] as? String
            ?: error("$provider returned an empty audio response.")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size > 1024) { "$provider returned an empty or incomplete audio file." }
        val isWav = bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
        val hasId3 = bytes.size > 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "ID3"
        val hasMpeg = bytes.size > 2 && (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xE0) == 0xE0
        val isOgg = bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "OggS"
        require(when (safeFormat) {
            "wav" -> isWav
            "ogg" -> isOgg
            else -> hasId3 || hasMpeg
        }) { "$provider returned audio that does not match the requested $safeFormat format." }
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)
    }
}
