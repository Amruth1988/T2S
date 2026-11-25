package com.t2sstudio.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.widget.Toast
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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
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
fun MyVoicesScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val accountUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    var voiceName by remember { mutableStateOf("") }
    var selectedAddTab by remember { mutableStateOf("Add Voice") }
    var showSarvamForm by remember { mutableStateOf(false) }
    var showVoltSettings by remember { mutableStateOf(false) }
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
    var savedPreviewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var previewingPickedRecording by remember { mutableStateOf(false) }
    var previewingSavedVoiceId by remember { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var hasMicPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var message by remember { mutableStateOf("Add a clear recording of your own voice to create a reusable voice profile.") }
    var lastToastMessage by remember { mutableStateOf(message) }
    val messageIsError = message.contains("failed", ignoreCase = true) ||
        message.contains("error", ignoreCase = true) ||
        message.contains("could not", ignoreCase = true) ||
        message.contains("not fully saved", ignoreCase = true) ||
        message.contains("required", ignoreCase = true) ||
        message.contains("must be", ignoreCase = true) ||
        message.contains("missing", ignoreCase = true) ||
        message.contains("invalid", ignoreCase = true)
    LaunchedEffect(message) {
        if (message != lastToastMessage) {
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
            lastToastMessage = message
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasMicPermission = granted; message = if (granted) "Microphone ready. Tap Start recording." else "Microphone permission is required." }
    var busy by remember { mutableStateOf(false) }
    var consentConfirmed by remember { mutableStateOf(false) }
    val voices = remember { mutableStateListOf<SavedUserVoice>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedUri = uri
        pickedLabel = if (uri == null) "No audio selected" else uri.lastPathSegment ?: "Audio sample selected"
    }
    fun loadVoices() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            voices.clear()
            message = "Sign in to load your saved Firebase voice profiles."
            return
        }
        com.t2sstudio.app.data.firebase.T2SFirestoreRepository().loadVoiceMetadata(uid) { result ->
            result.onSuccess { saved ->
                // Firebase stores cloud metadata, while the original audio remains local.
                // Merge by profile ID so refreshing cloud metadata never discards the
                // local recording path needed to replay the user's actual sample.
                val localProfiles = MyVoicesClient.list(context, uid)
                val localById = localProfiles.associateBy { it.id }
                val mergedCloud = saved.map { cloud ->
                    val local = localById[cloud.id]
                    if (local != null) cloud.copy(
                        localSamplePath = local.localSamplePath,
                        name = local.name.ifBlank { cloud.name },
                        language = local.language.ifBlank { cloud.language },
                        gender = local.gender.ifBlank { cloud.gender }
                    ) else cloud
                }
                val cloudIds = mergedCloud.map { it.id }.toSet()
                val localOnly = localProfiles.filter { it.id !in cloudIds }
                voices.clear()
                voices.addAll(mergedCloud + localOnly)
                if (voices.isEmpty()) message = "No voice profiles were found in your account."
            }.onFailure { error ->
                voices.clear()
                message = "Could not load voice profiles. Check your connection and try again."
            }
        }
    }

    LaunchedEffect(Unit) { loadVoices() }

    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null; runCatching { previewPlayer?.stop() }; runCatching { previewPlayer?.release() }; previewPlayer = null; runCatching { savedPreviewPlayer?.stop() }; runCatching { savedPreviewPlayer?.release() }; savedPreviewPlayer = null } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("My Voices", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
                Text("${voices.size} saved ${if (voices.size == 1) "voice" else "voices"}", fontSize = 13.sp, color = T2SSecondaryText)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(46.dp).background(T2SOrange.copy(alpha = 0.12f), RoundedCornerShape(50)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { loadVoices() }, enabled = !busy) {
                    Text("↻", color = T2SOrange, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        androidx.compose.material3.TabRow(selectedTabIndex = if (selectedAddTab == "Add Voice") 0 else 1) {
            listOf("Add Voice", "Add AI Voice").forEach { tab ->
                androidx.compose.material3.Tab(
                    selected = selectedAddTab == tab,
                    onClick = {
                        selectedAddTab = tab
                        showVoltSettings = false
                        showSarvamForm = false
                    },
                    text = { Text(tab, maxLines = 1, fontSize = 13.sp) },
                    enabled = !busy && !recording
                )
            }
        }

        if (selectedAddTab == "Add Voice" || selectedAddTab == "Add AI Voice") {
            if (showVoltSettings) {
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Voice server settings", fontWeight = FontWeight.SemiBold, color = T2SText)
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
                                    .onSuccess { message = "Voice server settings saved." }
                                    .onFailure { message = "Invalid voice server URL. Please enter a valid HTTPS URL." }
                            },
                            enabled = !checkingCloneServer
                        ) { Text("Save") }
                        TextButton(
                            onClick = {
                                checkingCloneServer = true
                                scope.launch {
                                    val result = runCatching { withContext(Dispatchers.IO) { VoiceCloneClient.checkServer(cloneServerUrl) } }
                                    message = result.fold({ it }, { "Could not connect to the voice server. Check the URL and connection, then try again." })
                                    checkingCloneServer = false
                                }
                            },
                            enabled = !checkingCloneServer && cloneServerUrl.isNotBlank()
                        ) {
                            if (checkingCloneServer) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Test connection")
                        }
                    }
                    TextButton(onClick = { showVoltSettings = false }) { Text("Done") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = T2SBackground), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Add a voice", fontWeight = FontWeight.SemiBold, color = T2SText)
                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Row {
                            Text("Voice Name")
                            Text(" *", color = androidx.compose.ui.graphics.Color.Red)
                        }
                    },
                    singleLine = true
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Voice Gender", color = T2SText, fontSize = 13.sp)
                    Text(" *", color = androidx.compose.ui.graphics.Color.Red, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female").forEach { gender ->
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Voice Language", color = T2SText, fontSize = 13.sp)
                    Text(" *", color = androidx.compose.ui.graphics.Color.Red, fontSize = 13.sp)
                }
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
                            ) {
                                Image(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_t2s_microphone),
                                    contentDescription = "T2S logo",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text("Record", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = T2SText, fontSize = 15.sp)
                            Text(if (recording) "● LIVE" else "READY", color = if (recording) androidx.compose.ui.graphics.Color(0xFFC62828) else T2SOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(T2SBackground, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                        androidx.compose.material3.HorizontalDivider(color = T2SOrange.copy(alpha = 0.18f))
                        Text("Speak for 10–30 sec in a quiet room.", color = T2SSecondaryText, fontSize = 12.sp)
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
                                }.onFailure { message = "Could not start recording. Check microphone permission and try again." }
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
                                    message = "Could not finish recording. Please record again for at least 5 seconds."
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
                        if (previewingPickedRecording) {
                            runCatching { previewPlayer?.stop() }
                            runCatching { previewPlayer?.release() }
                            previewPlayer = null
                            previewingPickedRecording = false
                            message = "Recording preview stopped."
                        } else {
                            val uri = pickedUri
                            if (uri == null) message = "Record or select an audio file first."
                            else runCatching {
                                runCatching { previewPlayer?.release() }
                                previewPlayer = null
                                previewingSavedVoiceId = null
                                val player = ExoPlayer.Builder(context).build()
                                previewPlayer = player
                                previewingPickedRecording = true
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
                                            previewingPickedRecording = false
                                            message = "Recording preview finished."
                                        }
                                    }
                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        player.release()
                                        if (previewPlayer === player) previewPlayer = null
                                        previewingPickedRecording = false
                                        message = "Recording preview failed. Please try again."
                                    }
                                })
                                player.setMediaItem(MediaItem.fromUri(uri))
                                player.prepare()
                                player.playWhenReady = true
                                message = "Playing your recording…"
                            }.onFailure {
                                previewingPickedRecording = false
                                message = "Preview failed. Please try again."
                            }
                        }
                    }, enabled = (pickedUri != null || previewingPickedRecording) && !recording) {
                        Text(if (previewingPickedRecording) "■ Stop recording" else "▶ Preview recording")
                    }
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
                        else if (voiceGender !in listOf("Male", "Female")) message = "Select Male or Female voice gender." 
                        else if (cloneLanguage !in listOf("en", "te", "hi")) message = "Select the voice sample language."
                        else {
                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val saved = MyVoicesClient.createLocal(context, accountUid, voiceName, uri, cloneLanguage, voiceGender)
                                        if (selectedAddTab == "Add AI Voice") {
                                            val signedInUid = accountUid.takeIf { it.isNotBlank() }
                                                ?: error("Sign in to T2S before creating an AI voice.")
                                            val sample = saved.localSamplePath?.let(::File)
                                                ?: error("Saved recording could not be read.")
                                            val sarvamId = SarvamVoiceCloneClient.createCentralVoice(sample, saved.name, saved.language, saved.gender)
                                            require(sarvamId.startsWith("svc-")) { "Sarvam did not return a valid voice ID." }
                                            MyVoicesClient.attachSarvamVoiceId(context, accountUid, saved.id, sarvamId)
                                            suspendCancellableCoroutine<Unit> { continuation ->
                                                com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveVoiceMetadata(
                                                    uid = signedInUid, voiceId = saved.id, name = saved.name,
                                                    gender = saved.gender, language = saved.language, sarvamVoiceId = sarvamId
                                                ) { cloudResult ->
                                                    if (continuation.isActive) cloudResult.fold(
                                                        onSuccess = { continuation.resume(Unit) },
                                                        onFailure = { continuation.resumeWithException(it) }
                                                    )
                                                }
                                            }
                                            MyVoicesClient.markFirebaseSynced(context, accountUid, saved.id)
                                            MyVoicesClient.list(context, accountUid).first { it.id == saved.id }
                                        } else saved
                                    }
                                }.onSuccess { saved ->
                                    loadVoices()
                                    voiceName = "My Voice"
                                    voiceGender = ""
                                    cloneLanguage = ""
                                    pickedUri = null
                                    pickedLabel = "No audio selected"
                                    consentConfirmed = false
                                    message = if (saved.sarvamVoiceId.isNotBlank()) "${saved.name} AI voice created and saved. Sarvam voice ID: ${saved.sarvamVoiceId}" else "${saved.name} saved as a local voice."
                                }.onFailure { error ->
                                    loadVoices()
                                    val details = error.message.orEmpty()
                                    message = when {
                                        details.contains("Total speech duration is too short", ignoreCase = true) ->
                                            "Recording too short. Please record at least 4 seconds and try again."
                                        details.contains("VALIDATION_FAILED", ignoreCase = true) ||
                                            details.contains("Audio validation failed", ignoreCase = true) ->
                                            "Audio validation failed. Please use a clear recording of at least 4 seconds."
                                        details.contains("HTTP 422", ignoreCase = true) ->
                                            "Voice sample was rejected. Please check the recording and try again."
                                        else -> "Could not create AI voice. Your local recording is saved; please try again."
                                    }
                                }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && !recording && pickedUri != null && consentConfirmed && voiceName.trim().length in 2..40 && voiceGender in listOf("Male", "Female") && cloneLanguage in listOf("en", "te", "hi")
                ) { Text(if (selectedAddTab == "Add AI Voice") "Create AI Voice" else "Save My Voice") }
            }
        }
        }
        if (busy) CircularProgressIndicator()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("My saved voices", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = T2SText)
            if (voices.any { it.firebaseSynced }) {
                Text("Registered cloud voices", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T2SOrange)
            }
        }
        val orderedVoices = voices.sortedBy { voice ->
            when {
                voice.firebaseSynced -> 0
                voice.sarvamVoiceId.isNotBlank() -> 1
                else -> 2
            }
        }
        orderedVoices.forEachIndexed { voiceIndex, voice ->
            val groupKey = when {
                voice.firebaseSynced -> 0
                voice.sarvamVoiceId.isNotBlank() -> 1
                else -> 2
            }
            val previousGroupKey = if (voiceIndex > 0) {
                val previous = orderedVoices[voiceIndex - 1]
                when {
                    previous.firebaseSynced -> 0
                    previous.sarvamVoiceId.isNotBlank() -> 1
                    else -> 2
                }
            } else -1
            if (groupKey != previousGroupKey && groupKey != 0) {
                Text(
                    when (groupKey) {
                        0 -> "Registered cloud voices"
                        1 -> "Cloud voices awaiting Firebase sync"
                        else -> "Local recordings — registration incomplete"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = T2SText,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (groupKey == 2) {
                    Text(
                        "These recordings are kept on this device. They are not registered cloud voices.",
                        fontSize = 12.sp,
                        color = T2SSecondaryText
                    )
                }
            }
            val sampleFileForStatus = voice.localSamplePath?.let(::File)
            val sampleAvailable = sampleFileForStatus != null &&
                sampleFileForStatus.isFile && sampleFileForStatus.canRead() && sampleFileForStatus.length() > 1024L
            val remotePreviewAvailable = voice.sarvamVoiceId.startsWith("svc-")
            val canPreviewVoice = sampleAvailable || remotePreviewAvailable
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
                        ) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_t2s_microphone),
                                contentDescription = "T2S microphone",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(voice.name, color = T2SText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${voice.gender} • ${voice.language.uppercase()}", color = T2SSecondaryText, fontSize = 12.sp)
                        }
                        Text(if (voice.firebaseSynced) "SAVED" else "PENDING", color = T2SOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(T2SCream, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
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
                                                MyVoicesClient.attachSarvamVoiceId(context, accountUid, voice.id, sarvamId)
                                                val uid = accountUid.takeIf { it.isNotBlank() }
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
                                                MyVoicesClient.markFirebaseSynced(context, accountUid, voice.id)
                                                MyVoicesClient.list(context, accountUid).first { it.id == voice.id }
                                            }
                                        }
                                        result.onSuccess { registered ->
                                            loadVoices()
                                            message = "${registered.name} Saved successfully. Sarvam voice ID: ${registered.sarvamVoiceId}"
                                        }.onFailure { error ->
                                            loadVoices()
                                            message = "Could not finish saving ${voice.name}. Please try again later. If a voice ID was created, you can retry syncing its metadata."
                                        }
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Registering…" else "Register existing voice with Sarvam") }
                    }
                    if (voice.sarvamVoiceId.isNotBlank() && !voice.firebaseSynced) {
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    val result = runCatching {
                                        val uid = accountUid.takeIf { it.isNotBlank() }
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
                                        MyVoicesClient.markFirebaseSynced(context, accountUid, voice.id)
                                    }
                                    result.onSuccess { loadVoices() }
                                    message = result.fold(
                                        { "${voice.name} Saved successfully. Sarvam voice ID: ${voice.sarvamVoiceId}" },
                                        { "Could not finish saving ${voice.name}. Please try again later. If a voice ID was created, you can retry syncing its metadata." }
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
                            if (voice.sarvamVoiceId.startsWith("svc-"))
                                "Cloud voice ready. You can generate speech with this voice; the original recording is not needed."
                            else
                                "Original recording is not available on this device. Restore the sample to preview or register this voice.",
                            color = T2SSecondaryText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            when {
                                sampleAvailable -> "Saved sample • play your original recording"
                                remotePreviewAvailable -> "Original recording unavailable • AI voice preview available"
                                else -> "No local recording or registered AI voice preview"
                            },
                            color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = canPreviewVoice,
                            onClick = {
                                if (previewingSavedVoiceId == voice.id) {
                                    runCatching { savedPreviewPlayer?.stop() }
                                    runCatching { savedPreviewPlayer?.release() }
                                    savedPreviewPlayer = null
                                    previewingSavedVoiceId = null
                                    message = "Voice preview stopped."
                                } else {
                                    runCatching { savedPreviewPlayer?.release() }
                                    savedPreviewPlayer = null
                                    previewingSavedVoiceId = voice.id
                                    scope.launch {
                                        var generatedPreview: File? = null
                                        try {
                                            val audioFile = if (sampleAvailable) {
                                                sampleFileForStatus!!
                                            } else {
                                                val languageCode = when (voice.language.lowercase().substringBefore('-')) {
                                                    "te", "telugu" -> "te"
                                                    "hi", "hindi" -> "hi"
                                                    else -> "en"
                                                }
                                                val sampleText = when (languageCode) {
                                                    "te" -> "నమస్కారం. ఇది మీ వాయిస్ ప్రివ్యూ నమూనా."
                                                    "hi" -> "नमस्ते। यह आपकी आवाज़ का नमूना है।"
                                                    else -> "Hello. This is a sample of your saved voice."
                                                }
                                                File(context.cacheDir, "my_voice_preview_" + voice.id + "_" + System.currentTimeMillis() + ".wav")
                                                    .also { output ->
                                                        generatedPreview = output
                                                        withContext(Dispatchers.IO) {
                                                            SarvamVoiceCloneClient.generateCentral(
                                                                voice.sarvamVoiceId, sampleText, languageCode, output
                                                            )
                                                        }
                                                    }
                                            }
                                            if (previewingSavedVoiceId != voice.id) {
                                                generatedPreview?.delete()
                                                return@launch
                                            }
                                            val player = ExoPlayer.Builder(context).build()
                                            savedPreviewPlayer = player
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
                                                        if (savedPreviewPlayer === player) savedPreviewPlayer = null
                                                        generatedPreview?.delete()
                                                        if (previewingSavedVoiceId == voice.id) previewingSavedVoiceId = null
                                                        message = "${voice.name} preview finished."
                                                    }
                                                }

                                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                                    player.release()
                                                    if (savedPreviewPlayer === player) savedPreviewPlayer = null
                                                    generatedPreview?.delete()
                                                    if (previewingSavedVoiceId == voice.id) previewingSavedVoiceId = null
                                                    message = "Voice preview failed: ${error.localizedMessage ?: "Please try again."}"
                                                }
                                            })
                                            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(audioFile)))
                                            player.prepare()
                                            player.playWhenReady = true
                                            message = if (sampleAvailable) {
                                                "Playing your original recording for \${voice.name}."
                                            } else {
                                                "Generating a preview with your saved AI voice."
                                            }
                                        } catch (error: Exception) {
                                            generatedPreview?.delete()
                                            runCatching { savedPreviewPlayer?.release() }
                                            savedPreviewPlayer = null
                                            previewingSavedVoiceId = null
                                            message = error.localizedMessage ?: "Could not preview this voice. Please try again."
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                when {
                                    previewingSavedVoiceId == voice.id -> "■ Stop preview"
                                    sampleAvailable -> "▶ Play my recording"
                                    remotePreviewAvailable -> "▶ Preview AI voice"
                                    else -> "Preview unavailable"
                                }
                            )
                        }
                    }
                        TextButton(onClick = {
                            if (previewingSavedVoiceId == voice.id) {
                                runCatching { savedPreviewPlayer?.release() }
                                savedPreviewPlayer = null
                                previewingSavedVoiceId = null
                            }
                            runCatching { MyVoicesClient.deleteLocal(context, accountUid, voice.id); loadVoices(); message = "Voice profile deleted from this device." }
                                .onFailure { message = "Could not delete the voice profile. Please try again." }
                        }) { Text("Delete") }
                    }
                }
            }
        }
        if (voices.any { it.sarvamVoiceId.isBlank() }) {
            Text("Local recordings are stored on this device as audio samples. Register a recording with a cloud provider to generate speech in that voice.", color = T2SSecondaryText, fontSize = 11.sp, lineHeight = 13.sp)
        }
        
    }
