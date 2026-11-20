package com.t2sstudio.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun audioMimeType(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    else -> "audio/wav"
}

/** Saves audio to the public Downloads collection where supported, or app-specific music storage on older Android. */
object AudioFileTransfer {
    fun saveToDownloads(context: Context, file: File): String {
        require(file.isFile && file.length() > 0L) { "The audio file is missing or empty." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, audioMimeType(file))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/T2S Studio")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create a Downloads entry.")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to open the Downloads output.")
                val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                check(context.contentResolver.update(uri, complete, null, null) > 0) {
                    "The audio was copied but could not be published in Downloads."
                }
            } catch (error: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw error
            }
            return "Saved to Downloads/T2S Studio."
        }

        val musicRoot = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val targetDirectory = File(musicRoot, "T2S Studio")
        check(targetDirectory.exists() || targetDirectory.mkdirs()) { "Could not create the app music folder." }
        file.copyTo(File(targetDirectory, file.name), overwrite = true)
        return "Saved to app storage: Music/T2S Studio."
    }
}

data class AudioRecord(
    val id: String,
    val filePath: String,
    val textPreview: String,
    val language: String,
    val createdAt: Long,
    val projectId: String = "",
    val projectName: String = "Unassigned"
)

object AudioHistoryStore {
    private const val PREFS = "t2s_audio_history"
    private const val KEY_ITEMS = "items"

    fun load(context: Context): List<AudioRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AudioRecord(
                    id = item.optString("id"),
                    filePath = item.optString("filePath"),
                    textPreview = item.optString("textPreview"),
                    language = item.optString("language"),
                    createdAt = item.optLong("createdAt"),
                    projectId = item.optString("projectId", ""),
                    projectName = item.optString("projectName", "Unassigned")
                )
            }.filter { it.id.isNotBlank() && it.filePath.isNotBlank() }
                .filter { File(it.filePath).exists() }
                .sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, records: List<AudioRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("filePath", record.filePath)
                put("textPreview", record.textPreview)
                put("language", record.language)
                put("createdAt", record.createdAt)
                put("projectId", record.projectId)
                put("projectName", record.projectName)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}

@Composable
fun SavedAudioScreen(
    records: List<AudioRecord>,
    onDelete: (AudioRecord) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playingId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var message by remember { mutableStateOf("Your generated recordings are stored on this device.") }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun share(record: AudioRecord) {
        runCatching {
            val file = File(record.filePath)
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = audioMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share speech audio"))
        }.onFailure { message = "Unable to share this recording." }
    }

    fun download(record: AudioRecord) {
        runCatching {
            AudioFileTransfer.saveToDownloads(context, File(record.filePath))
        }.onSuccess { message = it }
            .onFailure { message = "Unable to download this recording: ${it.localizedMessage ?: "storage error"}" }
    }

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Saved Audio", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
        Text("Generated audio files saved on this device.", fontSize = 13.sp, color = T2SSecondaryText)
        if (records.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = T2SCream), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = T2SOrange)
                    Text("No saved audio yet", color = T2SText, fontWeight = FontWeight.SemiBold)
                    Text("Generate speech and it will appear here.", color = T2SSecondaryText, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(modifier = androidx.compose.ui.Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = T2SBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.textPreview, color = T2SText, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(
                                "${record.language} • ${record.projectName} • " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(record.createdAt)),
                                color = T2SSecondaryText, fontSize = 11.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        val file = File(record.filePath)
                                        if (!file.exists()) {
                                            message = "Audio file is no longer available."
                                        } else if (playingId == record.id && player != null) {
                                            if (player?.isPlaying == true) {
                                                player?.pause()
                                                message = "Playback paused."
                                            } else {
                                                runCatching {
                                                    player?.start()
                                                    message = "Resuming recording."
                                                }.onFailure {
                                                    player?.release()
                                                    player = null
                                                    playingId = null
                                                    message = "Unable to resume this recording. Tap Play to restart."
                                                }
                                            }
                                        } else {
                                            runCatching {
                                                player?.release()
                                                player = MediaPlayer().apply {
                                                    setDataSource(file.absolutePath)
                                                    setOnCompletionListener {
                                                        it.release()
                                                        player = null
                                                        playingId = null
                                                    }
                                                    prepare()
                                                    start()
                                                }
                                                playingId = record.id
                                                message = "Playing recording."
                                            }.onFailure { message = "Unable to play this recording." }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = T2SCream, contentColor = T2SText)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (playingId == record.id) "Pause" else "Play")
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { download(record) }) { Icon(Icons.Filled.Download, contentDescription = "Download", tint = T2SOrange) }
                                IconButton(onClick = { share(record) }) { Icon(Icons.Filled.Share, contentDescription = "Share", tint = T2SOrange) }
                                IconButton(onClick = {
                                    if (playingId == record.id) {
                                        player?.release()
                                        player = null
                                        playingId = null
                                    }
                                    onDelete(record)
                                }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = T2SSecondaryText) }
                            }
                        }
                    }
                }
            }
        }
        Text(message, color = T2SSecondaryText, fontSize = 12.sp)
    }
}
