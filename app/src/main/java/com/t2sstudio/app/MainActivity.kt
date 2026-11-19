package com.t2sstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
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

    Scaffold(
        containerColor = T2SBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(T2SOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T2S", color = T2SBackground, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Studi", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = T2SText)
                                Image(
                                    painter = painterResource(R.drawable.ic_t2s_mark),
                                    contentDescription = "Studio microphone",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Text("Text to Speech", fontSize = 10.sp, color = T2SSecondaryText, lineHeight = 11.sp)
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier.padding(end = 16.dp).size(40.dp).clip(CircleShape).background(T2SCream),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = T2SOrange, modifier = Modifier.size(27.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = T2SBackground)
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
                    onAudioGenerated = { record ->
                        audioRecords.add(0, record)
                        AudioHistoryStore.save(context, audioRecords)
                    }
                )
                1 -> SavedAudioScreen(
                    records = audioRecords,
                    onDelete = { record ->
                        audioRecords.removeAll { it.id == record.id }
                        File(record.filePath).delete()
                        AudioHistoryStore.save(context, audioRecords)
                    }
                )
                2 -> MyVoicesScreen()
                3 -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
                    Text(
                        "T2S Studio uses your Android device's built-in speech engine. No paid API or billing is required.",
                        fontSize = 14.sp,
                        color = T2SSecondaryText
                    )
                }
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
        animationSpec = tween(durationMillis = 7_600, easing = FastOutSlowInEasing),
        label = "story-to-voice"
    )
    val gold = Color(0xFFFFB21A)
    val cream = Color(0xFFFFF3D6)
    val dark = Color(0xFF120D24)
    val firstFlow = ((progress - 0.02f) / 0.46f).coerceIn(0f, 1f)
    val secondFlow = ((progress - 0.48f) / 0.46f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101A35), dark, Color(0xFF241333))))
            .padding(horizontal = 18.dp, vertical = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FROM WORDS TO VOICE", color = gold, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Every story deserves a voice", color = cream.copy(alpha = 0.78f),
                    fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }

            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val sceneWidth = maxWidth
                // The writer and speaker are drawn as people, not emoji or placeholder icons.
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val ink = Color(0xFFFFD18A)
                    val skin = Color(0xFFE7AD83)
                    val shirt = Color(0xFF5D6C9A)
                    val table = Color(0xFF9B613D)
                    // Writer: head, hair, body, arm, pen and desk.
                    drawCircle(skin, radius = 25f, center = androidx.compose.ui.geometry.Offset(w * 0.17f, h * 0.29f))
                    drawArc(Color(0xFF21182D), 180f, 180f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.17f - 27f, h * 0.29f - 27f),
                        size = androidx.compose.ui.geometry.Size(54f, 48f), style = androidx.compose.ui.graphics.drawscope.Stroke(9f))
                    drawRoundRect(shirt, topLeft = androidx.compose.ui.geometry.Offset(w * 0.17f - 34f, h * 0.39f),
                        size = androidx.compose.ui.geometry.Size(78f, 92f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f))
                    drawLine(skin, androidx.compose.ui.geometry.Offset(w * 0.20f, h * 0.48f),
                        androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.64f), 13f)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.64f),
                        androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.56f), 4f)
                    drawRoundRect(table, topLeft = androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.69f),
                        size = androidx.compose.ui.geometry.Size(w * 0.39f, 15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
                    drawRoundRect(Color(0xFFFFE9C2), topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.59f),
                        size = androidx.compose.ui.geometry.Size(74f, 45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                    // Speaker: head, shoulders and visible sound waves leaving his mouth.
                    drawRoundRect(Color(0xFF34445F), topLeft = androidx.compose.ui.geometry.Offset(w * 0.77f, h * 0.48f),
                        size = androidx.compose.ui.geometry.Size(w * 0.19f, h * 0.34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(35f))
                    drawCircle(skin, radius = 29f, center = androidx.compose.ui.geometry.Offset(w * 0.865f, h * 0.31f))
                    drawArc(Color(0xFF21182D), 180f, 180f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.865f - 30f, h * 0.31f - 30f),
                        size = androidx.compose.ui.geometry.Size(60f, 54f), style = androidx.compose.ui.graphics.drawscope.Stroke(10f))
                    drawCircle(Color(0xFF5A2630), radius = 4f,
                        center = androidx.compose.ui.geometry.Offset(w * 0.895f, h * 0.34f))
                    drawArc(gold.copy(alpha = 0.9f), -55f, 110f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.89f, h * 0.24f),
                        size = androidx.compose.ui.geometry.Size(42f, 55f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                    drawArc(gold.copy(alpha = 0.6f), -55f, 110f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.91f, h * 0.19f),
                        size = androidx.compose.ui.geometry.Size(60f, 68f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                }

                // Brand mark is the destination of the written letters and source of spoken letters.
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier.size(104.dp).shadow(20.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp)).background(Color(0xFF34213F))
                            .border(2.dp, gold, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("T2S", color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Black)
                    }
                    Text("STUDIO", color = gold, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp)
                }

                // Staggered characters travel from the paper to the logo, then from the logo to the speaker.
                listOf("A", "త", "S", "మ", "♪").forEachIndexed { index, letter ->
                    val stagger = index * 0.055f
                    val phase = (progress - stagger).coerceIn(0f, 1f)
                    val xFraction = if (phase < 0.5f) {
                        val t = (phase / 0.5f).coerceIn(0f, 1f)
                        0.27f + (0.50f - 0.27f) * t
                    } else {
                        val t = ((phase - 0.5f) / 0.5f).coerceIn(0f, 1f)
                        0.50f + (0.83f - 0.50f) * t
                    }
                    val visible = if (progress < 0.08f) 0.35f else 1f
                    Text(
                        letter, color = gold.copy(alpha = visible), fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset(x = sceneWidth * xFraction, y = (118 + (index % 2) * 26).dp)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("WRITE", color = cream.copy(alpha = 0.78f), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("CREATE", color = gold, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("SPEAK", color = cream.copy(alpha = 0.78f), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("T2S Studio", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Turn your text into natural voice", color = cream, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth(0.72f).height(4.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(gold))
                }
                Text("Preparing your studio…", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }
        }
    }
}
