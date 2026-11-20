package com.t2sstudio.app

import android.content.ContentValues
import android.content.Intent
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

private fun audioMimeType(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    else -> "audio/wav"
}

private data class PurohitPreset(
    val name: String,
    val description: String,
    val speedMultiplier: Float,
    val pitchMultiplier: Float
)

private val purohitPresets = listOf(
    PurohitPreset("Purohit 1", "Deep, steady traditional recitation", 0.84f, 0.86f),
    PurohitPreset("Purohit 2", "Slow, devotional verse delivery", 0.72f, 1.08f),
    PurohitPreset("Purohit 3", "Clear, formal temple-style delivery", 0.96f, 0.98f)
)

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
fun SpeechStudioScreen(
    projects: List<T2SProject>,
    selectedProjectId: String?,
    onSelectedProjectIdChange: (String?) -> Unit,
    onAudioGenerated: (AudioRecord) -> Unit,
    initialScriptText: String?,
    onInitialScriptTextConsumed: () -> Unit,
    onSaveDraft: (String, String, String, String) -> T2SScriptDraft
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedGender by remember { mutableStateOf("All") }
    val voiceGenderOverrides = remember { mutableStateMapOf<String, String>().apply {
        context.getSharedPreferences("t2s_voice_gender_overrides", android.content.Context.MODE_PRIVATE)
            .getStringSet("overrides", emptySet()).orEmpty().forEach { entry ->
                val split = entry.lastIndexOf("=")
                if (split > 0) put(entry.substring(0, split), entry.substring(split + 1))
            }
    } }
    var genderMenuVoiceKey by remember { mutableStateOf<String?>(null) }
    var speechRate by remember { mutableFloatStateOf(1f) }
    var speechPitch by remember { mutableFloatStateOf(1f) }
    var engineReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf("Enter text to create your first voice recording.") }
    LaunchedEffect(initialScriptText) {
        if (initialScriptText != null) {
            inputText = initialScriptText
            statusMessage = "Saved script loaded. Edit it and generate new speech when ready."
            onInitialScriptTextConsumed()
        }
    }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var mediaPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPreviewing by remember { mutableStateOf(false) }
    var activePreviewKey by remember { mutableStateOf<String?>(null) }
    var outputFormat by remember { mutableStateOf("WAV") }
    var autoPlayAfterGeneration by remember { mutableStateOf(false) }
    var voiceSearch by remember { mutableStateOf("") }
    var projectMenuExpanded by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var selectedPurohitPreset by remember { mutableStateOf<PurohitPreset?>(null) }
    var isPurohitMode by remember { mutableStateOf(false) }
    var selectedPurohitVoice by remember { mutableStateOf<Voice?>(null) }
    val availableVoices = remember { mutableStateListOf<Voice>() }
    val onlineVoices = remember { mutableStateListOf<OnlineTtsVoice>() }
    var selectedOnlineVoice by remember { mutableStateOf<OnlineTtsVoice?>(null) }
    var onlineCatalogueStatus by remember { mutableStateOf("Checking online voices…") }
    val myVoices = remember { mutableStateListOf<SavedUserVoice>() }
    var selectedSavedVoice by remember { mutableStateOf<SavedUserVoice?>(null) }
    val convertedTeluguPacks = remember { mutableStateListOf<TeluguVoicePacks.InstalledConvertedVoice>().apply { addAll(TeluguVoicePacks.installedConverted(context)) } }
    var selectedTeluguPackId by remember { mutableStateOf(convertedTeluguPacks.firstOrNull()?.id) }
    var importingTeluguPacks by remember { mutableStateOf(false) }
    val importTeluguPackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importingTeluguPacks = true
            coroutineScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { TeluguVoicePacks.importConvertedArchive(context, uri) } }
                result.onSuccess {
                    convertedTeluguPacks.clear()
                    convertedTeluguPacks.addAll(TeluguVoicePacks.installedConverted(context))
                    selectedTeluguPackId = convertedTeluguPacks.firstOrNull()?.id
                    statusMessage = "Imported " + convertedTeluguPacks.size + " converted Telugu voice pack(s)."
                }.onFailure { statusMessage = "Voice-pack import failed: " + (it.message ?: "invalid archive") }
                importingTeluguPacks = false
            }
        }
    }

    val languages = listOf(
        SpeechLanguage("English", Locale.US),
        SpeechLanguage("Telugu", Locale("te", "IN")),
        SpeechLanguage("Hindi", Locale("hi", "IN"))
    )

    fun genderForVoice(voice: Voice): String? = when (voiceGenderOverrides[installedVoiceKey(voice)]) {
        "Male" -> "Male"
        "Female" -> "Female"
        "Unknown" -> null
        else -> voiceGender(voice.name)
    }

    fun saveVoiceGender(voice: Voice, gender: String) {
        voiceGenderOverrides[installedVoiceKey(voice)] = gender
        context.getSharedPreferences("t2s_voice_gender_overrides", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("overrides", voiceGenderOverrides.map { (key, value) -> key + "=" + value }.toSet()).apply()
        statusMessage = voice.name + " marked as " + gender + "."
    }

    fun refreshVoices(engine: TextToSpeech, locale: Locale) {
        runCatching {
            engine.setLanguage(locale)
            val matching = engine.voices.orEmpty()
                .filter { it.locale.language == locale.language }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            availableVoices.clear()
            availableVoices.addAll(matching)
            selectedVoice = matching.firstOrNull { selectedGender == "All" || genderForVoice(it) == selectedGender }
        }.onFailure {
            availableVoices.clear()
            selectedVoice = null
        }
    }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { MyVoicesClient.list(context) } }
            .onSuccess { saved -> myVoices.clear(); myVoices.addAll(saved) }
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

    fun previewOnlineVoice(voiceId: String, previewKey: String = voiceId, localSamplePath: String? = null, languageOverride: String? = null) {
        if (isPreviewing && activePreviewKey == previewKey) { stopVoicePreview(); statusMessage = "Preview stopped."; return }
        stopVoicePreview()
        val localSample = localSamplePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 1024L }
        val languageCode = languageOverride ?: when (selectedLanguage) { "English" -> "en"; "Hindi" -> "hi"; "Telugu" -> "te"; else -> "en" }
        isPreviewing = true
        activePreviewKey = previewKey
        statusMessage = if (localSample != null) "Preparing your saved recording…" else "Generating preview…"
        coroutineScope.launch {
            val previewFile = File(context.cacheDir, "voice_preview_${System.currentTimeMillis()}.${localSample?.extension ?: "mp3"}")
            val sample = when (languageCode) {
                "hi" -> "नमस्ते। यह आवाज़ का नमूना है।"
                "te" -> "నమస్కారం. ఇది మీ వాయిస్ ప్రివ్యూ నమూనా."
                else -> "Hello. This is a sample of the selected voice."
            }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when {
                        localSample != null -> localSample.copyTo(previewFile, overwrite = true)
                        else -> TtsAiClient.generate(sample, voiceId, 1f, previewFile, format = "mp3", apiKey = IndiaApiKeyStore.get(context))
                    }
                }
            }
            if (result.isFailure) {
                previewFile.delete(); isPreviewing = false; activePreviewKey = null
                statusMessage = "Preview failed: ${result.exceptionOrNull()?.message ?: "voice service unavailable"}"
                return@launch
            }
            if (activePreviewKey != previewKey) { previewFile.delete(); return@launch }
            runCatching {
                val player = ExoPlayer.Builder(context).build()
                mediaPlayer = player
                player.setAudioAttributes(
                    Media3AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true
                )
                player.volume = 1f
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                if (activePreviewKey == previewKey) {
                                    player.play()
                                    isPlaying = true
                                    statusMessage = "Playing preview: $previewKey"
                                }
                            }
                            Player.STATE_ENDED -> {
                                player.release()
                                if (mediaPlayer === player) mediaPlayer = null
                                previewFile.delete()
                                isPlaying = false; isPreviewing = false; activePreviewKey = null
                                statusMessage = "Preview finished."
                            }
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        player.release()
                        if (mediaPlayer === player) mediaPlayer = null
                        previewFile.delete()
                        isPlaying = false; isPreviewing = false; activePreviewKey = null
                        statusMessage = "Preview playback failed: ${error.errorCodeName}"
                    }
                })
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(previewFile)))
                player.prepare()
            }.onFailure {
                previewFile.delete(); mediaPlayer?.release(); mediaPlayer = null
                isPlaying = false; isPreviewing = false; activePreviewKey = null
                statusMessage = "Preview playback error: ${it.message ?: "unknown error"}"
            }
        }
    }

    fun previewPurohitPreset(preset: PurohitPreset) {
        val engine = ttsEngine
        // Purohit presets are delivery styles, not gender filters. Use the selected
        // Telugu system voice (including female voices), or the first installed Telugu voice.
        val purohitTeluguVoice = selectedPurohitVoice?.takeIf { it.locale.language == "te" }
            ?: engine?.voices.orEmpty().firstOrNull { it.locale.language == "te" }
        if (engine == null || !engineReady) {
            statusMessage = "Android speech engine is still starting. Try the preview again."
            return
        }
        if (purohitTeluguVoice == null) {
            statusMessage = "No Telugu system voice is installed. Install Telugu speech data in Android speech settings, then retry."
            return
        }
        stopVoicePreview()
        selectedPurohitVoice = purohitTeluguVoice
        val languageResult = engine.setLanguage(Locale("te", "IN"))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            statusMessage = "Telugu voice data is missing. Install Telugu speech data in Android settings."
            return
        }
        engine.voice = purohitTeluguVoice
        engine.setSpeechRate((speechRate * preset.speedMultiplier).coerceIn(0.5f, 2f))
        engine.setPitch((speechPitch * preset.pitchMultiplier).coerceIn(0.5f, 2f))
        val key = "purohit:${preset.name}"
        activePreviewKey = key
        isPreviewing = true
        isPlaying = false
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private val main = Handler(Looper.getMainLooper())
            override fun onStart(utteranceId: String?) {
                main.post { isPlaying = true; statusMessage = "Playing ${preset.name} style preview using ${purohitTeluguVoice.name}." }
            }
            override fun onDone(utteranceId: String?) {
                main.post {
                    isPlaying = false
                    isPreviewing = false
                    activePreviewKey = null
                    statusMessage = "${preset.name} style preview finished. This uses the installed Android voice, not a dedicated priest-recitation model."
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { isPlaying = false; isPreviewing = false; activePreviewKey = null; statusMessage = "Purohit-style preview failed. Check the installed Telugu voice." }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { isPlaying = false; isPreviewing = false; activePreviewKey = null; statusMessage = "Purohit-style preview failed (code $errorCode)." }
            }
        })
        val result = engine.speak("నమస్కారం. ఇది పూజారి శైలిలో తెలుగు స్వర పరీక్ష.", TextToSpeech.QUEUE_FLUSH, Bundle(), key)
        if (result == TextToSpeech.ERROR) {
            isPreviewing = false
            isPlaying = false
            activePreviewKey = null
            statusMessage = "Android could not start the Telugu preview."
        } else {
            statusMessage = "Preparing ${preset.name} style preview…"
        }
    }

    fun playGeneratedFile(file: File) {
        stopVoicePreview()
        runCatching {
            mediaPlayer?.release()
            val player = ExoPlayer.Builder(context).build()
            mediaPlayer = player
            player.setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            player.volume = 1f
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            player.play()
                            isPlaying = true
                            statusMessage = "Playing generated audio."
                        }
                        Player.STATE_ENDED -> {
                            player.release(); if (mediaPlayer === player) mediaPlayer = null
                            isPlaying = false; statusMessage = "Playback finished."
                        }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    player.release(); if (mediaPlayer === player) mediaPlayer = null
                    isPlaying = false
                    statusMessage = "Audio playback failed: ${error.errorCodeName}"
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        }.onFailure { statusMessage = "Unable to play generated audio: ${it.message ?: "unknown error"}" }
    }

    fun shareAudio(file: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = audioMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share speech audio"))
        }.onFailure {
            statusMessage = "Could not open the share menu. Please try again."
        }
    }

    fun saveToDownloads(file: File) {
        runCatching { AudioFileTransfer.saveToDownloads(context, file) }
            .onSuccess { statusMessage = it }
            .onFailure { statusMessage = "Unable to save audio: ${it.localizedMessage ?: "storage error"}" }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Text to Speech",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.35.sp,
            color = T2SText
        )

        if (projects.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = T2SBackground)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Save to project", color = T2SText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(selectedProject?.name ?: "Unassigned", color = T2SSecondaryText, fontSize = 12.sp)
                    }
                    Box {
                        OutlinedButton(onClick = { projectMenuExpanded = true }) {
                            Text("Change")
                        }
                        DropdownMenu(
                            expanded = projectMenuExpanded,
                            onDismissRequest = { projectMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unassigned") },
                                onClick = {
                                    onSelectedProjectIdChange(null)
                                    projectMenuExpanded = false
                                }
                            )
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        onSelectedProjectIdChange(project.id)
                                        projectMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
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
                            "Start typing or paste your text",
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
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${inputText.length} characters", fontSize = 11.sp, color = T2SSecondaryText)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val draft = onSaveDraft(inputText, selectedLanguage, selectedProject?.id.orEmpty(), selectedProject?.name ?: "Unassigned")
                                statusMessage = "Draft saved: ${draft.title}"
                            }.onFailure { statusMessage = it.localizedMessage ?: "Could not save draft." }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating
                    ) { Text("Save draft") }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val draft = onSaveDraft(inputText, selectedLanguage, selectedProject?.id.orEmpty(), selectedProject?.name ?: "Unassigned")
                                statusMessage = "Draft saved: ${draft.title}"
                            }.onFailure { statusMessage = it.localizedMessage ?: "Could not save draft." }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating
                    ) { Text("Save draft") }
                }
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
            Column(modifier = Modifier.weight(0.82f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Language", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                var languageMenuExpanded by remember { mutableStateOf(false) }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val languageDropdownWidth = maxWidth
                    OutlinedButton(
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (languageMenuExpanded) 1.5.dp else 1.dp,
                            if (languageMenuExpanded) T2SOrange else Color(0xFFD8D2CC)
                        ),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = T2SText
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            selectedLanguage,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Canvas(
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = if (languageMenuExpanded) 180f else 0f }
                        ) {
                            val stroke = 2.2.dp.toPx()
                            drawLine(
                                color = T2SOrange,
                                start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.38f),
                                end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.64f),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            drawLine(
                                color = T2SOrange,
                                start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.64f),
                                end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.38f),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                        modifier = Modifier
                            .width(languageDropdownWidth)
                            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE7DED6), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
                                    if (language.label == "Telugu") outputFormat = "WAV"
                                    if (language.label != "Telugu") { selectedPurohitPreset = null; isPurohitMode = false }
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
            Column(modifier = Modifier.weight(1.18f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Gender", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All", "Male", "Female").forEach { gender ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = selectedGender == gender,
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF47721),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                selectedTrailingIconColor = Color.White,
                                containerColor = Color(0xFFFFF7F0),
                                labelColor = Color(0xFF6B5546)
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedGender == gender,
                                borderColor = Color(0xFFE6CDB8),
                                selectedBorderColor = Color(0xFFF47721)
                            ),
                            onClick = {
                                selectedGender = gender
                                selectedVoice = availableVoices.firstOrNull { voice ->
                                    gender == "All" || genderForVoice(voice) == gender
                                }
                                selectedOnlineVoice = onlineVoices.firstOrNull { voice ->
                                    gender == "All" || voice.gender.equals(gender, ignoreCase = true)
                                }
                            },
                            label = { Text(gender, fontSize = 10.sp, maxLines = 1, softWrap = false) }
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
                Text(
                    "${availableVoices.size + onlineVoices.size + myVoices.size} voices",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = T2SOrange
                )
            }
            val allInstalledForLanguage = availableVoices.toList()
            val genderMatches = allInstalledForLanguage.filter { voice -> selectedGender == "All" || genderForVoice(voice) == selectedGender }
            val installedPool = genderMatches
            val onlineGenderMatches = onlineVoices.filter { voice -> selectedGender == "All" || voice.gender.equals(selectedGender, true) }
            val onlinePool = onlineGenderMatches
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
                voice.name.lowercase(Locale.ROOT).contains(searchTerm)
            }
            val showingSuggestions = searchTerm.isNotBlank() && searchedInstalled.isEmpty() && searchedOnline.isEmpty() && filteredPersonal.isEmpty()

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = voiceSearch,
                    onValueChange = {
                        voiceSearch = it
                                },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    placeholder = { Text("Search by voice name") },
                    label = { Text("Voice Search") },
                    leadingIcon = { Text("⌕", fontSize = 25.sp, color = T2SOrange, fontWeight = FontWeight.Bold) },
                    trailingIcon = {
                        if (voiceSearch.isNotBlank()) {
                            TextButton(onClick = { voiceSearch = ""; }) { Text("✕ Clear") }
                        }
                    }
                )
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7D7C2))
                ) {
                  Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        Text(installedVoiceDisplayName(voice, genderForVoice(voice), filteredInstalled.filter { genderForVoice(it) == genderForVoice(voice) }.indexOf(voice) + 1), color = Color(0xFF101B35), fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(voice.name + " • " + voice.locale.toLanguageTag(), color = Color(0xFF58677F), fontSize = 11.sp, maxLines = 1)
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            TextButton(onClick = { genderMenuVoiceKey = installedVoiceKey(voice) }) {
                                                Text("Gender: " + (genderForVoice(voice) ?: "Not set") + " ▾", fontSize = 11.sp)
                                            }
                                            DropdownMenu(expanded = genderMenuVoiceKey == installedVoiceKey(voice), onDismissRequest = { genderMenuVoiceKey = null }) {
                                                listOf("Male", "Female", "Unknown").forEach { genderOption ->
                                                    DropdownMenuItem(text = { Text(genderOption) }, onClick = {
                                                        if (genderOption == "Unknown") {
                                                            voiceGenderOverrides[installedVoiceKey(voice)] = "Unknown"
                                                            context.getSharedPreferences("t2s_voice_gender_overrides", android.content.Context.MODE_PRIVATE)
                                                                .edit().putStringSet("overrides", voiceGenderOverrides.map { (key, value) -> key + "=" + value }.toSet()).apply()
                                                            statusMessage = "Gender set to Unknown for " + voice.name + "."
                                                        } else saveVoiceGender(voice, genderOption)
                                                        genderMenuVoiceKey = null
                                                    })
                                                }
                                            }
                                        }
                                        if (isPlaying && activePreviewKey == "installed:${voice.name}") {
                                            VoicePlaybackWaveform()
                                        }
                                    }
                                },
                                onClick = {
                                    isPurohitMode = false
                                    selectedVoice = voice
                                    selectedOnlineVoice = null
                                    selectedSavedVoice = null
                                    voiceSearch = voice.name
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
                                    isPurohitMode = false
                                    selectedOnlineVoice = voice
                                    selectedVoice = null
                                    selectedSavedVoice = null
                                    voiceSearch = voice.name
                                    statusMessage = voice.name + " selected. Voice licence: " + voice.licence + ". " + voice.attribution
                                }) {
                                    Text(voice.name, color = T2SText, fontWeight = FontWeight.SemiBold)
                                    Text("Free online • " + voice.language + " • " + voice.licence, color = Color(0xFF58677F), fontSize = 10.sp, maxLines = 2)
                                }
                                TextButton(enabled = true, onClick = { previewOnlineVoice(voice.id, previewKey = "online:${voice.id}") }) {
                                    Text(if (activePreviewKey == "online:${voice.id}" && isPreviewing) "■ Stop" else "▶ Preview", color = Color(0xFFF47721), fontWeight = FontWeight.Bold)
                                }
                              }
                            }
                        }
                    }
                    if (filteredPersonal.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("MY SAVED VOICE SAMPLES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            onClick = {},
                            enabled = false
                        )
                        filteredPersonal.forEach { voice ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).background(if (selectedSavedVoice?.id == voice.id) T2SCream else Color.Transparent, RoundedCornerShape(10.dp)).clickable {
                                isPurohitMode = false
                                selectedSavedVoice = voice
                                selectedVoice = null
                                selectedOnlineVoice = null
                                voiceSearch = voice.name
                                statusMessage = "${voice.name} selected. This is a saved recording sample, not a trained voice model; text-to-speech generation in this exact voice is not available yet."
                            }.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(voice.name + if (selectedSavedVoice?.id == voice.id) " ✓" else "", color = T2SText, fontWeight = FontWeight.SemiBold)
                                    Text("Saved sample • " + voice.language.uppercase() + " • select to use", color = T2SSecondaryText, fontSize = 11.sp)
                                }
                                TextButton(enabled = true, onClick = { previewOnlineVoice(voice.id, previewKey = "personal:${voice.id}", localSamplePath = voice.localSamplePath, languageOverride = voice.language) }) {
                                    Text(if (activePreviewKey == "personal:${voice.id}" && isPreviewing) "■ Stop" else "▶ Preview", color = T2SOrange)
                                }
                            }
                        }
                    }
                    if (filteredInstalled.isEmpty() && filteredOnline.isEmpty() && filteredPersonal.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (onlineCatalogueStatus.startsWith("Loading")) "Loading voices…" else if (selectedGender != "All") "No $selectedGender voices match this language. Choose All or another gender." else "No voices are installed or available for this language yet. Try another language.", color = T2SSecondaryText) },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }
        }
        }
        }

        if (selectedLanguage == "Telugu") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = T2SCream),
                border = androidx.compose.foundation.BorderStroke(1.dp, T2SOrange.copy(alpha = 0.35f))
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Purohit-style voice presets", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = T2SText)
                    Text(
                        "These presets adjust speed and pitch on an installed Telugu Android voice. Android may not identify the voice gender. They are previews, not a dedicated or verified priest-recitation model.",
                        fontSize = 11.sp,
                        color = T2SSecondaryText
                    )
                    purohitPresets.forEach { preset ->
                        val selected = isPurohitMode && selectedPurohitPreset?.name == preset.name
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedPurohitPreset = preset
                                isPurohitMode = true
                                val engine = ttsEngine
                                selectedPurohitVoice = selectedPurohitVoice?.takeIf { it.locale.language == "te" }
                                    ?: availableVoices.firstOrNull { it.locale.language == "te" }
                                    ?: engine?.voices.orEmpty().firstOrNull { it.locale.language == "te" }
                                engine?.let {
                                    it.setLanguage(Locale("te", "IN"))
                                    selectedPurohitVoice?.let { voice -> it.voice = voice }
                                }
                                statusMessage = if (selectedPurohitVoice == null) {
                                    "${preset.name} selected, but no Telugu system voice is installed. Install Telugu speech data in Android settings."
                                } else {
                                    "${preset.name} selected with Telugu voice ${selectedPurohitVoice?.name}. Any installed voice can be used; preview it before generating audio."
                                }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) Color(0xFFFFE3CC) else Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) T2SOrange else Color(0xFFE5D6C8)
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(preset.name, color = T2SText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(preset.description, color = T2SSecondaryText, fontSize = 11.sp)
                                }
                                if (selected) Text("SELECTED", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val preset = selectedPurohitPreset.takeIf { isPurohitMode }
                                if (preset == null) statusMessage = "Select Purohit 1, 2 or 3 first."
                                else previewPurohitPreset(preset)
                            },
                            enabled = isPurohitMode && selectedPurohitPreset != null && selectedPurohitVoice?.locale?.language == "te" && engineReady,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = Color.White)
                        ) {
                            Text(if (isPreviewing && activePreviewKey?.startsWith("purohit:") == true) "Preview playing…" else "▶ Preview")
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                stopVoicePreview()
                                isPurohitMode = false
                                statusMessage = "Purohit mode cleared. Your selected preset and voice-library choice are kept."
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Output format:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                listOf("WAV", "MP3").forEach { format ->
                    FilterChip(
                        selected = outputFormat == format,
                        enabled = selectedLanguage != "Telugu" || format == "WAV" || (!isPurohitMode && selectedOnlineVoice != null),
                        onClick = { outputFormat = format },
                        label = { Text(format, fontSize = 12.sp) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    modifier = Modifier.offset(x = (-12).dp),
                    checked = autoPlayAfterGeneration,
                    onCheckedChange = { autoPlayAfterGeneration = it }
                )
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
                } else if (selectedSavedVoice != null) {
                    statusMessage = "${selectedSavedVoice?.name} is a saved audio sample, not a trained voice-cloning model. T2S cannot generate new text in that voice yet. Choose an installed or online TTS voice, or preview the saved recording."
                } else if (selectedLanguage == "Telugu" && !isPurohitMode && outputFormat == "WAV" && convertedTeluguPacks.any { it.id == selectedTeluguPackId }) {
                    val pack = convertedTeluguPacks.firstOrNull { it.id == selectedTeluguPackId }
                    if (pack == null) {
                        statusMessage = "Import and select a converted Telugu voice pack first."
                    } else {
                        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "T2SStudio").apply { mkdirs() }
                        val output = File(directory, "telugu_speech_" + System.currentTimeMillis() + ".wav")
                        isGenerating = true
                        generatedFile = null
                        stopVoicePreview()
                        statusMessage = "Generating offline Telugu speech with " + pack.displayName + "…"
                        coroutineScope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { TeluguOfflineTtsEngine(context, pack.model, pack.tokens).use { it.synthesizeToWav(textToSpeak, output, speechRate) } } }
                            result.onSuccess {
                                isGenerating = false
                                generatedFile = output
                                onAudioGenerated(AudioRecord(id = java.util.UUID.randomUUID().toString(), filePath = output.absolutePath, textPreview = textToSpeak.take(180), language = "Telugu", createdAt = System.currentTimeMillis(), projectId = selectedProject?.id.orEmpty(), projectName = selectedProject?.name ?: "Unassigned", scriptContent = textToSpeak))
                                statusMessage = "Offline Telugu WAV is ready."
                                if (autoPlayAfterGeneration) playGeneratedFile(output)
                            }.onFailure { output.delete(); isGenerating = false; statusMessage = "Offline Telugu generation failed: " + (it.message ?: "engine error") }
                        }
                    }
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
                        val onlineLanguage = if (isPurohitMode) null else when (selectedLanguage) {
                            "English" -> "en"
                            "Hindi" -> "hi"
                            "Telugu" -> "te"
                            else -> null
                        }
                        var onlineSuccess = false
                        val chosenOnlineVoice = selectedOnlineVoice
                        val voiceId = chosenOnlineVoice?.id
                        if (onlineLanguage != null && voiceId != null && textToSpeak.length <= 3000) {
                            val onlineResult = runCatching {
                                withContext(Dispatchers.IO) {
                                    TtsAiClient.generate(
                                        textToSpeak, voiceId, speechRate, output,
                                        format = outputFormat.lowercase(),
                                        apiKey = IndiaApiKeyStore.get(context)
                                    )
                                }
                            }
                            onlineSuccess = onlineResult.isSuccess && output.isFile && output.length() > 0L
                            if (!onlineSuccess) {
                                statusMessage = "Online unavailable (" +
                                    (onlineResult.exceptionOrNull()?.message ?: "service error") +
                                    "). Switching to Android voice…"
                            }
                        } else if (onlineLanguage != null && textToSpeak.length > 3000) {
                            statusMessage = "Text exceeds the free online 3,000-character limit. Switching to Android voice…"
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
                                    createdAt = System.currentTimeMillis(),
                                    projectId = selectedProject?.id.orEmpty(),
                                    projectName = selectedProject?.name ?: "Unassigned",
                                    scriptContent = textToSpeak
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
                                val preset = selectedPurohitPreset.takeIf { isPurohitMode }
                                val presetVoice = if (preset != null) {
                                    selectedPurohitVoice?.takeIf { it.locale.language == "te" }
                                        ?: engine.voices.orEmpty().firstOrNull { it.locale.language == "te" }
                                } else null
                                if (preset != null && presetVoice == null) {
                                    isGenerating = false
                                    statusMessage = "Cannot generate ${preset.name}: no Telugu system voice is installed. Install Telugu speech data in Android speech settings, or import a converted Telugu offline voice pack."
                                } else {
                                val languageResult = engine.setLanguage(language.locale)
                                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                                ) {
                                    isGenerating = false
                                    statusMessage = language.label + " voice data is not installed or supported on this device."
                                } else {
                                    (presetVoice ?: selectedVoice)?.let { engine.voice = it }
                                    engine.setSpeechRate((speechRate * (preset?.speedMultiplier ?: 1f)).coerceIn(0.5f, 2f))
                                    engine.setPitch((speechPitch * (preset?.pitchMultiplier ?: 1f)).coerceIn(0.5f, 2f))
                                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                        override fun onStart(utteranceId: String?) { }
                                        override fun onDone(utteranceId: String?) {
                                            if (!fallbackOutput.isFile || fallbackOutput.length() <= 0L) {
                                                fallbackOutput.delete()
                                                isGenerating = false
                                                statusMessage = "Android speech engine finished without producing a valid audio file."
                                                return
                                            }
                                            isGenerating = false
                                            generatedFile = fallbackOutput
                                            onAudioGenerated(
                                                AudioRecord(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    filePath = fallbackOutput.absolutePath,
                                                    textPreview = textToSpeak.take(180),
                                                    language = selectedLanguage,
                                                    createdAt = System.currentTimeMillis(),
                                                    projectId = selectedProject?.id.orEmpty(),
                                                    projectName = selectedProject?.name ?: "Unassigned",
                                                    scriptContent = textToSpeak
                                                )
                                            )
                                            statusMessage = if (outputFormat == "MP3") "MP3 generation was unavailable; Android fallback saved as WAV." else "Android speech audio is ready and saved in T2S Studio."
                                            if (autoPlayAfterGeneration) playGeneratedFile(fallbackOutput)
                                        }
                                        @Deprecated("Deprecated in Java")
                                        override fun onError(utteranceId: String?) {
                                            fallbackOutput.delete()
                                            isGenerating = false
                                            statusMessage = "Could not generate audio with either speech provider."
                                        }
                                        override fun onError(utteranceId: String?, errorCode: Int) {
                                            fallbackOutput.delete()
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
                }
            },
            enabled = !isGenerating && inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 5.dp,
                pressedElevation = 1.dp,
                disabledElevation = 0.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = T2SOrange,
                contentColor = T2SBackground,
                disabledContainerColor = T2SOrange.copy(alpha = 0.55f),
                disabledContentColor = T2SBackground.copy(alpha = 0.8f)
            )
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = T2SBackground,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("✨ Creating your audio…", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Text("🎙️  Generate Speech  ✨", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
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
                                player.play()
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
            "Use free online Piper voices when available, or installed Android voices and imported Telugu offline packs. Online requests need internet; audio stays in app storage.",
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

private fun installedVoiceKey(voice: Voice): String = voice.locale.toLanguageTag() + "|" + voice.name

private fun installedVoiceDisplayName(voice: Voice, gender: String?, index: Int): String {
    val label = when (gender) {
        "Male" -> "Male Voice " + index
        "Female" -> "Female Voice " + index
        else -> "Unclassified Voice " + index
    }
    return label + " (" + voice.locale.toLanguageTag() + ")"
}
