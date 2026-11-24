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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.firebase.auth.FirebaseAuth
import com.t2sstudio.app.data.firebase.T2SFirestoreRepository
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import java.io.File

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
        if (previewingVoiceId == voice.id && previewPlayer?.isPlaying == true) {
            previewPlayer?.pause()
            previewPlayer?.seekTo(0)
            previewPlayer?.release()
            previewPlayer = null
            previewingVoiceId = null
            return
        }
        runCatching {
            previewPlayer?.release()
            previewPlayer = null
            previewingVoiceId = null
            val sampleFile = voice.localSamplePath?.let(::File)
                ?: error("The original recording is not available on this device.")
            require(sampleFile.exists() && sampleFile.length() > 1024L) {
                "The original recording is missing or empty. Restore the voice backup to preview it."
            }
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
                        previewingVoiceId = null
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    player.release()
                    if (previewPlayer === player) previewPlayer = null
                    previewingVoiceId = null
                    loadMessage = "Preview failed. Please try again."
                }
            })
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(sampleFile)))
            player.prepare()
            player.play()
            previewingVoiceId = voice.id
        }.onFailure { error ->
            previewingVoiceId = null
            loadMessage = error.localizedMessage ?: "Could not preview this voice."
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                previewPlayer?.release()
                previewPlayer = null
                previewingVoiceId = null
                onBack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SText)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("My Voices List", color = T2SText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Your saved voice profiles", color = T2SSecondaryText, fontSize = 12.sp)
            }
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
                                    Text(
                                        if (voice.firebaseSynced) "Saved to Firebase" else "Saved on this device",
                                        color = T2SSecondaryText,
                                        fontSize = 11.sp
                                    )
                                    Button(
                                        onClick = { togglePreview(voice) },
                                        enabled = sampleAvailable,
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
                                            if (previewingVoiceId == voice.id) "Stop" else "Preview",
                                            modifier = Modifier.padding(start = 5.dp)
                                        )
                                    }
                                }
                                if (!sampleAvailable) {
                                    Text(
                                        "Original recording is not available on this device, so preview is unavailable.",
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
