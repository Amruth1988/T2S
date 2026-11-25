package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import com.google.firebase.auth.FirebaseAuth
import com.t2sstudio.app.data.firebase.T2SFirestoreRepository
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MyVoicesListScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val repository = remember { T2SFirestoreRepository() }
    val voices = remember(uid) { mutableStateListOf<SavedUserVoice>() }
    var loading by remember { mutableStateOf(true) }
    var loadMessage by remember { mutableStateOf<String?>(null) }
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var previewingVoiceId by remember { mutableStateOf<String?>(null) }

    fun loadVoices() {
        if (uid.isBlank()) {
            voices.clear()
            loading = false
            loadMessage = "Sign in to view your saved voices."
            return
        }
        loading = true
        loadMessage = null
        val localProfiles = runCatching { MyVoicesClient.list(context, uid) }.getOrDefault(emptyList())
        repository.loadVoiceMetadata(uid) { result ->
            result.onSuccess { cloudProfiles ->
                val localById = localProfiles.associateBy { it.id }
                val cloudWithLocalSamples = cloudProfiles.map { cloud ->
                    val local = localById[cloud.id]
                    if (local == null) cloud else cloud.copy(
                        localSamplePath = local.localSamplePath,
                        name = local.name.ifBlank { cloud.name },
                        language = local.language.ifBlank { cloud.language },
                        gender = local.gender.ifBlank { cloud.gender }
                    )
                }
                val cloudIds = cloudWithLocalSamples.map { it.id }.toSet()
                val localOnly = localProfiles.filter { it.id !in cloudIds }
                voices.clear()
                voices.addAll((cloudWithLocalSamples + localOnly).sortedBy { it.name.lowercase() })
                loading = false
            }.onFailure { error ->
                voices.clear()
                voices.addAll(localProfiles.sortedBy { it.name.lowercase() })
                loading = false
                loadMessage = if (localProfiles.isEmpty()) {
                    "Could not load your Firebase voices. Check your connection and refresh."
                } else {
                    "Showing voices saved on this device. Firebase could not be reached."
                }
            }
        }
    }

    fun togglePreview(voice: SavedUserVoice) {
        if (previewingVoiceId == voice.id) {
            runCatching { previewPlayer?.stop() }
            runCatching { previewPlayer?.release() }
            previewPlayer = null
            previewingVoiceId = null
            return
        }
        runCatching { previewPlayer?.stop() }
        runCatching { previewPlayer?.release() }
        previewPlayer = null
        previewingVoiceId = voice.id
        loadMessage = null
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            var generatedPreview: File? = null
            val result = runCatching {
                val localSample = voice.localSamplePath?.let(::File)?.takeIf { it.isFile && it.length() > 1024L }
                val audioFile = if (localSample != null) localSample else {
                    require(voice.sarvamVoiceId.startsWith("svc-")) {
                        "This voice has no recording on this device or registered AI voice to preview."
                    }
                    val languageCode = when (voice.language.lowercase().substringBefore('-')) {
                        "te", "telugu" -> "te"
                        "hi", "hindi" -> "hi"
                        else -> "en"
                    }
                    val sampleText = when (languageCode) {
                        "te" -> "నమస్కారం. ఇది మీ వాయిస్ ప్రివ్యూ నమూనా."
                        "hi" -> "नमस्ते। यह आपकी आवाज़ का नमूना है."
                        else -> "Hello. This is a sample of your saved voice."
                    }
                    File(context.cacheDir, "saved_voice_preview_" + voice.id + "_" + System.currentTimeMillis() + ".wav")
                        .also { output ->
                            generatedPreview = output
                            withContext(Dispatchers.IO) {
                                SarvamVoiceCloneClient.generateCentral(voice.sarvamVoiceId, sampleText, languageCode, output)
                            }
                        }
                }
                check(previewingVoiceId == voice.id) { "Preview was cancelled." }
                val player = ExoPlayer.Builder(context).build()
                previewPlayer = player
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
                        if (playbackState == Player.STATE_ENDED) {
                            player.release()
                            if (previewPlayer === player) previewPlayer = null
                            generatedPreview?.delete()
                            if (previewingVoiceId == voice.id) previewingVoiceId = null
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        player.release()
                        if (previewPlayer === player) previewPlayer = null
                        generatedPreview?.delete()
                        if (previewingVoiceId == voice.id) previewingVoiceId = null
                        loadMessage = "Preview playback failed. Please try again."
                    }
                })
                player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(audioFile)))
                player.prepare()
                player.playWhenReady = true
            }
            result.onFailure { error ->
                runCatching { previewPlayer?.release() }
                previewPlayer = null
                generatedPreview?.delete()
                if (previewingVoiceId == voice.id) previewingVoiceId = null
                if (error.localizedMessage != "Preview was cancelled.") {
                    loadMessage = error.localizedMessage ?: "Could not preview this voice."
                }
            }
        }
    }
    LaunchedEffect(uid) { loadVoices() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { previewPlayer?.stop() }
            runCatching { previewPlayer?.release() }
            previewPlayer = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(T2SBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { loadVoices() }, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh voices", tint = T2SOrange)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = T2SOrange.copy(alpha = 0.10f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = T2SOrange,
                        modifier = Modifier.padding(14.dp).size(30.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your voices", color = T2SText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "All voice profiles saved to your account, with a preview of the original recording when it is available on this device.",
                        color = T2SSecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved voices", color = T2SText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${voices.size}", color = T2SOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (loading) {
                    CircularProgressIndicator(
                        color = T2SOrange,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else if (voices.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(38.dp))
                        Text("No saved voices yet", color = T2SText, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Create a voice from the My Voice tab and it will appear here.",
                            color = T2SSecondaryText,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    voices.forEach { voice ->
                        val sampleFile = voice.localSamplePath?.let(::File)
                        val sampleAvailable = sampleFile != null && sampleFile.exists() && sampleFile.length() > 1024L
                        val remotePreviewAvailable = voice.sarvamVoiceId.startsWith("svc-")
                        val canPreview = sampleAvailable || remotePreviewAvailable
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = T2SBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                                ) {
                                    Surface(color = T2SCream, shape = CircleShape) {
                                        Icon(
                                            Icons.Filled.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = T2SOrange,
                                            modifier = Modifier.padding(11.dp).size(25.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(voice.name, color = T2SText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${voice.gender} • ${voice.language.uppercase()}",
                                            color = T2SSecondaryText,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        if (voice.sarvamVoiceId.isNotBlank()) "AI VOICE" else "RECORDING",
                                        color = T2SOrange,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.background(T2SCream, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                                androidx.compose.material3.HorizontalDivider(color = T2SOrange.copy(alpha = 0.16f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = { togglePreview(voice) },
                                        enabled = canPreview,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = T2SOrange,
                                            contentColor = Color.White,
                                            disabledContainerColor = T2SOrange.copy(alpha = 0.18f),
                                            disabledContentColor = T2SSecondaryText
                                        )
                                    ) {
                                        Icon(
                                            if (previewingVoiceId == voice.id) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            if (previewingVoiceId == voice.id) "Stop recording" else "Play my recording",
                                            modifier = Modifier.padding(start = 5.dp)
                                        )
                                    }
                                }
                                if (!sampleAvailable && remotePreviewAvailable) {
                                    Text(
                                        "Original recording is not on this device. Preview will be generated using your saved AI voice.",
                                        color = T2SSecondaryText,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                } else if (!canPreview) {
                                    Text(
                                        "Preview is unavailable because the original recording and registered AI voice are not available.",
                                        color = T2SSecondaryText,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        loadMessage?.let { message ->
            Text(message, color = T2SSecondaryText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.size(8.dp))
    }
}
