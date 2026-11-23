package com.t2sstudio.app

import android.content.ContentValues
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.Alignment
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class SpeechLanguage(val label: String, val locale: Locale)

private fun audioMimeType(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    else -> "audio/wav"
}
private suspend fun synthesizeInstalledVoiceToFile(
    engine: TextToSpeech, text: String, voice: Voice,
    speechRate: Float, speechPitch: Float, output: File
) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
    val utteranceId = "guest-${java.util.UUID.randomUUID()}"
    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(id: String) = Unit
        override fun onDone(id: String) {
            if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
        }
        @Deprecated("Deprecated in Java")
        override fun onError(id: String) {
            if (id == utteranceId && continuation.isActive) continuation.resumeWithException(IllegalStateException("Android speech engine could not create the audio file."))
        }
        override fun onError(id: String, errorCode: Int) {
            if (id == utteranceId && continuation.isActive) continuation.resumeWithException(IllegalStateException("Android speech engine failed (code $errorCode)."))
        }
    })
    runCatching {
        engine.setVoice(voice)
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2f))
        engine.setPitch(speechPitch.coerceIn(0.5f, 2f))
        engine.synthesizeToFile(text, Bundle(), output, utteranceId)
    }.onFailure {
        if (continuation.isActive) continuation.resumeWithException(it)
    }.onSuccess { result ->
        if (result == TextToSpeech.ERROR && continuation.isActive) continuation.resumeWithException(IllegalStateException("Android speech engine rejected this voice or language."))
    }
    continuation.invokeOnCancellation { runCatching { engine.stop() } }
}


private data class PurohitPreset(
    val name: String,
    val description: String,
    val speedMultiplier: Float,
    val pitchMultiplier: Float
)

