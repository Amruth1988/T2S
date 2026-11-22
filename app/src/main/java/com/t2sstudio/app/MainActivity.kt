package com.t2sstudio.app

import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SStudioTheme
import java.io.File

private object T2SSplashMusic {
    private const val SAMPLE_RATE = 22_050
    private const val LOOP_SECONDS = 8
    private var track: AudioTrack? = null

    @Synchronized
    fun start() {
        if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) return
        stop()
        val totalSamples = SAMPLE_RATE * LOOP_SECONDS
        val pcm = ShortArray(totalSamples)
        // Original, locally synthesized gentle flute-like melody over warm piano-like chords.
        val melody = listOf(523.25, 659.25, 783.99, 659.25, 587.33, 698.46, 783.99, 659.25)
        val chordRoots = listOf(130.81, 164.81, 196.00, 146.83)
        for (i in 0 until totalSamples) {
            val time = i.toDouble() / SAMPLE_RATE
            val beat = (time / 1.0).toInt()
            val note = melody[beat % melody.size]
            val within = time % 1.0
            val attack = (within / 0.12).coerceIn(0.0, 1.0)
            val release = ((1.0 - within) / 0.22).coerceIn(0.0, 1.0)
            val envelope = attack * release
            val flute = sin(2.0 * PI * note * time) * 0.58 +
                sin(2.0 * PI * note * 2.0 * time) * 0.12 +
                sin(2.0 * PI * note * 3.0 * time) * 0.035
            val root = chordRoots[(beat / 2) % chordRoots.size]
            val chord = sin(2.0 * PI * root * time) * 0.16 +
                sin(2.0 * PI * root * 1.5 * time) * 0.07 +
                sin(2.0 * PI * root * 2.0 * time) * 0.04
            val fade = minOf((time / 0.8).coerceIn(0.0, 1.0), ((LOOP_SECONDS - time) / 0.8).coerceIn(0.0, 1.0))
            pcm[i] = ((flute * envelope * 0.12 + chord * 0.12) * fade * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audio.write(pcm, 0, pcm.size)
        audio.setLoopPoints(0, pcm.size, -1)
        audio.setVolume(0.22f)
        audio.play()
        track = audio
    }

    @Synchronized
    fun stop() {
        track?.let { audio ->
            try {
                if (audio.playState == AudioTrack.PLAYSTATE_PLAYING) audio.pause()
                audio.flush()
            } catch (_: IllegalStateException) { }
            audio.release()
        }
        track = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { T2SStudioTheme { T2SApp() } }
    }
}

private data class Destination(val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("Home", Icons.Filled.Home),
    Destination("Projects", Icons.Filled.Folder),
    Destination("Recent", Icons.Filled.AccessTime),
    Destination("My Voice", Icons.Filled.RecordVoiceOver),
    Destination("Drafts", Icons.Filled.Description)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun T2SApp() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        T2SSplashScreen(onFinished = { showSplash = false })
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLoginPage by remember {
        mutableStateOf(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null)
    }
    var selectedDestination by remember { mutableIntStateOf(0) }
    val destinationBackStack = remember { mutableStateListOf<Int>() }
    var showAccountPage by remember { mutableStateOf(false) }
    val audioRecords = remember {
        mutableStateListOf<AudioRecord>().apply { addAll(AudioHistoryStore.load(context)) }
    }
    val projects = remember {
        mutableStateListOf<T2SProject>().apply { addAll(ProjectStore.load(context)) }
    }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser != null) {
                showLoginPage = false
                showAccountPage = false
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    var pendingScriptText by remember { mutableStateOf<String?>(null) }
    val scriptDrafts = remember { mutableStateListOf<T2SScriptDraft>() }
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    // Reconcile local metadata with the signed-in account on every app launch/sign-in.
    // Audio bytes remain local; only project and script descriptors/content are uploaded.
    LaunchedEffect(currentUid) {
        val uid = currentUid ?: return@LaunchedEffect
        val cloud = com.t2sstudio.app.data.firebase.T2SFirestoreRepository()
        val localProjects = ProjectStore.load(context)
        val localRecordings = AudioHistoryStore.load(context)

        localProjects.forEach { project ->
            cloud.saveProject(uid, project.id, project.name, project.createdAt) { }
        }
        localRecordings.forEach { recording ->
            cloud.saveScriptMetadata(
                uid = uid,
                scriptId = recording.id,
                projectId = recording.projectId.takeIf { it.isNotBlank() },
                title = recording.textPreview.ifBlank { "Untitled script" },
                content = recording.scriptContent.take(180_000),
                language = recording.language
            ) { }
        }

        cloud.loadProjects(uid) { result ->
            result.onSuccess { cloudProjects ->
                val merged = (cloudProjects + ProjectStore.load(context))
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                projects.clear()
                projects.addAll(merged)
                ProjectStore.save(context, merged)
                if (selectedProjectId == null || merged.none { it.id == selectedProjectId }) {
                    selectedProjectId = merged.firstOrNull()?.id
                }
            }
        }
    }

    LaunchedEffect(currentUid, selectedDestination) {
        if (currentUid != null && selectedDestination == 4) {
            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().loadDrafts(currentUid) { result ->
                result.onSuccess { cloudDrafts ->
                    scriptDrafts.clear()
                    scriptDrafts.addAll(cloudDrafts)
                }
            }
        } else if (currentUid == null) {
            scriptDrafts.clear()
        }
    }

    BackHandler(enabled = showLoginPage || showAccountPage || destinationBackStack.isNotEmpty()) {
        if (showLoginPage) {
            showLoginPage = false
        } else if (showAccountPage) {
            showAccountPage = false
        } else if (destinationBackStack.isNotEmpty()) {
            selectedDestination = destinationBackStack.removeAt(destinationBackStack.lastIndex)
        }
    }

    if (showLoginPage) {
        LoginScreen(
            onBack = { showLoginPage = false },
            onSignedIn = {
                showLoginPage = false
                showAccountPage = false
            }
        )
        return
    }
    if (showAccountPage) {
        AccountPageScreen(
            onBack = { showAccountPage = false },
            onSignIn = { showLoginPage = true },
            onNavigate = { destination ->
                showAccountPage = false
                selectedDestination = destination
                destinationBackStack.clear()
            }
        )
        return
    }

    Scaffold(
        containerColor = T2SBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "T2S Studio — Text to Speech",
                        modifier = Modifier.width(190.dp).height(58.dp),
                        contentScale = ContentScale.Fit
                    )
                },
                actions = {
                    Box(
                        modifier = Modifier.padding(end = 16.dp).size(40.dp).clip(CircleShape).background(T2SCream)
                            .clickable { showAccountPage = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = T2SOrange, modifier = Modifier.size(27.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = T2SOrange)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedDestination == index,
                        onClick = {
                            if (selectedDestination != index) {
                                if (destinationBackStack.lastOrNull() != selectedDestination) {
                                    destinationBackStack.add(selectedDestination)
                                }
                                selectedDestination = index
                            }
                            showLoginPage = false
                        },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selectedDestination == index) T2SOrange else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.label,
                                    tint = if (selectedDestination == index) Color.White else T2SSecondaryText
                                )
                            }
                        },
                        label = { Text(destination.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = T2SOrange,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = T2SSecondaryText,
                            unselectedTextColor = T2SSecondaryText
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedDestination) {
                0 -> SpeechStudioScreen(
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    onSelectedProjectIdChange = { selectedProjectId = it },
                    onAudioGenerated = { record ->
                        audioRecords.add(0, record)
                        AudioHistoryStore.save(context, audioRecords)
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveScriptMetadata(
                                uid = uid,
                                scriptId = record.id,
                                projectId = record.projectId.takeIf { it.isNotBlank() },
                                title = record.textPreview.ifBlank { "Untitled script" },
                                content = record.scriptContent.take(180_000),
                                language = record.language
                            ) { /* Speech generation and local history do not depend on cloud sync. */ }
                        }
                    },
                    initialScriptText = pendingScriptText,
                    onInitialScriptTextConsumed = { pendingScriptText = null },
                    onSaveDraft = { text, language, projectId, projectName, onComplete ->
                        try {
                            val cleanText = text.trim()
                            require(cleanText.isNotEmpty()) { "Write some text before saving a draft." }

                            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                ?: throw IllegalStateException("Please sign in again before saving a draft to your Firebase account.")

                            val existing = scriptDrafts.firstOrNull { draft ->
                                draft.content.trim() == cleanText &&
                                    draft.language.equals(language, ignoreCase = true) &&
                                    draft.projectId == projectId
                            }
                            val draft = existing?.copy(
                                title = cleanText.lineSequence().firstOrNull().orEmpty()
                                    .take(64).ifBlank { "Untitled draft" },
                                content = cleanText,
                                language = language,
                                projectName = projectName,
                                updatedAt = System.currentTimeMillis()
                            ) ?: ScriptDraftStore.create(cleanText, language, projectId, projectName)

                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveDraftMetadata(
                                uid = user.uid,
                                draftId = draft.id,
                                projectId = projectId.takeIf { it.isNotBlank() },
                                title = draft.title,
                                content = draft.content,
                                language = draft.language
                            ) { cloudResult ->
                                cloudResult.onSuccess {
                                    val index = scriptDrafts.indexOfFirst { it.id == draft.id }
                                    if (index >= 0) scriptDrafts[index] = draft else scriptDrafts.add(0, draft)
                                    onComplete(Result.success(draft))
                                }.onFailure { cloudError ->
                                    onComplete(Result.failure(
                                        IllegalStateException(
                                            "Firebase did not save this draft (${cloudError.localizedMessage ?: cloudError.javaClass.simpleName}). Please check your connection and try again.",
                                            cloudError
                                        )
                                    ))
                                }
                            }
                        } catch (error: Exception) {
                            onComplete(Result.failure(error))
                        }
                    }
                )
                1 -> ProjectsScreen(
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    onSelectProject = { selectedProjectId = it },
                    onCreateProject = { name ->
                        val project = ProjectStore.create(name)
                        projects.add(0, project)
                        selectedProjectId = project.id
                        ProjectStore.save(context, projects)
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveProject(
                                uid = uid,
                                projectId = project.id,
                                name = project.name,
                                createdAt = project.createdAt
                            ) { /* Local project remains saved even if cloud sync is unavailable. */ }
                        }
                    },
                    onRenameProject = { id, name ->
                        val index = projects.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            projects[index] = projects[index].copy(name = name)
                            for (audioIndex in audioRecords.indices) {
                                val record = audioRecords[audioIndex]
                                if (record.projectId == id) audioRecords[audioIndex] = record.copy(projectName = name)
                            }
                            ProjectStore.save(context, projects)
                            AudioHistoryStore.save(context, audioRecords)
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                com.t2sstudio.app.data.firebase.T2SFirestoreRepository().saveProject(
                                    uid = uid, projectId = id, name = name
                                ) { /* Local rename remains saved if cloud sync is unavailable. */ }
                            }
                        }
                    },
                    onDeleteProject = { id ->
                        projects.removeAll { it.id == id }
                        if (selectedProjectId == id) selectedProjectId = null
                        for (audioIndex in audioRecords.indices) {
                            val record = audioRecords[audioIndex]
                            if (record.projectId == id) {
                                audioRecords[audioIndex] = record.copy(projectId = "", projectName = "Unassigned")
                            }
                        }
                        ProjectStore.save(context, projects)
                        AudioHistoryStore.save(context, audioRecords)
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().archiveProject(uid, id) {
                                /* Local delete is retained regardless of cloud availability. */
                            }
                        }
                    }
                )
                2 -> SavedAudioScreen(
                    records = audioRecords,
                    onDelete = { record ->
                        audioRecords.removeAll { it.id == record.id }
                        File(record.filePath).delete()
                        AudioHistoryStore.save(context, audioRecords)
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().archiveScript(uid, record.id) {
                                /* Removing a local recording never depends on cloud availability. */
                            }
                        }
                    },
                    onEditScript = { record ->
                        pendingScriptText = record.scriptContent.ifBlank { record.textPreview }
                        selectedDestination = 0
                    }
                )
                3 -> MyVoicesScreen()
                4 -> ScriptDraftsScreen(
                    drafts = scriptDrafts,
                    onOpen = { draft -> pendingScriptText = draft.content; selectedProjectId = draft.projectId.takeIf { it.isNotBlank() }; selectedDestination = 0 },
                    onDelete = { draft ->
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            com.t2sstudio.app.data.firebase.T2SFirestoreRepository().archiveScript(uid, draft.id) { result ->
                                result.onSuccess { scriptDrafts.removeAll { it.id == draft.id } }
                            }
                        }
                    },
                    onCreateDraft = { selectedDestination = 0 }
                )
            }
        }
    }
}


