package com.t2sstudio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val T2S_MEDIA_MANIFEST_URL = "https://t2s-media.pages.dev/manifest.json"

@Composable
fun ExternalMediaCatalogueCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assets by remember { mutableStateOf<List<ExternalMediaAsset>>(emptyList()) }
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading free Telugu voice previews…") }
    val asset = assets.firstOrNull { it.id == "telugu-voice-preview-01" }

    fun refresh() {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExternalMediaClient.fetchCatalogue(T2S_MEDIA_MANIFEST_URL) } }
                .onSuccess { list ->
                    assets = list
                    status = if (list.isEmpty()) "No public media is available yet." else "Catalogue ready • ${list.size} item(s)"
                }
                .onFailure { status = "Catalogue unavailable: ${it.localizedMessage ?: "network error"}" }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() }; player = null }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = T2SBackground)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Free voice previews", color = T2SText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("External catalogue • Cloudflare Pages • verified offline cache", color = T2SSecondaryText, fontSize = 11.sp)
            if (busy) CircularProgressIndicator()
            Text(status, color = T2SSecondaryText, fontSize = 12.sp)
            if (asset != null) {
                Text(asset.title, color = T2SText, fontWeight = FontWeight.SemiBold)
                Text("${asset.language} • ${asset.kind} • ${asset.sizeBytes / 1024} KB", color = T2SSecondaryText, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { ExternalMediaClient.download(context, asset) } }
                                    .onSuccess { downloadedFile = it; status = "Downloaded and SHA-256 verified • saved privately on this device" }
                                    .onFailure { status = "Download failed: ${it.localizedMessage ?: "verification/network error"}" }
                                busy = false
                            }
                        }
                    ) { Text(if (downloadedFile?.exists() == true) "Download again" else "Download") }
                    OutlinedButton(
                        enabled = !busy && downloadedFile?.exists() == true,
                        onClick = {
                            runCatching {
                                player?.release()
                                val next = ExoPlayer.Builder(context).build()
                                player = next
                                next.addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        if (state == Player.STATE_ENDED) status = "Preview finished."
                                    }
                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        status = "Playback failed: ${error.message ?: "audio error"}"
                                    }
                                })
                                next.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(downloadedFile!!)))
                                next.prepare()
                                next.play()
                                status = "Playing verified Telugu preview…"
                            }.onFailure { status = "Could not play preview: ${it.localizedMessage}" }
                        }
                    ) { Text("▶ Play") }
                }
            }
            Text("Public samples only. Your personal recordings are never uploaded here.", color = T2SSecondaryText, fontSize = 10.sp)
        }
    }
}