private val purohitPresets = listOf(
    PurohitPreset("Purohit 1", "Deep, steady traditional recitation", 0.84f, 0.86f),
    PurohitPreset("Purohit 2", "Slow, devotional verse delivery", 0.72f, 0.90f),
    PurohitPreset("Purohit 3", "Clear, formal temple-style delivery", 0.96f, 0.88f)
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
    onSaveDraft: (String, String, String, String, (Result<T2SScriptDraft>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isRegisteredUser by remember { mutableStateOf(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) }
    val inputCharacterLimit = if (isRegisteredUser) 3000 else 2000
    DisposableEffect(Unit) {
        val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { authState ->
            isRegisteredUser = authState.currentUser != null
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }
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
    var dailyGenerationLimitReached by remember { mutableStateOf(false) }
    var isSavingDraft by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf("Enter text to create your first voice recording.") }
    LaunchedEffect(initialScriptText) {
        if (initialScriptText != null) {
            inputText = initialScriptText.take(inputCharacterLimit)
            statusMessage = if (initialScriptText.length > inputCharacterLimit) {
                "This input is limited to $inputCharacterLimit characters. The loaded script was shortened to fit."
            } else {
                "Saved script loaded. Edit it and generate new speech when ready."
            }
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
    var voiceLibraryTab by remember { mutableStateOf("Online") }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var selectedPurohitPreset by remember { mutableStateOf<PurohitPreset?>(null) }
    var isPurohitMode by remember { mutableStateOf(false) }
    var selectedPurohitVoice by remember { mutableStateOf<Voice?>(null) }
    val availableVoices = remember { mutableStateListOf<Voice>() }
    val onlineVoices = remember { mutableStateListOf<OnlineTtsVoice>() }
    var selectedOnlineVoice by remember { mutableStateOf<OnlineTtsVoice?>(null) }
    var onlineCatalogueStatus by remember { mutableStateOf("Checking online voices…") }
    var onlineCatalogueLanguage by remember { mutableStateOf<String?>(null) }
    var onlineCatalogueRefreshKey by remember { mutableStateOf(0) }
    val myVoices = remember { mutableStateListOf<SavedUserVoice>() }
    var selectedSavedVoice by remember { mutableStateOf<SavedUserVoice?>(null) }
    val signedInUidForVoiceSelection = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var selectedSavedVoiceId by remember(signedInUidForVoiceSelection) {
        mutableStateOf(
            signedInUidForVoiceSelection.takeIf { it.isNotBlank() }?.let { uid ->
                context.getSharedPreferences("t2s_voice_selection", android.content.Context.MODE_PRIVATE)
                    .getString("selected_saved_voice_id_$uid", null)
            }
        )
    }
    LaunchedEffect(signedInUidForVoiceSelection, selectedSavedVoiceId) {
        if (signedInUidForVoiceSelection.isNotBlank()) {
            context.getSharedPreferences("t2s_voice_selection", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("selected_saved_voice_id_$signedInUidForVoiceSelection", selectedSavedVoiceId)
                .apply()
        }
    }
    fun restoreSavedVoiceSelection() {
        val restored = selectedSavedVoiceId?.let { savedId -> myVoices.firstOrNull { it.id == savedId } }
        selectedSavedVoice = restored
        if (restored != null) {
            selectedLanguage = when (restored.language.lowercase(Locale.ROOT)) {
                "te", "telugu" -> "Telugu"
                "en", "english" -> "English"
                "hi", "hindi" -> "Hindi"
                else -> selectedLanguage
            }
            voiceLibraryTab = "My Voices"
        } else if (selectedSavedVoiceId != null) {
            selectedSavedVoiceId = null
        }
    }


    val languages = listOf(
        SpeechLanguage("English", Locale.US),
        SpeechLanguage("Telugu", Locale("te", "IN")),
        SpeechLanguage("Hindi", Locale("hi", "IN"))
    )

    // Android Voice metadata does not expose a dependable speaker-gender field.
    // Never infer gender from the engine's voice name: names such as "male", "female",
    // or numbered voice IDs are inconsistent across manufacturers and TTS engines.
    // Only use a user's explicit, persisted audition-based classification.
    fun genderForVoice(voice: Voice): String? = when (voiceGenderOverrides[installedVoiceKey(voice)]) {
        "Male" -> "Male"
        "Female" -> "Female"
        else -> null
    }

    fun saveGenderOverride(key: String, displayName: String, gender: String) {
        voiceGenderOverrides[key] = gender
        context.getSharedPreferences("t2s_voice_gender_overrides", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("overrides", voiceGenderOverrides.map { (entryKey, value) -> entryKey + "=" + value }.toSet()).apply()
        statusMessage = displayName + " marked as " + gender + "."
    }

    fun saveVoiceGender(voice: Voice, gender: String) =
        saveGenderOverride(installedVoiceKey(voice), voice.name, gender)

    fun onlineVoiceGender(voice: OnlineTtsVoice): String? {
        return when (voiceGenderOverrides["online|"+voice.provider+"|"+voice.id]) {
            "Male" -> "Male"
            "Female" -> "Female"
            "Unknown" -> null
            else -> when (voice.gender.trim().lowercase(Locale.ROOT)) {
                "male", "masculine", "m", "man" -> "Male"
                "female", "feminine", "f", "woman" -> "Female"
                else -> voiceGender(voice.name)
            }
        }
    }

    fun refreshVoices(engine: TextToSpeech, locale: Locale) {
        runCatching {
            engine.setLanguage(locale)
            val matching = engine.voices.orEmpty()
                .filter { it.locale.language == locale.language }
                .distinctBy { it.name }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            val previousName = selectedVoice?.name
            availableVoices.clear()
            availableVoices.addAll(matching)
            selectedVoice = matching.firstOrNull { it.name == previousName } ?: matching.firstOrNull()
        }.onFailure {
            availableVoices.clear()
            selectedVoice = null
        }
    }

    // Initialise Android's actual TTS engine so voices installed on the phone
    // are discovered and can be previewed/selected from the Installed tab.
    DisposableEffect(context) {
        var createdEngine: TextToSpeech? = null
        val engine = TextToSpeech(context.applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS) {
                createdEngine?.let { readyEngine ->
                    ttsEngine = readyEngine
                    engineReady = true
                    refreshVoices(readyEngine, languages.first { it.label == selectedLanguage }.locale)
                }
            } else {
                engineReady = false
                statusMessage = "Android text-to-speech engine could not be initialized. Check your phone's Text-to-speech settings."
            }
        }
        createdEngine = engine
        onDispose {
            engineReady = false
            if (ttsEngine === engine) ttsEngine = null
            runCatching { engine.stop(); engine.shutdown() }
        }
    }

    LaunchedEffect(selectedLanguage, engineReady) {
        if (engineReady) {
            ttsEngine?.let { engine ->
                val locale = languages.firstOrNull { it.label == selectedLanguage }?.locale ?: Locale.US
                refreshVoices(engine, locale)
            }
        }
    }

    LaunchedEffect(voiceLibraryTab) {
        if (voiceLibraryTab == "My Voices") {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                myVoices.clear()
                statusMessage = "Sign in to load your saved Firebase voices."
            } else {
                runCatching {
                    kotlinx.coroutines.suspendCancellableCoroutine<List<SavedUserVoice>> { continuation ->
                        com.t2sstudio.app.data.firebase.T2SFirestoreRepository().loadVoiceMetadata(uid) { result ->
                            if (continuation.isActive) result.fold(
                                onSuccess = { continuation.resume(it) },
                                onFailure = { continuation.resumeWithException(it) }
                            )
                        }
                    }
                }.onSuccess { cloudSaved ->
                    // Firebase stores voice metadata only; the original sample audio stays
                    // in app-private storage. Reattach local sample paths by stable voice ID
                    // so My Voices previews play the user's actual recording after cloud load.
                    val localSaved = MyVoicesClient.list(context)
                    val localById = localSaved.associateBy { it.id }
                    val merged = cloudSaved.map { cloud ->
                        localById[cloud.id]?.let { local ->
                            cloud.copy(
                                localSamplePath = local.localSamplePath,
                                name = local.name.ifBlank { cloud.name },
                                language = local.language.ifBlank { cloud.language },
                                gender = local.gender.ifBlank { cloud.gender }
                            )
                        } ?: cloud
                    }.toMutableList()
                    val cloudIds = cloudSaved.mapTo(mutableSetOf()) { it.id }
                    merged.addAll(localSaved.filterNot { it.id in cloudIds })
                    myVoices.clear()
                    myVoices.addAll(merged.distinctBy { it.id })
                    restoreSavedVoiceSelection()
                    if (merged.isEmpty()) statusMessage = "No saved voices found on this device or in Firebase."
                }.onFailure { error ->
                    // Even if Firebase is offline, local voice recordings remain playable.
                    val localSaved = MyVoicesClient.list(context)
                    myVoices.clear()
                    myVoices.addAll(localSaved)
                    restoreSavedVoiceSelection()
                    statusMessage = if (localSaved.isNotEmpty()) {
                        "Showing your locally saved voices. Cloud sync is unavailable."
                    } else {
                    "Could not load My Voices from Firebase: ${error.localizedMessage ?: "unknown error"}"
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedLanguage, voiceLibraryTab, onlineCatalogueRefreshKey) {
        if (voiceLibraryTab != "Online") return@LaunchedEffect

        val languageCode = when (selectedLanguage) {
            "English" -> "en"
            "Hindi" -> "hi"
            "Telugu" -> "te"
            else -> "en"
        }
        val languageChanged = onlineCatalogueLanguage != selectedLanguage
        if (languageChanged) {
            onlineVoices.clear()
            selectedOnlineVoice = null
            onlineCatalogueLanguage = selectedLanguage
        }
        onlineCatalogueStatus = "Loading free online " + selectedLanguage + " voices…"

        suspend fun <T> loadWithRetry(load: suspend () -> T): Result<T> {
            var result = runCatching { load() }
            if (result.isFailure) {
                kotlinx.coroutines.delay(700)
                result = runCatching { load() }
            }
            return result
        }

        val indiaResult = loadWithRetry {
            withContext(Dispatchers.IO) { CentralOnlineTtsClient.listVoices("India API", languageCode) }
        }
        val indiaVoices = indiaResult.getOrNull()
        val vakyamResult = loadWithRetry {
            withContext(Dispatchers.IO) { CentralOnlineTtsClient.listVoices("Vakyam", languageCode) }
        }
        val vakyamVoices = vakyamResult.getOrNull()

        // Replace only provider catalogues that loaded successfully. A temporary
        // failure from one provider must not erase voices already loaded from another.
        if (indiaVoices != null) {
            onlineVoices.removeAll { it.provider != "Vakyam" }
            onlineVoices.addAll(indiaVoices)
        }
        if (vakyamVoices != null) {
            onlineVoices.removeAll { it.provider == "Vakyam" }
            onlineVoices.addAll(vakyamVoices)
        }
        selectedOnlineVoice = selectedOnlineVoice?.let { selected ->
            onlineVoices.firstOrNull { it.provider == selected.provider && it.id == selected.id }
        } ?: onlineVoices.firstOrNull { selectedGender == "All" || onlineVoiceGender(it) == selectedGender }
            ?: onlineVoices.firstOrNull()

        onlineCatalogueStatus = buildString {
            if (onlineVoices.isNotEmpty()) append("${onlineVoices.size} online voices available • India API: ${onlineVoices.count { it.provider != "Vakyam" }} • Vakyam: ${onlineVoices.count { it.provider == "Vakyam" }}")
            else append("No ${selectedLanguage} online voices are available. Check provider setup and internet, then tap Retry online voices.")
            if (indiaResult.isFailure) append(" • India API: ${indiaResult.exceptionOrNull()?.message ?: "unavailable"}")
            if (vakyamResult.isFailure) append(" • Vakyam: ${vakyamResult.exceptionOrNull()?.message ?: "unavailable"}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun stopVoicePreview() {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        isPreviewing = false
        activePreviewKey = null
    }

    fun previewOnlineVoice(voiceId: String, previewKey: String = voiceId, localSamplePath: String? = null, languageOverride: String? = null, provider: String = "India API", sarvamVoiceId: String = "") {
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
                        sarvamVoiceId.startsWith("svc-") ->
                            SarvamVoiceCloneClient.generateCentral(sarvamVoiceId, sample, languageCode, previewFile)
                        localSamplePath != null ->
                            error("This voice's saved recording is missing. Re-add the voice sample to preview it.")
                        else ->
                            error("This saved voice has no playable recording or registered voice ID. Re-add or register it before previewing.")
                    }
                }
            }
            if (result.isFailure) {
                previewFile.delete(); isPreviewing = false; activePreviewKey = null
                val previewError = result.exceptionOrNull()?.message ?: "voice service unavailable"
                if (previewError.contains("Daily limit reached", ignoreCase = true)) {
                    dailyGenerationLimitReached = true
                }
                statusMessage = "Preview failed: $previewError"
                return@launch
            }
            if (activePreviewKey != previewKey) { previewFile.delete(); return@launch }
            runCatching {
                val player = ExoPlayer.Builder(context).build()
                            // Match the clean Add Voice audition path; ExoPlayer silence skipping can
                // introduce a dead-air lead-in with some registered voice samples.
                player.skipSilenceEnabled = false
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

    fun playGeneratedFile(file: File, pitch: Float = 1f) {
        stopVoicePreview()
        runCatching {
            mediaPlayer?.release()
            val player = ExoPlayer.Builder(context).build()
                            player.skipSilenceEnabled = true
            mediaPlayer = player
            player.setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            player.volume = 1f
            player.setPlaybackParameters(
                androidx.media3.common.PlaybackParameters(1f, pitch.coerceIn(0.5f, 2f))
            )
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
            .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Your text", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = T2SText)
                            Box(
                                modifier = Modifier
                                    .background(T2SOrange, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .height(28.dp)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("SCRIPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                            }
                        }
                        Text("Write or paste your script below", fontSize = 12.sp, color = T2SSecondaryText)
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newText ->
                        inputText = newText.take(inputCharacterLimit)
                        generatedFile = null
                        if (newText.length > inputCharacterLimit) {
                            statusMessage = "Input limit reached: $inputCharacterLimit characters."
                        }
                    },
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
                if (dailyGenerationLimitReached) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1E5)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, T2SOrange.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "Daily generation limit reached",
                                color = T2SText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "You've used today's speech allowance. You can still edit your text and manage voices. Generation resets at 5:30 AM IST.",
                                color = T2SSecondaryText,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${inputText.length} / $inputCharacterLimit characters", fontSize = 11.sp, color = if (inputText.length >= inputCharacterLimit) T2SOrange else T2SSecondaryText)
                    OutlinedButton(
                        onClick = {
                            if (isSavingDraft) return@OutlinedButton
                            isSavingDraft = true
                            statusMessage = "Saving draft to your Firebase account…"
                            onSaveDraft(inputText, selectedLanguage, "", "Unassigned") { result ->
                                isSavingDraft = false
                                result.onSuccess { draft ->
                                    statusMessage = "✓ Draft saved to your Firebase account and this device: ${draft.title}."
                                    android.widget.Toast.makeText(context, "Draft saved on this device", android.widget.Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    statusMessage = "Draft was NOT saved to Firebase: ${error.localizedMessage ?: "Unknown Firebase error."} A local recovery copy remains on this device. Sign in and retry."
                                    android.widget.Toast.makeText(context, "Draft save failed", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating && !isSavingDraft
                    ) {
                        if (isSavingDraft) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Saving…")
                        } else Text("Save as draft")
                    }
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
          ) {
            Column(modifier = Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Language", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                var languageMenuExpanded by remember { mutableStateOf(false) }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val languageDropdownWidth = maxWidth
                    OutlinedButton(
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (languageMenuExpanded) 1.5.dp else 1.dp,
                            T2SOrange
                        ),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF47721),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            selectedLanguage,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                        Canvas(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = if (languageMenuExpanded) 180f else 0f }
                        ) {
                            val stroke = 2.2.dp.toPx()
                            drawLine(
                                color = Color.White,
                                start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.38f),
                                end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.64f),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
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
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(9.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (selectedLanguage == language.label) Color(0xFFFFEBDD) else Color(0xFFFFF7F0)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedLanguage == language.label) T2SOrange else Color(0xFFF0D7C2))
                                    ) {
                                        Text(
                                            language.label,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            color = if (selectedLanguage == language.label) Color(0xFFF47721) else Color(0xFF101B35),
                                            fontSize = 14.sp,
                                            fontWeight = if (selectedLanguage == language.label) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
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
            Column(modifier = Modifier.weight(1.22f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Gender", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("All", "Male", "Female").forEach { gender ->
                        FilterChip(
                            modifier = Modifier.weight(1f).height(34.dp),
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
                                selectedVoice = availableVoices.firstOrNull()
                                selectedOnlineVoice = onlineVoices.firstOrNull { voice ->
                                    gender == "All" || onlineVoiceGender(voice) == gender
                                }
                            },
                            label = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        gender,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
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
                val visibleVoiceCount = when (voiceLibraryTab) {
                    "Online" -> onlineVoices.count { selectedGender == "All" || onlineVoiceGender(it) == selectedGender }
                    "Installed" -> availableVoices.size
                    else -> myVoices.size
                }
                Text(
                    "$visibleVoiceCount voices",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = T2SOrange
                )
            }
            val allInstalledForLanguage = availableVoices.toList()
            // Installed Android voices are listed without gender labels or gender filtering.
            // Android TTS does not provide reliable speaker-gender metadata.
            val installedPool = allInstalledForLanguage
            // Gender filters are strict: unknown/unclassified voices must not leak into
            // Male or Female results. Users can correct a provider label from each voice card.
            val onlinePool = onlineVoices.filter { voice ->
                selectedGender == "All" || onlineVoiceGender(voice) == selectedGender
            }
            val searchTerm = voiceSearch.trim().lowercase(Locale.ROOT)
            val searchedInstalled = installedPool.filter { voice ->
                "${voice.name} ${voice.locale.displayName} ${voice.locale.toLanguageTag()}".lowercase(Locale.ROOT).contains(searchTerm)
            }
            val searchedOnline = onlinePool.filter { voice ->
                "${voice.name} ${voice.language}".lowercase(Locale.ROOT).contains(searchTerm)
            }
            val filteredInstalled = if (searchTerm.isBlank()) installedPool else searchedInstalled
            val filteredOnline = if (searchTerm.isBlank()) onlinePool else searchedOnline
            val filteredPersonal = myVoices.filter { voice ->
                (searchTerm.isBlank() || voice.name.lowercase(Locale.ROOT).contains(searchTerm))
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = voiceSearch,
                    onValueChange = { query ->
                        voiceSearch = query
                        // Search only filters the Online catalogue; it must never expose local Android voices.
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = T2SText
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    placeholder = {
                        Text(
                            "Search by voice name",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = { Text("⌕", fontSize = 19.sp, color = T2SOrange, fontWeight = FontWeight.Bold) },
                    trailingIcon = {
                        if (voiceSearch.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    stopVoicePreview()
                                    voiceSearch = ""
                                    voiceLibraryTab = "Online"
                                    selectedSavedVoice = null
                                    selectedSavedVoiceId = null
                                    selectedOnlineVoice = null
                                    selectedVoice = availableVoices.firstOrNull()
                                    selectedVoice?.let { voice ->
                                        ttsEngine?.let { engine ->
                                            engine.stop()
                                            engine.setLanguage(voice.locale)
                                            engine.voice = voice
                                        }
                                    }
                                    statusMessage = selectedVoice?.let { "Voice search cleared. ${it.name} is selected as the installed voice." }
                                        ?: "Voice search cleared. Select an installed voice to continue."
                                }
                            ) { Text("✕ Clear") }
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (if (isRegisteredUser) listOf("Online", "Installed", "My Voices") else listOf("Installed")).forEach { tab ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = voiceLibraryTab == tab,
                            onClick = { voiceLibraryTab = tab },
                            label = { Text(tab, maxLines = 1, softWrap = false, fontSize = 11.sp) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = T2SOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFFFF7F0),
                                labelColor = Color(0xFF6B5546)
                            )
                        )
                    }
                }
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
                    if (voiceLibraryTab == "Installed" && filteredInstalled.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("INSTALLED VOICES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("${filteredInstalled.size}", color = Color(0xFF58677F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            },
                            onClick = {},
                            enabled = false
                        )
                        filteredInstalled.forEach { voice ->
                            val isSelected = selectedVoice?.name == voice.name
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFFFEBDD) else Color(0xFFFFF7F0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) T2SOrange else Color(0xFFF0D7C2))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(Modifier.weight(1f).clickable {
                                        isPurohitMode = false
                                        selectedVoice = voice
                                        selectedOnlineVoice = null
                                        selectedSavedVoice = null
                                        selectedSavedVoiceId = null
                                        voiceSearch = voice.name
                                        ttsEngine?.let { it.stop(); it.setLanguage(voice.locale); it.voice = voice }
                                        statusMessage = "${voice.name} selected."
                                    }) {
                                        Text(
                                            installedVoiceDisplayName(voice, filteredInstalled.indexOf(voice) + 1),
                                            color = T2SText,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                    if (isSelected) {
                                        Text("✓", color = T2SOrange, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            val engine = ttsEngine
                                            if (engine != null && engineReady) {
                                                if (isPreviewing && activePreviewKey == "installed:${voice.name}") {
                                                    stopVoicePreview()
                                                    statusMessage = "Preview stopped."
                                                    return@TextButton
                                                }
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
                                        },
                                        enabled = engineReady
                                    ) {
                                        Text(
                                            if (isPreviewing && activePreviewKey == "installed:${voice.name}") "■ Stop" else "▶ Play",
                                            color = T2SOrange,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (voiceLibraryTab == "Online" && filteredOnline.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text("ONLINE VOICES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("${filteredOnline.size}", color = Color(0xFF58677F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            },
                            onClick = {},
                            enabled = false
                        )
                        filteredOnline.forEach { voice ->
                            val isSelected = selectedOnlineVoice?.let { it.provider == voice.provider && it.id == voice.id } == true
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFFFEBDD) else Color(0xFFFFF7F0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) T2SOrange else Color(0xFFF0D7C2))
                            ) {
                              Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable {
                                    isPurohitMode = false
                                    selectedOnlineVoice = voice
                                    selectedVoice = null
                                    selectedSavedVoice = null
                                    selectedSavedVoiceId = null
                                    voiceSearch = voice.name
                                    statusMessage = voice.name + " selected from " + voice.provider + ". Voice licence: " + voice.licence + ". " + voice.attribution
                                }) {
                                    Text(voice.name, color = T2SText, fontWeight = FontWeight.SemiBold)
                                if (isSelected) {
                                    Text("✓", color = T2SOrange, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                }
                                }
                                TextButton(enabled = true, onClick = { previewOnlineVoice(voice.id, previewKey = "online:${voice.provider}:${voice.id}", provider = voice.provider) }) {
                                    Text(if (activePreviewKey == "online:${voice.provider}:${voice.id}" && isPreviewing) "■ Stop" else "▶ Play", color = Color(0xFFF47721), fontWeight = FontWeight.Bold)
                                }
                              }
                            }
                        }
                    }
                    if (voiceLibraryTab == "My Voices" && filteredPersonal.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("MY VOICES", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            onClick = {},
                            enabled = false
                        )
                        filteredPersonal.forEach { voice ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = if (selectedSavedVoice?.id == voice.id) Color(0xFFFFEBDD) else Color(0xFFFFF7F0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedSavedVoice?.id == voice.id) T2SOrange else Color(0xFFF0D7C2))
                            ) {
                                Row(Modifier.fillMaxWidth().clickable {
                                    isPurohitMode = false
                                    selectedSavedVoice = voice
                                    selectedSavedVoiceId = voice.id
                                    selectedVoice = null
                                    selectedOnlineVoice = null
                                    val savedLanguage = when (voice.language.lowercase()) {
                                        "te", "telugu" -> "Telugu"
                                        "en", "english" -> "English"
                                        "hi", "hindi" -> "Hindi"
                                        else -> selectedLanguage
                                    }
                                    selectedLanguage = savedLanguage
                                    voiceSearch = voice.name
                                    statusMessage = "${voice.name} selected • ${voice.gender} • ${savedLanguage}. The saved sample will be used as the voice-cloning reference."
                                }.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(voice.name + if (selectedSavedVoice?.id == voice.id) " ✓" else "", modifier = Modifier.weight(1f), color = T2SText, fontWeight = FontWeight.SemiBold)
                                    TextButton(enabled = true, onClick = { previewOnlineVoice(voice.id, previewKey = "personal:${voice.id}", localSamplePath = voice.localSamplePath, languageOverride = voice.language, sarvamVoiceId = voice.sarvamVoiceId) }) {
                                        Text(if (activePreviewKey == "personal:${voice.id}" && isPreviewing) "■ Stop" else "▶ Play", color = T2SOrange, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    if ((voiceLibraryTab == "Installed" && filteredInstalled.isEmpty()) || (voiceLibraryTab == "Online" && filteredOnline.isEmpty()) || (voiceLibraryTab == "My Voices" && filteredPersonal.isEmpty())) {
                        DropdownMenuItem(
                            text = { Text(when (voiceLibraryTab) { "Installed" -> if (filteredInstalled.isEmpty()) "No installed voices match this search and language." else ""; "Online" -> if (onlineCatalogueStatus.startsWith("Loading")) "Loading online voices…" else "No online voices match this search and language."; else -> "No saved voices match this search." }, color = T2SSecondaryText) },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
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
                (if (isRegisteredUser) listOf("WAV", "MP3", "OGG") else listOf("WAV")).forEach { format ->
                    val formatSupported = !(selectedOnlineVoice?.provider == "Vakyam" && format == "OGG")
                    FilterChip(
                        selected = outputFormat == format,
                        enabled = formatSupported,
                        onClick = { outputFormat = format },
                        label = { Text(format, fontSize = 12.sp) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = T2SOrange,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFFFF7F0),
                            labelColor = Color(0xFF6B5546),
                            disabledContainerColor = Color(0xFFF3EFEB),
                            disabledLabelColor = Color(0xFFB8AAA0)
                        )
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

        LabeledSpeechSlider(
            title = "Speaking speed",
            value = speechRate,
            onValueChange = { speechRate = it },
            lowLabel = "Slow",
            highLabel = "Fast"
        )

        LabeledSpeechSlider(
            title = "Voice pitch",
            value = speechPitch,
            onValueChange = { speechPitch = it },
            lowLabel = "Low",
            highLabel = "High"
        )



        Button(
            onClick = {
                val textToSpeak = inputText.trim()
                val languageCode = when (selectedLanguage) {
                    "English" -> "en"
                    "Hindi" -> "hi"
                    "Telugu" -> "te"
                    else -> ""
                }
                val personalVoice = selectedSavedVoice
                val onlineVoice = selectedOnlineVoice
                val usingPersonalVoice = personalVoice != null
                val guestMode = !isRegisteredUser
                val installedVoice = selectedVoice
                val outputFormatForRequest = if (usingPersonalVoice || guestMode) "wav" else outputFormat.lowercase(Locale.ROOT)
                val directory = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
                    "T2SStudio"
                ).apply { mkdirs() }
                val output = File(directory, "speech_" + System.currentTimeMillis() + "." + outputFormatForRequest)
                when {
                    textToSpeak.isBlank() -> statusMessage = "Please enter some text first."
                    languageCode.isBlank() -> statusMessage = "This language is not supported by the speech service."
                    personalVoice != null && languageCode !in setOf("te", "en") ->
                        statusMessage = "My Voices generation currently supports Telugu and English. Choose one of those languages."
                    personalVoice != null && (when (personalVoice.language.lowercase(Locale.ROOT).substringBefore('-')) {
                        "te", "telugu" -> "te"
                        "en", "english" -> "en"
                        "hi", "hindi" -> "hi"
                        else -> ""
                    }) != languageCode ->
                        statusMessage = "The selected My Voice profile language does not match the script language."
                    personalVoice != null && personalVoice.sarvamVoiceId.isBlank() ->
                        statusMessage = "${personalVoice.name} is saved locally but is not registered with Sarvam yet. Open My Voices and complete voice registration before generating."
                    personalVoice != null && textToSpeak.length > 1000 ->
                        statusMessage = "My Voices supports up to 1,000 characters per generation. Shorten your text and try again."
                    guestMode && (!engineReady || installedVoice == null) ->
                        statusMessage = "No installed ${selectedLanguage} voice is available. Install an offline voice in Android Text-to-speech settings and try again."
                    guestMode && installedVoice?.isNetworkConnectionRequired == true ->
                        statusMessage = "This voice requires a network connection. Choose an installed offline voice for Guest mode."
                    guestMode && textToSpeak.length > 2000 ->
                        statusMessage = "Guest audio is limited to 2,000 characters. Shorten your text and try again."
                    !guestMode && personalVoice == null && textToSpeak.length > 3000 ->
                        statusMessage = "Online TTS supports up to 3,000 characters per request. Shorten your text and try again."
                    !guestMode && personalVoice == null && (onlineVoice == null || !onlineVoice.language.substringBefore('-').equals(languageCode, ignoreCase = true)) ->
                        statusMessage = "Choose an online ${selectedLanguage} voice before generating speech."
                    else -> {
                        isGenerating = true
                        generatedFile = null
                        stopVoicePreview()
                        statusMessage = when {
                            guestMode -> "Creating audio on this device with ${installedVoice?.name ?: "the installed voice"}…"
                            personalVoice != null -> "Generating speech with your voice: ${personalVoice.name}…"
                            else -> "Generating ${selectedLanguage} speech online…"
                        }
                        coroutineScope.launch {
                            val result = runCatching {
                                if (guestMode) {
                                    withContext(Dispatchers.Main) {
                                        synthesizeInstalledVoiceToFile(requireNotNull(ttsEngine), textToSpeak, requireNotNull(installedVoice), speechRate, speechPitch, output)
                                    }
                                } else withContext(Dispatchers.IO) {
                                    if (personalVoice != null) {
                                        SarvamVoiceCloneClient.generateCentral(
                                            voiceId = personalVoice.sarvamVoiceId,
                                            text = textToSpeak,
                                            language = languageCode,
                                            output = output
                                        )
                                    } else if (onlineVoice!!.provider == "Vakyam") {
                                        CentralOnlineTtsClient.generate(
                                            "Vakyam", textToSpeak, onlineVoice.id, languageCode,
                                            speechRate, output, format = outputFormatForRequest
                                        )
                                    } else {
                                        CentralOnlineTtsClient.generate(
                                            "India API", textToSpeak, onlineVoice.id, languageCode,
                                            speechRate, output, format = outputFormatForRequest
                                        )
                                    }
                                }
                            }
                            if (result.isSuccess && output.isFile && output.length() > 1024L) {
                                isGenerating = false
                                generatedFile = output
                                if (!guestMode) onAudioGenerated(
                                    AudioRecord(
                                        id = java.util.UUID.randomUUID().toString(),
                                        filePath = output.absolutePath,
                                        textPreview = textToSpeak.take(180),
                                        language = selectedLanguage,
                                        createdAt = System.currentTimeMillis(),
                                        projectId = "",
                                        projectName = "Unassigned",
                                        scriptContent = textToSpeak,
                                        voiceSource = if (personalVoice != null) "My Voice" else "Standard",
                                        voiceName = personalVoice?.name.orEmpty(),
                                        voiceGender = personalVoice?.gender.orEmpty()
                                    )
                                )
                                statusMessage = when {
                                    guestMode -> "Your audio is ready. Tap Download to save it to your device."
                                    personalVoice != null -> "Speech generated with ${personalVoice.name}. Your audio is ready in Preview."
                                    else -> "Online speech audio is ready and saved in T2S Studio."
                                }
                                if (autoPlayAfterGeneration || personalVoice != null) playGeneratedFile(output)
                            } else {
                                output.delete()
                                isGenerating = false
                                val generationError = result.exceptionOrNull()?.message ?: "The service returned invalid audio."
                                if (generationError.contains("Daily limit reached", ignoreCase = true)) {
                                    dailyGenerationLimitReached = true
                                }
                                statusMessage = (if (personalVoice != null) "My Voice generation failed: " else "Online TTS failed: ") +
                                    generationError +
                                    if (dailyGenerationLimitReached) "" else " Check the selected voice and try again."
                            }
                        }
                    }
                }
            },
            enabled = !isGenerating && !dailyGenerationLimitReached && inputText.isNotBlank(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Generate speech",
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Generate Speech", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
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
            if (isRegisteredUser) "Online speech generation is available for signed-in users. Guest mode uses an installed offline Android voice; generated audio can be downloaded." else "Guest mode creates audio on this device using an installed offline Android voice. Audio is not saved to an account.",
            fontSize = 11.sp,
            color = T2SSecondaryText
        )
    }
}

@Composable
private fun LabeledSpeechSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    lowLabel: String,
    highLabel: String
) {
    val tickLabels = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            Spacer(Modifier.weight(1f))
            Text("${"%.2f".format(value).trimEnd('0').trimEnd('.')}x", color = T2SOrange, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.5f..2f,
            steps = 5
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            tickLabels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(4.dp).background(T2SOrange, RoundedCornerShape(50)))
                        Text(label, color = T2SSecondaryText, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lowLabel, color = T2SSecondaryText, fontSize = 11.sp)
            Text(highLabel, color = T2SSecondaryText, fontSize = 11.sp)
        }
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

private fun installedVoiceDisplayName(voice: Voice, index: Int): String {
    return "Voice " + index + " (" + voice.locale.toLanguageTag() + ")"
}