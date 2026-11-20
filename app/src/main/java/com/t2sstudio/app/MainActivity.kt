package com.t2sstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SOrangeDark
import com.t2sstudio.app.ui.theme.T2SBorder
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import com.t2sstudio.app.ui.theme.T2SStudioTheme
import java.io.File

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
    Destination("My", Icons.Filled.RecordVoiceOver),
    Destination("Drafts", Icons.Filled.Description),
    Destination("Profile", Icons.Filled.Person)
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
    var selectedDestination by remember { mutableIntStateOf(0) }
    val destinationBackStack = remember { mutableStateListOf<Int>() }
    var showAccountPage by remember { mutableStateOf(false) }
    var showLoginPage by remember {
        mutableStateOf(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null)
    }
    val audioRecords = remember {
        mutableStateListOf<AudioRecord>().apply { addAll(AudioHistoryStore.load(context)) }
    }
    val projects = remember {
        mutableStateListOf<T2SProject>().apply { addAll(ProjectStore.load(context)) }
    }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var pendingScriptText by remember { mutableStateOf<String?>(null) }
    val scriptDrafts = remember { mutableStateListOf<T2SScriptDraft>().apply { addAll(ScriptDraftStore.load(context)) } }

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
        LoginScreen(onBack = { showLoginPage = false })
        return
    }
    if (showAccountPage) {
        AccountPageScreen(
            onBack = { showAccountPage = false },
            onSignIn = { showLoginPage = true }
        )
        return
    }

    Scaffold(
        containerColor = T2SBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "T2S",
                                color = T2SOrangeDark,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                letterSpacing = (-1.1).sp,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = T2SOrange.copy(alpha = 0.42f),
                                        offset = Offset(1.2f, 2.0f),
                                        blurRadius = 2.8f
                                    )
                                )
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text("Studi", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
                                Box(
                                    modifier = Modifier.padding(start = 3.dp)
                                        .size(23.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = "Microphone",
                                        tint = T2SOrange,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
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
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = T2SOrange,
                            indicatorColor = T2SOrange,
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
                    onSaveDraft = { text, language, projectId, projectName ->
                        val draft = ScriptDraftStore.create(text, language, projectId, projectName)
                        scriptDrafts.add(0, draft)
                        ScriptDraftStore.save(context, scriptDrafts)
                        draft
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
                    onDelete = { draft -> scriptDrafts.removeAll { it.id == draft.id }; ScriptDraftStore.save(context, scriptDrafts) },
                    onCreateDraft = { selectedDestination = 0 }
                )
                5 -> ProfileScreen(onOpenLogin = { showLoginPage = true })
            }
        }
    }
}


@Composable
private fun T2SSplashScreen(onFinished: () -> Unit) {
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
    val ink = T2SText
    val muted = T2SSecondaryText
    val orange = T2SOrange

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFEFC), Color(0xFFFFF5EC))))
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
                    Text("🧑🏻‍💻", fontSize = 48.sp)
                    Text("THE STORY BEGINS", color = ink, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✍️", fontSize = 27.sp, modifier = Modifier.offset(y = penTilt.dp))
                    Box(
                        Modifier.width(116.dp).height(9.dp).clip(RoundedCornerShape(8.dp))
                            .background(orange)
                    )
                    Box(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFFFEBDD)).padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Aa  అక్షరం", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("WORDS ON PAPER", color = muted, fontSize = 8.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 5.dp))
                }
            }

            // Keep the moving characters inside the logo zone, never over the people.
            Box(
                modifier = Modifier.weight(1.28f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size((116f * logoScale).dp)
                            .shadow(16.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(orange, T2SOrangeDark)))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFFF3E9E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T2S", color = Color.White, fontSize = 38.sp,
                                fontWeight = FontWeight.Black)
                        }
                    }
                    Text("STUDIO", color = orange, fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp,
                        modifier = Modifier.padding(top = 10.dp))
                    Text("TEXT  •  CREATE  •  SPEAK", color = muted, fontSize = 9.sp,
                        letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 5.dp))
                }

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
                    Text(")))", color = orange, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFEBDD))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text("నమస్కారం  •  Hello", color = ink, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Text("WORDS COME ALIVE", color = muted, fontSize = 8.sp,
                        letterSpacing = 0.9.sp, modifier = Modifier.padding(top = 5.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧔🏻", fontSize = 51.sp, modifier = Modifier.offset(y = speakerBob.dp))
                    Text("YOUR VOICE", color = ink, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("From your words to a voice that speaks",
                    color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier.fillMaxWidth(0.76f).height(4.dp).clip(CircleShape)
                        .background(orange.copy(alpha = 0.16f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).height(4.dp)
                            .clip(CircleShape).background(orange)
                    )
                }
                Text("Opening your studio…", color = muted, fontSize = 10.sp)
            }
        }
    }
}
