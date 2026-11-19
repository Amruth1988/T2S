package com.t2sstudio.app

import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
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

private fun voiceGender(name: String): String? {
    val normalized = name.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    val tokens = normalized.split(Regex("\\s+")).toSet()
    val female = "female" in tokens || "woman" in tokens || "women" in tokens ||
        "feminine" in tokens || "fem" in tokens
    val male = "male" in tokens || "man" in tokens || "men" in tokens ||
        "masculine" in tokens
    return when {
        female && !male -> "Female"
        male && !female -> "Male"
        else -> null
    }
}


@Composable
fun SpeechStudioScreen(onAudioGenerated: (AudioRecord) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedGender by remember { mutableStateOf("All") }
    var speechRate by remember { mutableFloatStateOf(1f) }
    var speechPitch by remember { mutableFloatStateOf(1f) }
    var engineReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf("Enter text to create your first voice recording.") }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPreviewing by remember { mutableStateOf(false) }
    var activePreviewKey by remember { mutableStateOf<String?>(null) }
    var outputFormat by remember { mutableStateOf("WAV") }
    var autoPlayAfterGeneration by remember { mutableStateOf(false) }
    var voiceSearch by remember { mutableStateOf("") }
    var voiceSearchExpanded by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    val availableVoices = remember { mutableStateListOf<Voice>() }
    val onlineVoices = remember { mutableStateListOf<OnlineTtsVoice>() }
    var selectedOnlineVoice by remember { mutableStateOf<OnlineTtsVoice?>(null) }
    var onlineCatalogueStatus by remember { mutableStateOf("Checking online voices…") }
    val myVoices = remember { mutableStateListOf<SavedUserVoice>() }
    var selectedMyVoice by remember { mutableStateOf<SavedUserVoice?>(null) }
    val ttsApiKey = remember { storedTtsApiKey(context) }

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
            selectedVoice = matching.firstOrNull { selectedGender == "All" || voiceGender(it.name) == selectedGender }
        }.onFailure {
            availableVoices.clear()
            selectedVoice = null
        }
    }

    LaunchedEffect(ttsApiKey) {
        if (ttsApiKey.isNotBlank()) {
            runCatching { withContext(Dispatchers.IO) { MyVoicesClient.list(ttsApiKey) } }
                .onSuccess { saved -> myVoices.clear(); myVoices.addAll(saved) }
        }
    }

    LaunchedEffect(selectedLanguage) {
        onlineVoices.clear()
        selectedOnlineVoice = null
        val languageCode = when (selectedLanguage) {
            "English" -> "en"
            "Hindi" -> "hi"
            "Telugu" -> "te"
            else -> "en"
        }
        onlineCatalogueStatus = "Loading online " + selectedLanguage + " voices…"
        runCatching { withContext(Dispatchers.IO) { TtsAiClient.listVoices(languageCode) } }
            .onSuccess { voices ->
                onlineVoices.addAll(voices)
                selectedOnlineVoice = voices.firstOrNull { selectedGender == "All" || it.gender.equals(selectedGender, ignoreCase = true) }
                onlineCatalogueStatus = if (voices.isEmpty()) "No online " + selectedLanguage + " voices returned. Showing installed voices where available."
                    else voices.size.toString() + " online " + selectedLanguage + " voices available."
            }
            .onFailure { onlineCatalogueStatus = "Online " + selectedLanguage + " voice catalogue unavailable. Showing installed voices where available." }
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

    fun stopVoicePreview() {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        ttsEngine?.stop()
        isPlaying = false
        isPreviewing = false
        activePreviewKey = null
    }

    fun previewOnlineVoice(voiceId: String, model: String? = null, personal: Boolean = false, previewKey: String = voiceId, previewUrl: String? = null) {
        if (isPreviewing && activePreviewKey == previewKey) {
            stopVoicePreview()
            statusMessage = "Preview stopped."
            return
        }
        stopVoicePreview()
        val languageCode = when (selectedLanguage) { "English" -> "en"; "Hindi" -> "hi"; "Telugu" -> "te"; else -> "en" }
        if (languageCode == null) {
            statusMessage = "Online preview is not available for Telugu yet. Use an installed Telugu voice."
            return
        }
        isPreviewing = true
        activePreviewKey = previewKey
        statusMessage = "Generating preview for $previewKey…"
        coroutineScope.launch {
            val previewFile = File(context.cacheDir, "voice_preview_${System.currentTimeMillis()}.wav")
            val sample = if (selectedLanguage == "Hindi") "नमस्ते। यह आवाज़ का नमूना है।"
                else "Hello. This is a sample of the selected voice."
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    TtsAiClient.generate(
                        sample, languageCode, voiceId, 1f, previewFile,
                        apiKey = ttsApiKey,
                        model = model,
                        format = "wav"
                    )
                }
            }
            if (result.isFailure) {
                previewFile.delete()
                isPreviewing = false
                activePreviewKey = null
                statusMessage = "Preview failed: ${result.exceptionOrNull()?.message ?: "voice service unavailable"}"
                return@launch
            }
            if (activePreviewKey != previewKey) {
                previewFile.delete()
                return@launch
            }
            runCatching {
                val player = MediaPlayer()
                mediaPlayer = player
                player.setDataSource(previewFile.absolutePath)
                player.setOnPreparedListener {
                    if (activePreviewKey == previewKey) {
                        it.start()
                        isPlaying = true
                        statusMessage = "Playing only: $previewKey"
                    } else {
                        it.release()
                        previewFile.delete()
                    }
                }
                player.setOnCompletionListener {
                    it.release()
                    if (mediaPlayer === it) mediaPlayer = null
                    previewFile.delete()
                    isPlaying = false
                    isPreviewing = false
                    activePreviewKey = null
                    statusMessage = "Preview finished: $previewKey"
                }
                player.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                    previewFile.delete()
                    isPlaying = false
                    isPreviewing = false
                    activePreviewKey = null
                    statusMessage = "Audio could not be played for $previewKey."
                    true
                }
                player.prepareAsync()
            }.onFailure {
                previewFile.delete()
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
                isPreviewing = false
                activePreviewKey = null
                statusMessage = "Preview playback error: ${it.message ?: "unknown error"}"
            }
        }
    }

    fun playGeneratedFile(file: File) {
        stopVoicePreview()
        runCatching {
            mediaPlayer?.release()
            val player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { it.start(); isPlaying = true; statusMessage = "Auto-playing generated audio." }
            player.setOnCompletionListener { it.release(); mediaPlayer = null; isPlaying = false; statusMessage = "Playback finished." }
            player.setOnErrorListener { mp, _, _ -> mp.release(); mediaPlayer = null; isPlaying = false; statusMessage = "Audio playback failed. Try downloading the file."; true }
            player.prepareAsync()
        }.onFailure { statusMessage = "Unable to play generated audio: ${it.message ?: "unknown error"}" }
    }

    fun shareAudio(file: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("mp3", true)) "audio/mpeg" else "audio/wav"
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
                    put(MediaStore.Downloads.MIME_TYPE, if (file.extension.equals("mp3", true)) "audio/mpeg" else "audio/wav")
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Your text", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = T2SText)
                        Text("Write or paste your script below", fontSize = 12.sp, color = T2SSecondaryText)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF0E5), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("SCRIPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF47721), letterSpacing = 1.sp)
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it; generatedFile = null },
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    placeholder = {
                        Text(
                            "Start typing your text here…\n\nYou can also paste your script.",
                            color = Color(0xFF929DB0),
                            lineHeight = 22.sp
                        )
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF47721),
                        unfocusedBorderColor = Color(0xFFD7DFEC),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        cursorColor = Color(0xFFF47721),
                        focusedTextColor = Color(0xFF101B35),
                        unfocusedTextColor = Color(0xFF101B35)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = Color(0xFF101B35)
                    ),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text("${inputText.length} characters", fontSize = 11.sp, color = T2SSecondaryText)
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = T2SBackground)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Language", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                var languageMenuExpanded by remember { mutableStateOf(false) }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val languageDropdownWidth = maxWidth
                    OutlinedButton(
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (languageMenuExpanded) Color(0xFFF47721) else Color(0xFFD5DCE8)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF5F7FB),
                            contentColor = Color(0xFF101B35)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            selectedLanguage,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("⌄", color = Color(0xFFF47721), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                        modifier = Modifier
                            .width(languageDropdownWidth)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFDCE3EF), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    ) {
                        languages.forEach { language ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        language.label,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (selectedLanguage == language.label) Color(0xFFF47721) else Color(0xFF101B35),
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedLanguage == language.label) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    selectedLanguage = language.label
                                    generatedFile = null
                                    languageMenuExpanded = false
                                    ttsEngine?.let { refreshVoices(it, language.locale) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Gender", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All", "Male", "Female").forEach { gender ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = selectedGender == gender,
                            onClick = {
                                selectedGender = gender
                                selectedVoice = availableVoices.firstOrNull { voice ->
                                    voiceGender(voice.name) == gender
                                }
                                selectedOnlineVoice = onlineVoices.firstOrNull { voice ->
                                    voice.gender.equals(gender, ignoreCase = true)
                                }
                            },
                            label = { Text(gender, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
          }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = T2SBackground)) {
          Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Voice library", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                }
                Text("VOICE PICKER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101B35))
            }
            val allInstalledForLanguage = availableVoices.toList()
            val genderMatches = allInstalledForLanguage.filter { voice -> selectedGender == "All" || voiceGender(voice.name) == selectedGender }
            val installedPool = if (genderMatches.isNotEmpty()) genderMatches else allInstalledForLanguage
            val onlineGenderMatches = onlineVoices.filter { voice -> selectedGender == "All" || voice.gender.equals(selectedGender, true) }
            val onlinePool = if (onlineGenderMatches.isNotEmpty()) onlineGenderMatches else onlineVoices.toList()
            val searchTerm = voiceSearch.trim().lowercase(Locale.ROOT)
            val searchedInstalled = installedPool.filter { voice ->
                "${voice.name} ${voice.locale.displayName} ${voice.locale.toLanguageTag()}".lowercase(Locale.ROOT).contains(searchTerm)
            }
            val searchedOnline = onlinePool.filter { voice ->
                "${voice.name} ${voice.language}".lowercase(Locale.ROOT).contains(searchTerm)
            }
            val filteredInstalled = if (searchedInstalled.isNotEmpty() || searchTerm.isBlank()) searchedInstalled else installedPool.take(5)
            val filteredOnline = if (searchedOnline.isNotEmpty() || searchTerm.isBlank()) searchedOnline else onlinePool.take(5)
            val filteredPersonal = myVoices.filter { voice ->
                "${voice.name} ${voice.model}".lowercase(Locale.ROOT).contains(searchTerm)
            }
            val showingSuggestions = searchTerm.isNotBlank() && searchedInstalled.isEmpty() && searchedOnline.isEmpty() && filteredPersonal.isEmpty()

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = voiceSearch,
                    onValueChange = {
                        voiceSearch = it
                        voiceSearchExpanded = it.isNotBlank()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    placeholder = { Text("Search by voice name") },
                    label = { Text("Voice Search") },
                    leadingIcon = { Text("⌕", fontSize = 25.sp, color = T2SOrange, fontWeight = FontWeight.Bold) },
                    trailingIcon = {
                        if (voiceSearch.isNotBlank()) {
                            TextButton(onClick = { voiceSearch = ""; voiceSearchExpanded = false }) { Text("✕ Clear") }
                        }
                    }
                )
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState())
                        .border(1.dp, T2SCream, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredInstalled.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(if (showingSuggestions) "SUGGESTED INSTALLED VOICES" else "INSTALLED VOICES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("${filteredInstalled.size}", color = Color(0xFF58677F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            },
                            onClick = {},
                            enabled = false
                        )
                        filteredInstalled.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(installedVoiceDisplayName(voice), color = Color(0xFF101B35), fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("${voice.locale.getDisplayLanguage(Locale.getDefault())} • ${voiceGender(voice.name)} • On device", color = Color(0xFF58677F), fontSize = 11.sp, maxLines = 1)
                                        if (isPlaying && activePreviewKey == "installed:${voice.name}") {
                                            VoicePlaybackWaveform()
                                        }
                                    }
                                },
                                onClick = {
                                    selectedVoice = voice
                                    selectedOnlineVoice = null
                                    selectedMyVoice = null
                                    voiceSearch = voice.name
                                    voiceSearchExpanded = false
                                    ttsEngine?.let { it.stop(); it.setLanguage(voice.locale); it.voice = voice }
                                    statusMessage = "${voice.name} selected."
                                },
                                trailingIcon = {
                                    TextButton(onClick = {
                                        val engine = ttsEngine
                                        if (engine != null && engineReady) {
                                            if (isPreviewing && activePreviewKey == "installed:${voice.name}") { stopVoicePreview(); statusMessage = "Preview stopped."; return@TextButton }
                                            stopVoicePreview()
                                            activePreviewKey = "installed:${voice.name}"
                                            isPreviewing = true
                                            val mainHandler = Handler(Looper.getMainLooper())
                                            engine.setLanguage(voice.locale)
                                            engine.voice = voice
                                            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                override fun onStart(utteranceId: String?) {
                                                    mainHandler.post { isPlaying = true; statusMessage = "Playing ${voice.name} preview." }
                                                }
                                                override fun onDone(utteranceId: String?) {
                                                    mainHandler.post { isPlaying = false; isPreviewing = false; activePreviewKey = null; statusMessage = "${voice.name} preview finished." }
                                                }
                                                @Deprecated("Deprecated in Java")
                                                override fun onError(utteranceId: String?) {
                                                    mainHandler.post { isPlaying = false; isPreviewing = false; activePreviewKey = null; statusMessage = "Could not play ${voice.name}. Check installed voice data." }
                                                }
                                                override fun onError(utteranceId: String?, errorCode: Int) {
                                                    mainHandler.post { isPlaying = false; isPreviewing = false; activePreviewKey = null; statusMessage = "Voice preview failed (code $errorCode)." }
                                                }
                                            })
                                            val sample = when (voice.locale.language) {
                                                "te" -> "నమస్కారం. ఇది స్వర నమూనా."
                                                "hi" -> "नमस्ते। यह आवाज़ का नमूना है।"
                                                else -> "Hello. This is a sample voice preview."
                                            }
                                            val speakResult = engine.speak(sample, TextToSpeech.QUEUE_FLUSH, Bundle(), "t2s-preview-${voice.name}")
                                            if (speakResult == TextToSpeech.ERROR) {
                                                isPreviewing = false
                                                isPlaying = false
                                                statusMessage = "Android could not start this voice preview."
                                            } else {
                                                statusMessage = "Preparing ${voice.name} preview…"
                                            }
                                        } else {
                                            statusMessage = "Android speech engine is not ready yet."
                                        }
                                    }, enabled = engineReady) {
                                        Text(
                                            if (isPreviewing && activePreviewKey == "installed:${voice.name}") "Ⅱ" else "▶",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(Color(0xFFF47721), androidx.compose.foundation.shape.CircleShape)
                                                .padding(horizontal = 13.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                    if (filteredOnline.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(if (showingSuggestions) "SUGGESTED ONLINE VOICES" else "ONLINE VOICES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("${filteredOnline.size}", color = Color(0xFF58677F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            },
                            onClick = {},
                            enabled = false
                        )
                        filteredOnline.forEach { voice ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FB)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCE3EF))
                            ) {
                              Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f).clickable {
                                    selectedOnlineVoice = voice
                                    selectedVoice = null
                                    selectedMyVoice = null
                                    voiceSearch = voice.name
                                    voiceSearchExpanded = false
                                    statusMessage = voice.name + " selected for online speech."
                                }) {
                                    Text(voice.name, color = T2SText, fontWeight = FontWeight.SemiBold)
                                    Text("Online • " + voice.gender + " • " + selectedLanguage, color = Color(0xFF58677F), fontSize = 11.sp)
                                }
                                TextButton(enabled = true, onClick = { previewOnlineVoice(voice.id, previewKey = "online:${voice.id}", previewUrl = voice.previewUrl) }) {
                                    Text(if (activePreviewKey == "online:${voice.id}" && isPreviewing) "■ Stop" else "▶ Preview", color = Color(0xFFF47721), fontWeight = FontWeight.Bold)
                                }
                              }
                            }
                        }
                    }
                    if (filteredPersonal.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("MY CLONED VOICES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            onClick = {},
                            enabled = false
                        )
                        filteredPersonal.forEach { voice ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f).clickable {
                                    selectedMyVoice = voice
                                    selectedVoice = null
                                    selectedOnlineVoice = null
                                    voiceSearch = voice.name
                                    voiceSearchExpanded = false
                                    statusMessage = voice.name + " selected."
                                }) {
                                    Text(voice.name, color = T2SText, fontWeight = FontWeight.SemiBold)
                                    Text("Personal voice", color = T2SSecondaryText, fontSize = 11.sp)
                                }
                                TextButton(enabled = true, onClick = { previewOnlineVoice(voice.id, voice.model, personal = true, previewKey = "personal:${voice.id}", previewUrl = voice.previewUrl) }) {
                                    Text(if (activePreviewKey == "personal:${voice.id}" && isPreviewing) "■ Stop" else "▶ Preview", color = T2SOrange)
                                }
                            }
                        }
                    }
                    if (filteredInstalled.isEmpty() && filteredOnline.isEmpty() && filteredPersonal.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (onlineCatalogueStatus.startsWith("Loading")) "Loading voices…" else "No voices are installed or available for this language yet. Try another language.", color = T2SSecondaryText) },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }
            Text(
                "${filteredInstalled.size + filteredOnline.size + filteredPersonal.size} voices shown • ${selectedGender} • ${selectedLanguage}",
                fontSize = 11.sp,
                color = T2SSecondaryText
            )
            Text(
                if (onlineCatalogueStatus.startsWith("Loading")) onlineCatalogueStatus
                else if (showingSuggestions) "No exact match found. Showing suggested voices for this language." else "Search results include installed, online and your personal voices. Scroll inside the results list to see more.",
                fontSize = 11.sp,
                color = T2SSecondaryText
            )
        }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Output format", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MP3", "WAV").forEach { format ->
                    FilterChip(selected = outputFormat == format, onClick = { outputFormat = format }, label = { Text(format) })
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = autoPlayAfterGeneration, onCheckedChange = { autoPlayAfterGeneration = it })
                Text("Auto-play after generation", color = T2SText, fontSize = 13.sp)
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
                    val output = File(audioDirectory, "speech_" + System.currentTimeMillis() + "." + outputFormat.lowercase())
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
                        val chosenMyVoice = selectedMyVoice
                        val voiceId = chosenMyVoice?.id ?: chosenOnlineVoice?.id
                        if (onlineLanguage != null && voiceId != null && textToSpeak.length <= 500) {
                            val onlineResult = runCatching {
                                withContext(Dispatchers.IO) {
                                    TtsAiClient.generate(
                                        textToSpeak, onlineLanguage, voiceId, speechRate, output,
                                        apiKey = if (chosenMyVoice != null) ttsApiKey else "",
                                        model = chosenMyVoice?.model,
                                        format = outputFormat.lowercase()
                                    )
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
                            if (autoPlayAfterGeneration) playGeneratedFile(output)
                        } else {
                            output.delete()
                            val fallbackOutput = if (outputFormat == "MP3") File(output.parentFile, output.nameWithoutExtension + ".wav") else output
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
                                            generatedFile = fallbackOutput
                                            onAudioGenerated(
                                                AudioRecord(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    filePath = fallbackOutput.absolutePath,
                                                    textPreview = textToSpeak.take(180),
                                                    language = selectedLanguage,
                                                    createdAt = System.currentTimeMillis()
                                                )
                                            )
                                            statusMessage = if (outputFormat == "MP3") "MP3 generation was unavailable; Android fallback saved as WAV." else "Android speech audio is ready and saved in T2S Studio."
                                            if (autoPlayAfterGeneration) playGeneratedFile(fallbackOutput)
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
                                    val result = engine.synthesizeToFile(textToSpeak, Bundle(), fallbackOutput, "t2s-" + fallbackOutput.name)
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
                                playGeneratedFile(file)
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

@Composable
private fun VoicePlaybackWaveform() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "voice-wave")
    val heights = (0..8).map { index ->
        transition.animateFloat(
            initialValue = 4f,
            targetValue = (9 + (index * 7 % 15)).toFloat(),
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 260 + index * 55,
                    delayMillis = index * 35
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "voice-wave-$index"
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.height(22.dp)
    ) {
        heights.forEach { height ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(height.value.dp)
                    .background(Color(0xFFF47721), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            )
        }
    }
}

private fun installedVoiceDisplayName(voice: Voice): String {
    val language = voice.locale.getDisplayLanguage(Locale.getDefault())
    val region = voice.locale.getDisplayCountry(Locale.getDefault())
    val gender = voiceGender(voice.name)
    val quality = when {
        voice.quality >= Voice.QUALITY_VERY_HIGH -> "Premium"
        voice.quality >= Voice.QUALITY_HIGH -> "High quality"
        else -> "Standard"
    }
    return listOf(language, region.takeIf { it.isNotBlank() }, gender, quality)
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}
