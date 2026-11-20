package com.t2sstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
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
    Destination("Create", Icons.Filled.Add),
    Destination("Projects", Icons.Filled.Folder),
    Destination("Recent", Icons.Filled.AccessTime),
    Destination("My Voices", Icons.Filled.RecordVoiceOver),
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
    val audioRecords = remember {
        mutableStateListOf<AudioRecord>().apply { addAll(AudioHistoryStore.load(context)) }
    }
    val projects = remember {
        mutableStateListOf<T2SProject>().apply { addAll(ProjectStore.load(context)) }
    }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }

    Scaffold(
        containerColor = T2SBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T2S", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Studi", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
                                Image(
                                    painter = painterResource(R.drawable.ic_t2s_mark),
                                    contentDescription = "Studio microphone",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Text("Text to Speech", fontSize = 10.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 11.sp)
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier.padding(end = 16.dp).size(40.dp).clip(CircleShape).background(T2SCream)
                            .clickable { selectedDestination = 4 },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = T2SOrange, modifier = Modifier.size(27.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = T2SOrange)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = T2SBackground, tonalElevation = 3.dp) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedDestination == index,
                        onClick = { selectedDestination = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = T2SOrange,
                            selectedTextColor = T2SOrange,
                            indicatorColor = T2SCream,
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
                    }
                )
                2 -> SavedAudioScreen(
                    records = audioRecords,
                    onDelete = { record ->
                        audioRecords.removeAll { it.id == record.id }
                        File(record.filePath).delete()
                        AudioHistoryStore.save(context, audioRecords)
                    }
                )
                3 -> MyVoicesScreen()
                4 -> ApiKeySettingsScreen()
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
        label = "splash-story-flow"
    )
    val pulse = rememberInfiniteTransition(label = "splash-pulse")
    val logoPulse by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            tween(1_100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "logo-pulse"
    )
    val penTilt by pulse.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "writing-pen"
    )
    val gold = Color(0xFFFFB52E)
    val cream = Color(0xFFFFF4DE)
    val muted = Color(0xFFB9B5C9)
    val firstFlow = ((progress - 0.08f) / 0.40f).coerceIn(0f, 1f)
    val secondFlow = ((progress - 0.52f) / 0.40f).coerceIn(0f, 1f)

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF10182F), Color(0xFF171329), Color(0xFF25152E))))
            .padding(horizontal = 22.dp, vertical = 25.dp)
    ) {
        val sceneWidth = maxWidth
        val sceneHeight = maxHeight

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Scene 1: a seated writer works at a desk; the paper is the source of the letters.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text("🧑🏻‍💻", fontSize = 62.sp)
                    Text("🪑", fontSize = 25.sp, modifier = Modifier.offset(y = (-12).dp))
                    Text(
                        "THE STORY BEGINS",
                        color = cream,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "✍️",
                        fontSize = 30.sp,
                        modifier = Modifier.offset(y = penTilt.dp)
                    )
                    Box(
                        Modifier.padding(top = 3.dp).width(132.dp).height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF9A613C))
                    )
                    Box(
                        Modifier.padding(top = 4.dp).size(70.dp, 38.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFFFE8BD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa   అక్షరం", color = Color(0xFF513A35), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("WORDS ON PAPER", color = muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }

            // Scene 2: the words gather around the T2S Studio identity.
            Box(Modifier.weight(0.92f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(112.dp).shadow(24.dp, RoundedCornerShape(30.dp))
                            .clip(RoundedCornerShape(30.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF49304E), Color(0xFF24213F))))
                            .border(2.dp, gold.copy(alpha = 0.9f), RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size((96 * logoPulse).dp).clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.045f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T2S", color = Color.White, fontSize = 37.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Text(
                        "STUDIO",
                        color = gold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp,
                        modifier = Modifier.padding(top = 9.dp)
                    )
                    Text(
                        "TEXT  •  CREATE  •  SPEAK",
                        color = muted,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            // Scene 3: a speaker receives the words and gives them a voice.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧔🏻", fontSize = 61.sp)
                    Text("🗣️", fontSize = 24.sp, modifier = Modifier.offset(y = (-9).dp))
                    Text(
                        "YOUR VOICE",
                        color = cream,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(")))", color = gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text("నమస్కారం • Hello", color = cream, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("WORDS COME ALIVE", color = muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }

        // Individual Telugu, English and music glyphs travel paper -> logo -> speaker.
        val letters = listOf("A", "త", "S", "మ", "♪", "ఓ")
        letters.forEachIndexed { index, letter ->
            val firstStage = progress < 0.52f
            val t = if (firstStage) firstFlow else secondFlow
            val x = if (firstStage) {
                0.19f + 0.31f * t
            } else {
                0.50f + 0.34f * t
            }
            val y = if (firstStage) {
                0.20f + 0.29f * t + (index % 3) * 0.018f
            } else {
                0.50f + 0.29f * t + (index % 3) * 0.018f
            }
            val alpha = when {
                progress < 0.07f -> 0f
                progress in 0.49f..0.54f -> 0.15f
                else -> 1f
            }
            Text(
                letter,
                color = gold.copy(alpha = alpha),
                fontSize = (16 + (index % 3) * 3).sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(x = sceneWidth * x, y = sceneHeight * y)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("From your words to a voice that speaks", color = cream, fontSize = 12.sp)
            Box(
                Modifier.fillMaxWidth(0.68f).height(3.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(gold)
                )
            }
            Text("Opening your studio…", color = muted, fontSize = 10.sp)
        }
    }
}
