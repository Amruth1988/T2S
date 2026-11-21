package com.t2sstudio.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun MyVoicesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceName by remember { mutableStateOf("My Voice") }
    var cloneServerUrl by remember { mutableStateOf(VoiceCloneServerSettings.get(context)) }
    var checkingSarvam by remember { mutableStateOf(false) }
    var sarvamMessage by remember {
        mutableStateOf(
            "Checking the shared Sarvam service…"
        )
    }
    var checkingCloneServer by remember { mutableStateOf(false) }
    var cloneLanguage by remember { mutableStateOf("") }
    var voiceGender by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickedLabel by remember { mutableStateOf("No audio selected") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var previewingSavedVoiceId by remember { mutableStateOf<String?>(null) }
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
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { MyVoicesClient.exportBackup(context, uri) }
                }
                message = result.fold(
                    { "Backup created with ${it} voice profile(s). Keep the ZIP file somewhere safe." },
                    { "Backup export failed: ${it.message ?: "unknown error"}" }
                )
                busy = false
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { MyVoicesClient.importBackup(context, uri) }
                }
                result.onSuccess { (imported, skipped) ->
                    voices.clear()
                    voices.addAll(MyVoicesClient.list(context))
                    message = "Restored ${imported} voice profile(s). Skipped ${skipped} duplicate profile(s)."
                }.onFailure {
                    message = "Backup import failed: ${it.message ?: "unknown error"}"
                }
                busy = false
            }
        }
    }

    fun loadVoices() {
        runCatching { MyVoicesClient.list(context) }
            .onSuccess { saved -> voices.clear(); voices.addAll(saved) }
            .onFailure { message = "Could not load saved voice profiles: ${it.message}" }
    }

    LaunchedEffect(Unit) { loadVoices() }

    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null; runCatching { previewPlayer?.stop() }; runCatching { previewPlayer?.release() }; previewPlayer = null } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ExternalMediaCatalogueCard()
        Text("My Voices", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = T2SText)
        Text("Save and manage reusable voice recordings on this device. Local profiles remain available after reopening the app.", color = T2SSecondaryText, fontSize = 14.sp)
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("My Voices backup", fontWeight = FontWeight.SemiBold, color = T2SText)
                Text(
                    "Export profiles and available recordings to a ZIP file. After reinstalling T2S, choose Import backup and select that ZIP. Import merges profiles and does not replace existing ones. Backups stay in your chosen location; nothing is uploaded by this feature.",
                    color = T2SSecondaryText, fontSize = 12.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportBackupLauncher.launch("T2S_MyVoices_Backup.zip") },
                        enabled = !busy && !recording
                    ) { Text("Export backup") }
                    TextButton(
                        onClick = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !busy && !recording
                    ) { Text("Import backup") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Sarvam AI voice cloning", fontWeight = FontWeight.SemiBold, color = T2SText)
                Text("T2S uses the centrally managed Sarvam key. You do not need to enter or store an API key on this device. Your voice sample is sent securely through T2S to Sarvam only when you register it.", color = T2SSecondaryText, fontSize = 12.sp)
                TextButton(onClick = {
                    checkingSarvam = true
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { SarvamVoiceCloneClient.testCentralConnection() } }
                        sarvamMessage = result.getOrElse { "Shared Sarvam service: ${it.message ?: it.javaClass.simpleName}" }
                        checkingSarvam = false
                    }
                }, enabled = !checkingSarvam && !busy) {
                    if (checkingSarvam) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Test shared Sarvam service")
                }
                Text(sarvamMessage, color = T2SText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Voice cloning may consume the shared provider account's paid allowance. Daily limits apply. Only upload your own voice or a recording whose speaker has explicitly consented.", color = T2SSecondaryText, fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Self-hosted Volt-TTS server", fontWeight = FontWeight.SemiBold, color = T2SText)
                Text("Connect your own T2S Volt-TTS server to generate new Telugu or English speech from your saved voice sample. The sample is uploaded only when you tap Generate Speech.", color = T2SSecondaryText, fontSize = 12.sp)
                OutlinedTextField(
                    value = cloneServerUrl,
                    onValueChange = { cloneServerUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Server URL") },
                    placeholder = { Text("https://your-private-server:8080") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching { VoiceCloneServerSettings.save(context, cloneServerUrl) }
                                .onSuccess { message = "Voice-cloning server URL saved." }
                                .onFailure { message = it.message ?: "Invalid server URL." }
                        },
                        enabled = !checkingCloneServer
                    ) { Text("Save URL") }
                    TextButton(
                        onClick = {
                            checkingCloneServer = true
                            scope.launch {
                                val result = runCatching { withContext(Dispatchers.IO) { VoiceCloneClient.checkServer(cloneServerUrl) } }
                                message = result.getOrElse { "Server check failed: " + (it.message ?: "connection error") }
                                checkingCloneServer = false
                            }
                        },
                        enabled = !checkingCloneServer && cloneServerUrl.isNotBlank()
                    ) {
                        if (checkingCloneServer) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Test connection")
                    }
                }
                Text("The service requires your own NVIDIA CUDA host with about 8 GB VRAM. No paid TTS API or cloud account is configured by T2S.", color = T2SSecondaryText, fontSize = 11.sp)
                Text("Only use your own voice or a recording whose speaker has explicitly consented. Remote servers must use HTTPS.", color = T2SSecondaryText, fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Add a voice", fontWeight = FontWeight.SemiBold, color = T2SText)
                OutlinedTextField(value = voiceName, onValueChange = { voiceName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Voice name") }, singleLine = true)
                Text("Voice gender", color = T2SText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female", "Other").forEach { gender ->
                        androidx.compose.material3.FilterChip(
                            selected = voiceGender == gender,
                            onClick = { voiceGender = gender },
                            label = { Text(gender) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = T2SOrange,
                                selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                containerColor = T2SBackground,
                                labelColor = T2SText
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = voiceGender == gender,
                                borderColor = T2SOrange.copy(alpha = 0.45f),
                                selectedBorderColor = T2SOrange
                            )
                        )
                    }
                }
                Text("Voice sample language", color = T2SText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "te" to "Telugu", "hi" to "Hindi").forEach { (code, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = cloneLanguage == code,
                            onClick = { cloneLanguage = code },
                            label = { Text(label) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = T2SOrange,
                                selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                containerColor = T2SBackground,
                                labelColor = T2SText
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = cloneLanguage == code,
                                borderColor = T2SOrange.copy(alpha = 0.45f),
                                selectedBorderColor = T2SOrange
                            )
                        )
                    }
                }
                Text("Your recording and profile details are stored locally. Connect your own Volt-TTS server above to generate new Telugu or English speech in this voice. The recording is sent only when you request cloned generation.", fontSize = 12.sp, color = T2SSecondaryText)
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
                                    message = "Could not finish recording: " + (error.localizedMessage ?: error.javaClass.simpleName) + ". Please record again for at least 5 seconds."
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
                                        message = "Recording preview finished."
                                    }
                                }
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    player.release()
                                    if (previewPlayer === player) previewPlayer = null
                                    message = "Recording preview failed: ${error.message ?: "audio playback error"}"
                                }
                            })
                            player.setMediaItem(MediaItem.fromUri(uri))
                            player.prepare()
                            player.play()
                            message = "Playing your recording…"
                        }.onFailure { message = "Preview failed: " + (it.localizedMessage ?: "audio could not be played") }
                    }, enabled = pickedUri != null && !recording) { Text("▶ Preview recording") }
                    TextButton(onClick = { picker.launch("audio/*") }, enabled = !recording) { Text("Upload audio") }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = consentConfirmed, onCheckedChange = { consentConfirmed = it }, enabled = !busy)
                    Text("I confirm this is my voice, or I have permission to save and use this recording.", color = T2SSecondaryText, fontSize = 12.sp, lineHeight = 14.sp)
                }
                Button(
                    onClick = {
                        val uri = pickedUri
                        if (uri == null) message = "Record or select an audio file first."
                        else if (!consentConfirmed) message = "Confirm that this is your voice or that you have permission to use it."
                        else if (voiceName.trim().length !in 2..40) message = "Voice name must be between 2 and 40 characters."
                        else if (voiceGender !in listOf("Male", "Female", "Other")) message = "Select the voice gender from My Voices."
                        else if (cloneLanguage !in listOf("en", "te", "hi")) message = "Select the voice sample language from My Voices."
                        else {
                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val signedInUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                            ?: error("Sign in to T2S before registering a voice with Sarvam.")
                                        // Keep a local recovery copy, but do not report success until
                                        // Sarvam returns an ID and Firestore confirms the metadata write.
                                        val saved = MyVoicesClient.createLocal(context, voiceName, uri, cloneLanguage, voiceGender)
                                        val sample = saved.localSamplePath?.let(::File)
                                            ?: error("Saved recording could not be read.")
                                        val sarvamId = SarvamVoiceCloneClient.createCentralVoice(
                                            sample, saved.name, saved.language, saved.gender
                                        )
                                        require(sarvamId.startsWith("svc-")) { "Sarvam did not return a valid voice ID." }
                                        MyVoicesClient.attachSarvamVoiceId(context, saved.id, sarvamId)
                                        suspendCancellableCoroutine<Unit> { continuation ->
                                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveVoiceMetadata(
                                                uid = signedInUid,
                                                voiceId = saved.id,
                                                name = saved.name,
                                                gender = saved.gender,
                                                language = saved.language,
                                                sarvamVoiceId = sarvamId
                                            ) { cloudResult ->
                                                if (continuation.isActive) cloudResult.fold(
                                                    onSuccess = { continuation.resume(Unit) },
                                                    onFailure = { continuation.resumeWithException(it) }
                                                )
                                            }
                                        }
                                        MyVoicesClient.markFirebaseSynced(context, saved.id)
                                        MyVoicesClient.list(context).first { it.id == saved.id }
                                    }
                                }.onSuccess { saved ->
                                    loadVoices()
                                    voiceName = "My Voice"
                                    voiceGender = ""
                                    cloneLanguage = ""
                                    pickedUri = null
                                    pickedLabel = "No audio selected"
                                    consentConfirmed = false
                                    message = "${saved.name} Saved successfully. Sarvam voice ID: ${saved.sarvamVoiceId}"
                                }.onFailure { error ->
                                    loadVoices()
                                    message = "Not fully saved: ${error.message ?: "registration or Firebase save failed"}. Your local recording is retained; retry Firebase sync if Sarvam registration completed."
                                }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && !recording && pickedUri != null && consentConfirmed && voiceName.trim().length in 2..40 && voiceGender in listOf("Male", "Female", "Other") && cloneLanguage in listOf("en", "te", "hi")
                ) { Text("Save voice profile") }
            }
        }
        if (busy) CircularProgressIndicator()
        Text(message, color = T2SSecondaryText, fontSize = 12.sp, lineHeight = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Saved voice profiles", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = T2SText)
            TextButton(onClick = { loadVoices() }, enabled = !busy, modifier = Modifier.padding(0.dp)) { Text("↻ Refresh") }
        }
        voices.forEach { voice ->
            val sampleFileForStatus = voice.localSamplePath?.let(::File)
            val sampleAvailable = sampleFileForStatus != null &&
                sampleFileForStatus.exists() && sampleFileForStatus.length() > 1024L
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
                            Text("${voice.gender} • ${voice.language.uppercase()} • " + when { voice.firebaseSynced -> "Saved to Firebase"; voice.sarvamVoiceId.isNotBlank() -> "Firebase sync pending"; else -> "Local only" }, color = T2SSecondaryText, fontSize = 12.sp)
                        }
                        Text(if (voice.firebaseSynced) "SAVED" else "PENDING", color = T2SOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(T2SCream, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    androidx.compose.material3.HorizontalDivider(color = T2SOrange.copy(alpha = 0.16f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("TYPE", color = T2SSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(if (voice.sarvamVoiceId.isNotBlank()) "Sarvam cloud voice" else "Local recording only", color = T2SText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Voice ID  •  ${voice.sarvamVoiceId.ifBlank { voice.id }}", color = T2SSecondaryText, fontSize = 10.sp, maxLines = 1)
                    if (voice.sarvamVoiceId.isBlank() && sampleAvailable) {
                        Button(
                            onClick = {
                                run {
                                    busy = true
                                    scope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                val sample = voice.localSamplePath?.let(::File)
                                                    ?: error("Saved voice sample is missing.")
                                                val sarvamId = SarvamVoiceCloneClient.createCentralVoice(
                                                    sample, voice.name, voice.language, voice.gender
                                                )
                                                MyVoicesClient.attachSarvamVoiceId(context, voice.id, sarvamId)
                                                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                                    ?: error("Sign in to T2S before syncing voice metadata.")
                                                suspendCancellableCoroutine<Unit> { continuation ->
                                                    com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveVoiceMetadata(
                                                        uid = uid,
                                                        voiceId = voice.id,
                                                        name = voice.name,
                                                        gender = voice.gender,
                                                        language = voice.language,
                                                        sarvamVoiceId = sarvamId
                                                    ) { cloudResult ->
                                                        if (continuation.isActive) cloudResult.fold(
                                                            onSuccess = { continuation.resume(Unit) },
                                                            onFailure = { continuation.resumeWithException(it) }
                                                        )
                                                    }
                                                }
                                                MyVoicesClient.markFirebaseSynced(context, voice.id)
                                                MyVoicesClient.list(context).first { it.id == voice.id }
                                            }
                                        }
                                        result.onSuccess { registered ->
                                            loadVoices()
                                            message = "${registered.name} Saved successfully. Sarvam voice ID: ${registered.sarvamVoiceId}"
                                        }.onFailure { error ->
                                            loadVoices()
                                            message = "Not fully saved for ${voice.name}: ${error.message ?: error.javaClass.simpleName}. If Sarvam returned an ID, use Sync metadata to Firebase."
                                        }
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Registering…" else "Register existing voice with Sarvam") }
                    }
                    if (voice.sarvamVoiceId.isNotBlank()) {
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    val result = runCatching {
                                        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                            ?: error("Sign in to T2S before syncing voice metadata.")
                                        suspendCancellableCoroutine<Unit> { continuation ->
                                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveVoiceMetadata(
                                                uid = uid,
                                                voiceId = voice.id,
                                                name = voice.name,
                                                gender = voice.gender,
                                                language = voice.language,
                                                sarvamVoiceId = voice.sarvamVoiceId
                                            ) { cloudResult ->
                                                if (continuation.isActive) cloudResult.fold(
                                                    onSuccess = { continuation.resume(Unit) },
                                                    onFailure = { continuation.resumeWithException(it) }
                                                )
                                            }
                                        }
                                        MyVoicesClient.markFirebaseSynced(context, voice.id)
                                    }
                                    result.onSuccess { loadVoices() }
                                    message = result.fold(
                                        { "${voice.name} Saved successfully. Sarvam voice ID: ${voice.sarvamVoiceId}" },
                                        { "Firebase metadata sync failed: ${it.localizedMessage ?: "try again"}" }
                                    )
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Sync metadata to Firebase") }
                    }
                    if (!sampleAvailable) {
                        Text(
                            "Original recording is missing on this device. This profile is retained, but local preview and sample-based cloning are unavailable. Restore a backup recording to use those features.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            if (sampleAvailable) "Saved sample • preview the original recording" else "No local audio sample",
                            color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(enabled = sampleAvailable, onClick = {
                            val currentPlayer = previewPlayer
                            if (previewingSavedVoiceId == voice.id && currentPlayer?.isPlaying == true) {
                                currentPlayer.pause()
                                currentPlayer.seekTo(0)
                                previewingSavedVoiceId = null
                                message = "Voice sample preview stopped."
                            } else runCatching {
                                currentPlayer?.release()
                                previewPlayer = null
                                previewingSavedVoiceId = null
                                val sampleFile = voice.localSamplePath?.let(::File)
                                    ?: error("Saved voice sample file is missing.")
                                require(sampleFile.exists() && sampleFile.length() > 1024L) { "Saved voice sample file is missing or empty." }
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
                                            previewingSavedVoiceId = null
                                            message = "${voice.name} sample preview finished."
                                        }
                                    }
                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        player.release()
                                        if (previewPlayer === player) previewPlayer = null
                                        previewingSavedVoiceId = null
                                        message = "Sample preview failed: ${error.message ?: "audio playback error"}"
                                    }
                                })
                                player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(sampleFile)))
                                player.prepare()
                                player.play()
                                previewingSavedVoiceId = voice.id
                                message = "Playing saved sample for ${voice.name}. This is the original recording, not generated speech."
                            }.onFailure {
                                previewingSavedVoiceId = null
                                message = "Could not preview saved voice: ${it.localizedMessage ?: "audio playback error"}"
                            }
                        }) { Text(if (previewingSavedVoiceId == voice.id) "■ Stop" else "▶ Preview") }
                        TextButton(onClick = {
                            if (previewingSavedVoiceId == voice.id) {
                                runCatching { previewPlayer?.release() }
                                previewPlayer = null
                                previewingSavedVoiceId = null
                            }
                            runCatching { MyVoicesClient.deleteLocal(context, voice.id); loadVoices(); message = "Voice profile deleted from this device." }
                                .onFailure { message = "Could not delete profile: ${it.message}" }
                        }) { Text("Delete") }
                    }
                }
            }
        }
        Text("These recordings are stored locally as audio samples. They do not create new speech in your recorded voice.", color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp)
        
    }
}
