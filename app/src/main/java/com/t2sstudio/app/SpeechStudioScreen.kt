package com.t2sstudio.app

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import java.io.File
import java.util.Locale

private data class SpeechLanguage(val label: String, val locale: Locale)

@Composable
fun SpeechStudioScreen() {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var speechRate by remember { mutableFloatStateOf(1f) }
    var speechPitch by remember { mutableFloatStateOf(1f) }
    var engineReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf("Enter text to create your first voice recording.") }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val languages = listOf(
        SpeechLanguage("English", Locale.US),
        SpeechLanguage("Telugu", Locale("te", "IN")),
        SpeechLanguage("Hindi", Locale("hi", "IN"))
    )

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                statusMessage = "Text-to-speech engine could not start. Check your Android speech settings."
            }
        }
        ttsEngine = engine
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            engine.stop()
            engine.shutdown()
            ttsEngine = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Text to Speech", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
            Text("Turn your words into spoken audio.", fontSize = 14.sp, color = T2SSecondaryText)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = T2SBackground)
        ) {
            Column(Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your text", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it; generatedFile = null },
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    placeholder = { Text("Type or paste your text here…") },
                    supportingText = { Text("${inputText.length} characters") }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Choose language", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                languages.forEach { language ->
                    FilterChip(
                        selected = selectedLanguage == language.label,
                        onClick = { selectedLanguage = language.label; generatedFile = null },
                        label = { Text(language.label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Speaking speed", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Spacer(Modifier.weight(1f))
                Text("${"%.1f".format(speechRate)}x", color = T2SOrange, fontWeight = FontWeight.Bold)
            }
            Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.5f..2f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Slow", color = T2SSecondaryText, fontSize = 11.sp)
                Text("Fast", color = T2SSecondaryText, fontSize = 11.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Voice pitch", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Spacer(Modifier.weight(1f))
                Text("${"%.1f".format(speechPitch)}x", color = T2SOrange, fontWeight = FontWeight.Bold)
            }
            Slider(value = speechPitch, onValueChange = { speechPitch = it }, valueRange = 0.5f..2f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Low", color = T2SSecondaryText, fontSize = 11.sp)
                Text("High", color = T2SSecondaryText, fontSize = 11.sp)
            }
        }

        Button(
            onClick = {
                val engine = ttsEngine
                val language = languages.first { it.label == selectedLanguage }
                if (inputText.isBlank()) {
                    statusMessage = "Please enter some text first."
                } else if (!engineReady || engine == null) {
                    statusMessage = "Speech engine is still starting. Please try again."
                } else {
                    val languageResult = engine.setLanguage(language.locale)
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                        languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        statusMessage = "${language.label} voice data is not installed or supported on this device."
                    } else {
                        engine.setSpeechRate(speechRate)
                        engine.setPitch(speechPitch)
                        val output = File(context.cacheDir, "t2s_${System.currentTimeMillis()}.wav")
                        isGenerating = true
                        generatedFile = null
                        statusMessage = "Generating audio…"
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { }
                            override fun onDone(utteranceId: String?) {
                                isGenerating = false
                                generatedFile = output
                                statusMessage = "Your speech audio is ready."
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                isGenerating = false
                                statusMessage = "Could not generate audio. Please try again."
                            }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                isGenerating = false
                                statusMessage = "Audio generation failed (code $errorCode)."
                            }
                        })
                        val params = Bundle()
                        val result = engine.synthesizeToFile(inputText, params, output, "t2s-${output.name}")
                        if (result != TextToSpeech.SUCCESS) {
                            isGenerating = false
                            statusMessage = "The selected speech engine could not generate this audio."
                        }
                    }
                }
            },
            enabled = !isGenerating && inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = T2SBackground, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (isGenerating) "Generating…" else "Generate Speech", fontWeight = FontWeight.Bold)
        }

        if (generatedFile != null) {
            Button(
                onClick = {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(generatedFile!!.absolutePath)
                            setOnCompletionListener { it.release(); mediaPlayer = null }
                            prepare()
                            start()
                        }
                        statusMessage = "Playing your generated speech."
                    } catch (error: Exception) {
                        statusMessage = "Unable to play the generated audio on this device."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = T2SCream, contentColor = T2SText)
            ) {
                Text("▶  Play generated audio", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(statusMessage, fontSize = 13.sp, color = T2SSecondaryText)
        Text(
            "Speech is generated on your Android device using its installed text-to-speech engine. Voice availability depends on your device.",
            fontSize = 11.sp,
            color = T2SSecondaryText
        )
    }
}
