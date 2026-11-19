package com.t2sstudio.app

import android.content.Context
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * App-managed Piper/Sherpa offline synthesis.
 *
 * The caller must supply a Sherpa-converted Piper model and its tokens.txt.
 * Raw Piper downloads are deliberately rejected because Sherpa 2.x needs
 * the model metadata and external Piper phonemization frontend.
 */
class TeluguOfflineTtsEngine(
    context: Context,
    modelFile: File,
    tokensFile: File,
) : AutoCloseable {
    private val tts: OfflineTts

    init {
        require(modelFile.isFile && modelFile.length() > 10_000_000L) {
            "Converted Telugu ONNX model is missing or incomplete."
        }
        require(tokensFile.isFile && tokensFile.length() > 100L) {
            "Sherpa tokens.txt is missing. Install a converted voice pack first."
        }

        // Piper phonemizer initializes its bundled eSpeak-NG resources from app context.
        piperPhonemizeInitialize(context.applicationContext)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        tts = OfflineTts(config = config)
        require(tts.sampleRate() > 0) { "Offline Telugu voice model returned an invalid sample rate." }
    }

    fun synthesizeToWav(text: String, output: File, speed: Float = 1.0f) {
        require(text.isNotBlank()) { "Enter text to synthesize." }
        require(speed in 0.5f..2.0f) { "Speech speed must be between 0.5x and 2.0x." }

        val language = tts.lang().ifBlank { "te" }
        val phonemeResult = piperPhonemizeText(text, language)
            ?: error("Telugu phonemization failed. Check the installed phonemizer resources.")

        val phonemeCodepoints = try {
            Array(phonemeResult.numSentences) { index ->
                phonemeResult.getPhonemes(index)
            }
        } finally {
            phonemeResult.close()
        }
        require(phonemeCodepoints.isNotEmpty()) { "No Telugu phonemes were produced." }

        val generation = GenerationConfig(
            speed = speed,
            sid = 0,
            phonemeCodepoints = phonemeCodepoints,
        )
        val audio = tts.generateWithConfig("", generation)
        require(audio.samples.size >= tts.sampleRate() / 4) {
            "The offline voice returned audio that is too short. Try a longer Telugu sentence or reinstall the voice pack."
        }
        check(output.parentFile?.exists() == true || output.parentFile?.mkdirs() == true) {
            "Could not create the audio output folder."
        }
        audio.save(output.absolutePath)
        require(output.isFile && output.length() > 44L) { "Could not save generated WAV audio." }
    }

    override fun close() {
        tts.release()
    }
}