@Composable
private fun T2SSplashScreen(onFinished: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        T2SSplashMusic.start()
        onDispose { T2SSplashMusic.stop() }
    }
    LaunchedEffect(Unit) {
        delay(8_000)
        onFinished()
    }

    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 7_700, easing = FastOutSlowInEasing),
        label = "splash-progress"
    )
    val motion = rememberInfiniteTransition(label = "splash-motion")
    val logoScale by motion.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo-scale"
    )
    val penTilt by motion.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pen-tilt"
    )
    val speakerBob by motion.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "speaker-bob"
    )
    val ink = Color(0xFF3A1B0B)
    val muted = Color(0xFF7A3515)
    val orange = Color(0xFFFFB15C)
    val paper = Color(0xFFFFF7EF)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFF7A1A), Color(0xFFE95B0C))))
            .padding(horizontal = 26.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A single, connected opening scene keeps the writer and paper visually related.
            Row(
                modifier = Modifier.weight(0.88f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧑🏻", fontSize = 42.sp)
                        Box(
                            Modifier.width(48.dp).height(25.dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color(0xFFFF7417), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Text("THE STORY BEGINS", color = Color.White, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✍️", fontSize = 27.sp, modifier = Modifier.offset(y = penTilt.dp))
                    Box(
                        Modifier.width(116.dp).height(9.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                    )
                    Box(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(9.dp))
                            .background(paper).padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Aa  అక్షరం", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("WORDS ON PAPER", color = Color.White, fontSize = 8.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 5.dp))
                }
            }

            // Keep the moving characters inside the logo zone, never over the people.
            Box(
                modifier = Modifier.weight(1.28f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "T2S Studio — Text to Speech",
                    modifier = Modifier
                        .width((190f * logoScale).dp)
                        .height((76f * logoScale).dp),
                    contentScale = ContentScale.Fit
                )

                val glyphs = listOf("A", "త", "♪", "మ")
                glyphs.forEachIndexed { index, glyph ->
                    val drift = (progress * 18f)
                    val x = (-66f + index * 43f + if (index % 2 == 0) drift else -drift)
                    val y = if (index % 2 == 0) -48f else 48f
                    Text(
                        glyph,
                        color = orange.copy(alpha = if (progress > 0.92f) 0.12f else 0.68f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = x.dp, y = y.dp)
                    )
                }
            }

            // Words become speech: the speech bubble and speaker share one visual group.
            Row(
                modifier = Modifier.weight(0.88f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 5.dp)
                    ) {
                        listOf(12, 22, 30, 22, 12).forEach { barHeight ->
                            Box(
                                Modifier.width(3.dp).height(barHeight.dp)
                                    .clip(CircleShape).background(Color.White)
                            )
                        }
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFEBDD))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text("నమస్కారం  •  Hello", color = ink, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Text("WORDS COME ALIVE", color = Color.White, fontSize = 8.sp,
                        letterSpacing = 0.9.sp, modifier = Modifier.padding(top = 5.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧔🏻", fontSize = 51.sp, modifier = Modifier.offset(y = speakerBob.dp))
                    Text("YOUR VOICE", color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("From your words to a voice that speaks",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier.fillMaxWidth(0.76f).height(4.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.38f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).height(4.dp)
                            .clip(CircleShape).background(Color.White)
                    )
                }
                Text("Opening your studio…", color = Color(0xFFFFE3C9), fontSize = 10.sp)
            }
        }
    }
}
