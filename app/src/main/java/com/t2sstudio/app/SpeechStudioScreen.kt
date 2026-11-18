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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SpeechLanguage(val label: String, val locale: Locale)

@Composable
fun SpeechStudioScreen(onAudioGenerated: (AudioRecord) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedGender by remember { mutableStateOf("Female") }
    var speechRate by remember { mutableFloatStateOf(1f) }
    var speechPitch by remember { mutableFloatStateOf(1f) }
    var engineReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf("Enter text to create your first voice recording.") }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var voiceSearch by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    val availableVoices = remember { mutableStateListOf<Voice>() }
    val onlineVoices = remember { mutableStateListOf<OnlineTtsVoice>() }
    var selectedOnlineVoice by remember { mutableStateOf<OnlineTtsVoice?>(null) }
    var onlineCatalogueStatus by remember { mutableStateOf("Checking online voices…") }

    val languages = listOf(
        SpeechLanguage("English", Locale.US),
        SpeechLanguage("Telugu", Locale("te", "IN")),
        SpeechLanguage("Hindi", Locale("hi", "IN"))
    )

    fun refreshVoices(engine: TextToSpeech, locale: Locale) {
        runCatching {
            engine.setLanguage(locale)
            val matching = engine.voices.orEmpty()
                .filter { it.locale.language == locale.language }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            availableVoices.clear()
            availableVoices.addAll(matching)
            selectedVoice = matching.firstOrNull()
        }.onFailure {
            availableVoices.clear()
            selectedVoice = null
        }
    }

    LaunchedEffect(selectedLanguage) {
        onlineVoices.clear()
        selectedOnlineVoice = null
        val languageCode = when (selectedLanguage) {
            "English" -> "en"
            "Hindi" -> "hi"
            else -> null
        }
        if (languageCode == null) {
            onlineCatalogueStatus = "Online Telugu voices are not verified yet. On-device Telugu remains available."
        } else {
            onlineCatalogueStatus = "Loading online " + selectedLanguage + " voices…"
            runCatching { withContext(Dispatchers.IO) { TtsAiClient.listVoices(languageCode) } }
                .onSuccess { voices ->
                    onlineVoices.addAll(voices)
                    selectedOnlineVoice = voices.firstOrNull()
                    onlineCatalogueStatus = if (voices.isEmpty()) "No online voices returned. On-device speech is available."
                        else voices.size.toString() + " online " + selectedLanguage + " voices available."
                }
                .onFailure { onlineCatalogueStatus = "Online voice catalogue unavailable. On-device speech is available." }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Language", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    languages.forEach { language ->
                        FilterChip(
                            selected = selectedLanguage == language.label,
                            onClick = {
                                selectedLanguage = language.label
                                generatedFile = null
                                ttsEngine?.let { refreshVoices(it, language.locale) }
                            },
                            label = { Text(language.label, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Gender", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Male", "Female").forEach { gender ->
                        FilterChip(
                            selected = selectedGender == gender,
                            onClick = {
                                selectedGender = gender
                                selectedVoice = availableVoices.firstOrNull { voice ->
                                    val name = voice.name.lowercase(Locale.ROOT)
                                    if (gender == "Female") name.contains("female") || name.contains("woman") || name.contains("fem")
                                    else (name.contains("male") && !name.contains("female")) ||
                                        (name.contains("man") && !name.contains("woman"))
                                } ?: availableVoices.firstOrNull()
                            },
                            label = { Text(gender, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a voice", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            OutlinedTextField(
                value = voiceSearch,
                onValueChange = { voiceSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search voices by name or language…") },
                label = { Text("Search voice") }
            )
            val genderMatches = availableVoices.filter { voice ->
                val name = voice.name.lowercase(Locale.ROOT)
                if (selectedGender == "Female") {
                    name.contains("female") || name.contains("woman") || name.contains("fem")
                } else {
                    (name.contains("male") && !name.contains("female")) ||
                        (name.contains("man") && !name.contains("woman"))
                }
            }
            val voicePool = genderMatches.ifEmpty { availableVoices }
            val filteredVoices = voicePool.filter { voice ->
                val searchable = "${voice.name} ${voice.locale.displayName} ${voice.locale.toLanguageTag()}".lowercase(Locale.ROOT)
                searchable.contains(voiceSearch.trim().lowercase(Locale.ROOT))
            }
            Text(
                if (availableVoices.isEmpty() && engineReady) {
                    "No ${selectedLanguage.lowercase()} voices found. Install voice data in Android Settings."
                } else if (genderMatches.isEmpty() && availableVoices.isNotEmpty()) {
                    "Your Android engine does not label voice gender. Showing all installed ${selectedLanguage.lowercase()} voices."
                } else {
                    "${filteredVoices.size} installed ${selectedLanguage.lowercase()} voices"
                },
                fontSize = 11.sp,
                color = T2SSecondaryText
            )
            filteredVoices.forEach { voice ->
                val isSelected = selectedVoice?.name == voice.name
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) T2SCream else T2SBackground)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(voice.name, fontWeight = FontWeight.SemiBold, color = T2SText, fontSize = 13.sp)
                            Text(
                                "${voice.locale.displayName} • ${if (voice.isNetworkConnectionRequired) "Network required" else "On-device"}",
                                color = T2SSecondaryText,
                                fontSize = 10.sp
                            )
                            if (isSelected) Text("Selected", color = T2SOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            val engine = ttsEngine
                            if (engine != null && engineReady) {
                                engine.stop()
                                engine.setLanguage(voice.locale)
                                engine.voice = voice
                                val sample = when (voice.locale.language) {
                                    "te" -> "నమస్కారం. ఇది నా స్వర నమూనా."
                                    "hi" -> "नमस्ते। यह मेरी आवाज़ का नमूना है।"
                                    else -> "Hello. This is a sample of my voice."
                                }
                                engine.speak(sample, TextToSpeech.QUEUE_FLUSH, Bundle(), "t2s-preview-${voice.name}")
                                statusMessage = "Previewing ${voice.name}."
                            }
                        }, enabled = engineReady) { Text("▶ Preview", color = T2SOrange, fontSize = 11.sp) }
                        TextButton(onClick = {
                            selectedVoice = voice
                            ttsEngine?.let { it.stop(); it.setLanguage(voice.locale); it.voice = voice }
                            statusMessage = "${voice.name} selected."
                        }) { Text(if (isSelected) "✓" else "Select", color = T2SOrange, fontSize = 11.sp) }
                    }
                }
            }
            if (filteredVoices.isEmpty()) {
                Text("No matching voices. Try another search or install additional voice data in Android Settings.", color = T2SSecondaryText, fontSize = 12.sp)
            }
            Text("Voice search and previews use speech voices installed on this device. Availability depends on your Android TTS engine.", fontSize = 11.sp, color = T2SSecondaryText)
            Text("Online voices (TTS.ai)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            Text(onlineCatalogueStatus, fontSize = 11.sp, color = T2SSecondaryText)
            onlineVoices.filter { voice ->
                voice.gender.equals("unknown", true) || voice.gender.equals(selectedGender, true)
            }.take(12).forEach { voice ->
                FilterChip(
                    selected = selectedOnlineVoice?.id == voice.id,
                    onClick = { selectedOnlineVoice = voice; statusMessage = voice.name + " selected for online speech." },
                    label = { Text(voice.name + " • " + voice.gender, fontSize = 11.sp) }
                )
            }
            Text("Free anonymous online requests are limited to 500 characters each. Longer text, Telugu, or online errors automatically use Android TTS.", fontSize = 11.sp, color = T2SSecondaryText)
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
                val textToSpeak = inputText.trim()
                val language = languages.first { it.label == selectedLanguage }
                if (textToSpeak.isBlank()) {
                    statusMessage = "Please enter some text first."
                } else if (!engineReady || ttsEngine == null) {
                    statusMessage = "Speech engine is still starting. Please try again."
                } else {
                    val audioDirectory = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
                        "T2SStudio"
                    ).apply { mkdirs() }
                    val output = File(audioDirectory, "speech_" + System.currentTimeMillis() + ".wav")
                    isGenerating = true
                    generatedFile = null
                    statusMessage = "Trying online speech…"
                    coroutineScope.launch {
                        val onlineLanguage = when (selectedLanguage) {
                            "English" -> "en"
                            "Hindi" -> "hi"
                            else -> null
                        }
                        var onlineSuccess = false
                        val chosenOnlineVoice = selectedOnlineVoice
                        if (onlineLanguage != null && chosenOnlineVoice != null && textToSpeak.length <= 500) {
                            val onlineResult = runCatching {
                                withContext(Dispatchers.IO) {
                                    TtsAiClient.generate(textToSpeak, onlineLanguage, chosenOnlineVoice.id, speechRate, output)
                                }
                            }
                            onlineSuccess = onlineResult.isSuccess
                            if (!onlineSuccess) {
                                statusMessage = "Online unavailable (" +
                                    (onlineResult.exceptionOrNull()?.message ?: "service error") +
                                    "). Switching to Android voice…"
                            }
                        } else if (onlineLanguage != null && textToSpeak.length > 500) {
                            statusMessage = "Text exceeds the free online 500-character limit. Switching to Android voice…"
                        } else {
                            statusMessage = "Using Android voice for Telugu…"
                        }

                        if (onlineSuccess) {
                            isGenerating = false
                            generatedFile = output
                            onAudioGenerated(
                                AudioRecord(
                                    id = java.util.UUID.randomUUID().toString(),
                                    filePath = output.absolutePath,
                                    textPreview = textToSpeak.take(180),
                                    language = selectedLanguage,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            statusMessage = "Online speech audio is ready and saved in T2S Studio."
                        } else {
                            output.delete()
                            val engine = ttsEngine
                            if (engine == null || !engineReady) {
                                isGenerating = false
                                statusMessage = "Online speech failed and Android speech engine is unavailable."
                            } else {
                                val languageResult = engine.setLanguage(language.locale)
                                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                                ) {
                                    isGenerating = false
                                    statusMessage = language.label + " voice data is not installed or supported on this device."
                                } else {
                                    selectedVoice?.let { engine.voice = it }
                                    engine.setSpeechRate(speechRate)
                                    engine.setPitch(speechPitch)
                                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                        override fun onStart(utteranceId: String?) { }
                                        override fun onDone(utteranceId: String?) {
                                            isGenerating = false
                                            generatedFile = output
                                            onAudioGenerated(
                                                AudioRecord(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    filePath = output.absolutePath,
                                                    textPreview = textToSpeak.take(180),
                                                    language = selectedLanguage,
                                                    createdAt = System.currentTimeMillis()
                                                )
                                            )
                                            statusMessage = "Android speech audio is ready and saved in T2S Studio."
                                        }
                                        @Deprecated("Deprecated in Java")
                                        override fun onError(utteranceId: String?) {
                                            isGenerating = false
                                            statusMessage = "Could not generate audio with either speech provider."
                                        }
                                        override fun onError(utteranceId: String?, errorCode: Int) {
                                            isGenerating = false
                                            statusMessage = "Android speech generation failed (code " + errorCode + ")."
                                        }
                                    })
                                    val result = engine.synthesizeToFile(textToSpeak, Bundle(), output, "t2s-" + output.name)
                                    if (result != TextToSpeech.SUCCESS) {
                                        isGenerating = false
                                        statusMessage = "Android speech engine could not generate this audio."
                                    }
                                }
                            }
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
            "Online speech is attempted first for supported languages. If unavailable, T2S Studio automatically uses your installed Android speech engine. Audio stays in app storage.",
            fontSize = 11.sp,
            color = T2SSecondaryText
        )
    }
}
