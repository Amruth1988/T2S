package com.t2sstudio.app

import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
    var isPlaying by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    val availableVoices = remember { mutableStateListOf<Voice>() }

    val languages = listOf(
        SpeechLanguage("English", Locale.US),
        SpeechLanguage("Telugu", Locale("te", "IN")),
        SpeechLanguage("Hindi", Locale("hi", "IN"))
    )

    fun refreshVoices(engine: TextToSpeech, locale: Locale) {
        runCatching {
            engine.setLanguage(locale)
            val matching = engine.voices.orEmpty()
                .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            availableVoices.clear()
            availableVoices.addAll(matching)
            selectedVoice = matching.firstOrNull()
        }.onFailure {
            availableVoices.clear()
            selectedVoice = null
        }
    }

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { result ->
            engineReady = result == TextToSpeech.SUCCESS
            if (result == TextToSpeech.SUCCESS) {
                refreshVoices(engine, languages.first { it.label == selectedLanguage }.locale)
            } else {
                statusMessage = "Text-to-speech engine could not start. Check Android speech settings."
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

    fun shareAudio(file: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share speech audio"))
        }.onFailure {
            statusMessage = "Could not open the share menu. Please try again."
        }
    }

    fun saveToDownloads(file: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/T2S Studio")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create download entry")
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not open download output")
                val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, complete, null, null)
                statusMessage = "Saved to Downloads/T2S Studio."
            } else {
                val target = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), file.name)
                file.copyTo(target, overwrite = true)
                statusMessage = "Saved in T2S Studio app storage: Music/${file.name}"
            }
        }.onFailure {
            statusMessage = "Unable to save audio: ${it.localizedMessage ?: "storage error"}"
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
                        onClick = {
                            selectedLanguage = language.label
                            generatedFile = null
                            ttsEngine?.let { refreshVoices(it, language.locale) }
                        },
                        label = { Text(language.label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Voice", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            TextButton(onClick = { voiceMenuExpanded = true }, enabled = availableVoices.isNotEmpty()) {
                Text(selectedVoice?.name ?: if (engineReady) "No installed voice found" else "Loading voices…", color = T2SOrange)
            }
            DropdownMenu(expanded = voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                availableVoices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.name) },
                        onClick = { selectedVoice = voice; voiceMenuExpanded = false }
                    )
                }
            }
            Text("Only voices installed on this device are listed.", fontSize = 11.sp, color = T2SSecondaryText)
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
                        selectedVoice?.let { engine.voice = it }
                        engine.setSpeechRate(speechRate)
                        engine.setPitch(speechPitch)
                        val audioDirectory = File(
                            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
                            "T2SStudio"
                        ).apply { mkdirs() }
                        val output = File(audioDirectory, "speech_${System.currentTimeMillis()}.wav")
                        isGenerating = true
                        generatedFile = null
                        statusMessage = "Generating audio…"
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { }
                            override fun onDone(utteranceId: String?) {
                                isGenerating = false
                                generatedFile = output
                                statusMessage = "Speech audio is ready and saved in T2S Studio."
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
                        val result = engine.synthesizeToFile(inputText, Bundle(), output, "t2s-${output.name}")
                        if (result != TextToSpeech.SUCCESS) {
                            isGenerating = false
                            statusMessage = "The selected speech engine could not generate this audio."
                        }
                    }
                }
            },
            enabled = !isGenerating && inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = T2SBackground, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (isGenerating) "Generating…" else "Generate Speech", fontWeight = FontWeight.Bold)
        }

        generatedFile?.let { file ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        try {
                            val player = mediaPlayer
                            if (player?.isPlaying == true) {
                                player.pause()
                                isPlaying = false
                                statusMessage = "Playback paused."
                            } else if (player != null) {
                                player.start()
                                isPlaying = true
                                statusMessage = "Playing audio."
                            } else {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(file.absolutePath)
                                    setOnCompletionListener {
                                        it.release()
                                        mediaPlayer = null
                                        isPlaying = false
                                        statusMessage = "Playback finished."
                                    }
                                    prepare()
                                    start()
                                }
                                isPlaying = true
                                statusMessage = "Playing audio."
                            }
                        } catch (error: Exception) {
                            statusMessage = "Unable to play audio on this device."
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SCream, contentColor = T2SText)
                ) {
                    Text(if (isPlaying) "Ⅱ  Pause" else "▶  Play", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        isPlaying = false
                        statusMessage = "Playback stopped."
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SCream, contentColor = T2SText)
                ) { Text("■  Stop", fontWeight = FontWeight.SemiBold) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { saveToDownloads(file) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)
                ) { Text("↓  Download") }
                Button(
                    onClick = { shareAudio(file) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)
                ) { Text("↗  Share") }
            }
        }

        Text(statusMessage, fontSize = 13.sp, color = T2SSecondaryText)
        Text(
            "Speech is generated on your Android device. Audio is kept in app storage; download or share it when ready. Voice availability depends on your installed speech engine.",
            fontSize = 11.sp,
            color = T2SSecondaryText
        )
    }
}
