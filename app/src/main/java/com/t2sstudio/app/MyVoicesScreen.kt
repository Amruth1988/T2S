package com.t2sstudio.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import java.io.File
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MyVoicesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.TTS_API_KEY
    var voiceName by remember { mutableStateOf("My Voice") }
    var model by remember { mutableStateOf("chatterbox") }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickedLabel by remember { mutableStateOf("No audio selected") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var hasMicPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var message by remember { mutableStateOf("Add a clear recording of your own voice to create a reusable voice profile.") }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasMicPermission = granted; message = if (granted) "Microphone ready. Tap Start recording." else "Microphone permission is required." }
    var busy by remember { mutableStateOf(false) }
    var consentConfirmed by remember { mutableStateOf(false) }
    val voices = remember { mutableStateListOf<SavedUserVoice>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedUri = uri
        pickedLabel = if (uri == null) "No audio selected" else uri.lastPathSegment ?: "Audio sample selected"
    }

    fun loadVoices() {
        if (apiKey.isBlank()) { message = "Personal voices are not configured in this build. Add the TTS_API_KEY GitHub Actions secret and rebuild."; return }
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { MyVoicesClient.list(context, apiKey.trim()) } }
                .onSuccess { voices.clear(); voices.addAll(it); message = "Loaded ${it.size} saved voice profiles." }
                .onFailure { message = it.message ?: "Could not load saved voices." }
            busy = false
        }
    }

    LaunchedEffect(Unit) { if (apiKey.isNotBlank()) loadVoices() }
    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null; runCatching { previewPlayer?.stop() }; runCatching { previewPlayer?.release() }; previewPlayer = null } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("My Voices", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = T2SText)
        Text("Create and manage reusable AI voice profiles from your own recordings.", color = T2SSecondaryText, fontSize = 14.sp)
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Add a voice", fontWeight = FontWeight.SemiBold, color = T2SText)
                OutlinedTextField(value = voiceName, onValueChange = { voiceName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Voice name") }, singleLine = true)
                Text("Cloning model", color = T2SText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("chatterbox", "cosyvoice2").forEach { option ->
                        androidx.compose.material3.FilterChip(
                            selected = model == option, onClick = { model = option },
                            label = { Text(option) }
                        )
                    }
                }
                Text("Record your voice directly here, or select an existing file. Speak naturally in a quiet room for 10–30 seconds.", fontSize = 12.sp, color = T2SSecondaryText)
                Card(
                    colors = CardDefaults.cardColors(containerColor = T2SCream),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, T2SOrange.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(46.dp).background(T2SOrange.copy(alpha = 0.14f), RoundedCornerShape(15.dp)),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text("🎙️", fontSize = 24.sp) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Record with your phone microphone", fontWeight = FontWeight.Bold, color = T2SText, fontSize = 15.sp)
                                Text("Capture a clear sample using your device", color = T2SSecondaryText, fontSize = 11.sp)
                            }
                            Text(if (recording) "● LIVE" else "READY", color = if (recording) androidx.compose.ui.graphics.Color(0xFFC62828) else T2SOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(T2SBackground, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                        androidx.compose.material3.HorizontalDivider(color = T2SOrange.copy(alpha = 0.18f))
                        Text("Speak naturally for 10–30 seconds in a quiet room.", color = T2SSecondaryText, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (!hasMicPermission) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                else runCatching {
                                    val sampleDir = File(context.cacheDir, "voice_samples").apply { mkdirs() }
                                    val file = File(sampleDir, "my_voice_sample.m4a")
                                    if (file.exists()) file.delete()
                                    val mr = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                                    mr.setAudioSource(MediaRecorder.AudioSource.MIC); mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); mr.setAudioSamplingRate(44100); mr.setAudioEncodingBitRate(128000)
                                    mr.setOutputFile(file.absolutePath); mr.prepare(); mr.start()
                                    recorder = mr; recordingFile = file; recordingStartedAt = android.os.SystemClock.elapsedRealtime(); recording = true; message = "Recording started. Speak clearly for at least 3 seconds; 10–30 seconds is recommended."
                                }.onFailure { message = "Could not start recording: " + it.message }
                            }, enabled = !recording && !busy, shape = RoundedCornerShape(14.dp)) { Text("▶", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                            Button(onClick = {
                                val activeRecorder = recorder
                                val elapsedMs = android.os.SystemClock.elapsedRealtime() - recordingStartedAt
                                try {
                                    if (elapsedMs < 5000L) {
                                        message = "Please record for at least 5 seconds before stopping. 10–30 seconds is recommended."
                                        return@Button
                                    }
                                    activeRecorder?.stop()
                                    val file = recordingFile
                                    if (file != null && file.exists() && file.length() > 1024L) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context, context.packageName + ".fileprovider", file
                                        )
                                        pickedUri = uri
                                        pickedLabel = "Recorded voice sample (" + file.length() / 1024 + " KB)"
                                        message = "Recording ready. You can now create your voice profile."
                                    } else {
                                        message = "No usable audio was saved. Please record again in a quiet place."
                                    }
                                } catch (error: Exception) {
                                    message = "Could not finish recording: " + (error.localizedMessage ?: error.javaClass.simpleName) + ". Please record again for at least 3 seconds."
                                } finally {
                                    runCatching { activeRecorder?.reset() }
                                    runCatching { activeRecorder?.release() }
                                    recorder = null
                                    recording = false
                                }
                            }, enabled = recording, shape = RoundedCornerShape(14.dp)) { Text("■", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        }
                        if (recording) Text("● Recording in progress — tap ■ when finished", color = androidx.compose.ui.graphics.Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) else Text("Tap ▶ to begin recording", color = T2SSecondaryText, fontSize = 11.sp)
                    }
                }
                OutlinedTextField(value = pickedLabel, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text("Reference recording") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val uri = pickedUri
                        if (uri == null) message = "Record or select an audio file first."
                        else runCatching {
                            previewPlayer?.release()
                            val player = MediaPlayer.create(context, uri) ?: error("Could not open this audio sample.")
                            previewPlayer = player
                            player.setOnCompletionListener { it.release(); if (previewPlayer === it) previewPlayer = null; message = "Recording preview finished." }
                            player.start()
                            message = "Playing your recording…"
                        }.onFailure { message = "Preview failed: " + (it.localizedMessage ?: "audio could not be played") }
                    }, enabled = pickedUri != null && !recording) { Text("▶ Preview recording") }
                    TextButton(onClick = { picker.launch("audio/*") }, enabled = !recording) { Text("Upload audio") }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = consentConfirmed, onCheckedChange = { consentConfirmed = it }, enabled = !busy)
                    Text("I confirm this is my voice, or I have permission to clone it.", color = T2SSecondaryText, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val uri = pickedUri
                        if (apiKey.isBlank()) message = "Personal voices are not configured in this build. Add the TTS_API_KEY GitHub Actions secret and rebuild."
                        else if (!consentConfirmed) message = "Please confirm you have the right to clone this voice."
                        else if (voiceName.isBlank()) message = "Enter a name for this voice."
                        else if (uri == null) message = "Choose a voice recording first."
                        else {
                            busy = true
                            message = "Uploading sample and creating your saved voice…"
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { MyVoicesClient.create(context, apiKey.trim(), voiceName.trim(), uri, model) } }
                                    .onSuccess { created -> voices.add(0, created); pickedUri = null; pickedLabel = "No audio selected"; message = "${created.name} was saved to your voice library." }
                                    .onFailure { message = it.message ?: "Could not create voice profile." }
                                busy = false
                            }
                        }
                    }, enabled = !busy && consentConfirmed
                ) { Text("Create and save voice") }
            }
        }
        if (busy) CircularProgressIndicator()
        Text(message, color = T2SSecondaryText, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Saved voice profiles", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            TextButton(onClick = { loadVoices() }, enabled = !busy, modifier = Modifier.padding(0.dp)) { Text("↻ Refresh") }
        }
        voices.forEach { voice ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = T2SBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, T2SOrange.copy(alpha = 0.22f))
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(48.dp).background(T2SCream, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) { Text("🎙️", fontSize = 23.sp) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(voice.name, color = T2SText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Personal voice profile", color = T2SSecondaryText, fontSize = 12.sp)
                        }
                        Text("SAVED", color = T2SOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(T2SCream, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    androidx.compose.material3.HorizontalDivider(color = T2SOrange.copy(alpha = 0.16f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("MODEL", color = T2SSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(voice.model, color = T2SText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Voice ID  •  ${voice.id}", color = T2SSecondaryText, fontSize = 10.sp, maxLines = 1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Ready for supported voice generation", color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            busy = true
                            message = "Generating AI preview for ${voice.name}…"
                            scope.launch {
                                val audioFile = File(context.cacheDir, "saved_voice_preview_${voice.id.hashCode()}.mp3")
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val previewUrl = voice.previewUrl
                                        if (!previewUrl.isNullOrBlank()) {
                                            TtsAiClient.downloadPreview(previewUrl, audioFile)
                                        } else {
                                            TtsAiClient.generate(
                                                text = "Hello. This is a preview of my saved voice.",
                                                language = "en",
                                                voice = "",
                                                userVoiceId = voice.id,
                                                speed = 1f,
                                                output = audioFile,
                                                apiKey = apiKey.trim(),
                                                model = voice.model,
                                                format = "mp3"
                                            )
                                        }
                                    }
                                }
                                if (result.isSuccess) {
                                    runCatching {
                                        previewPlayer?.release()
                                        val player = MediaPlayer()
                                        previewPlayer = player
                                        player.setAudioAttributes(
                                            android.media.AudioAttributes.Builder()
                                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                                .build()
                                        )
                                        player.setVolume(1f, 1f)
                                        player.setDataSource(audioFile.absolutePath)
                                        player.setOnPreparedListener { it.start(); message = "Playing AI preview: ${voice.name}…" }
                                        player.setOnCompletionListener { it.release(); if (previewPlayer === it) previewPlayer = null; audioFile.delete(); message = "AI voice preview finished." }
                                        player.setOnErrorListener { mp, _, _ -> mp.release(); if (previewPlayer === mp) previewPlayer = null; audioFile.delete(); message = "Could not play the generated voice preview."; true }
                                        player.prepareAsync()
                                    }.onFailure {
                                        audioFile.delete()
                                        message = "Preview playback failed: ${it.localizedMessage ?: "audio player error"}"
                                    }
                                } else {
                                    audioFile.delete()
                                    message = "AI preview failed: ${result.exceptionOrNull()?.message ?: "voice service unavailable"}"
                                }
                                busy = false
                            }
                        }, enabled = !busy && !recording) { Text("▶ Preview") }
                    }
                }
            }
        }
        Text("Language note: English and Hindi are documented for multilingual voice workflows. Telugu availability depends on the selected model and is not guaranteed by the provider.", color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp)
        Text("Voice cloning consumes account credits. Check the provider's current usage before creating profiles.", color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp)
    }
}
