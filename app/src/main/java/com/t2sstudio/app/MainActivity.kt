package com.t2sstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SBorder
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import com.t2sstudio.app.ui.theme.T2SStudioTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            T2SStudioTheme {
                T2SApp()
            }
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector)
private data class StudioProject(val id: String, val name: String)

private val destinations = listOf(
    Destination("Home", Icons.Filled.Home),
    Destination("Projects", Icons.Filled.FolderOpen),
    Destination("Create", Icons.Filled.Add),
    Destination("Recent", Icons.Filled.AccessTime),
    Destination("Profile", Icons.Filled.Person)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun T2SApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedDestination by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }\n    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val audioRecords = remember {
        mutableStateListOf<AudioRecord>().apply { addAll(AudioHistoryStore.load(context)) }
    }
    val projects = remember {
        mutableStateListOf<StudioProject>().apply {
            val saved = context.getSharedPreferences("t2s_projects", android.content.Context.MODE_PRIVATE)
                .getString("items", "[]") ?: "[]"
            runCatching {
                val array = org.json.JSONArray(saved)
                for (index in 0 until array.length()) {
                    val name = array.optString(index).trim()
                    if (name.isNotEmpty()) add(StudioProject(name))
                }
            }
        }
    }

    fun createProject() {
        val name = projectName.trim()
        if (name.isNotEmpty()) {
            projects.add(0, StudioProject(name))
            val array = org.json.JSONArray()
            projects.forEach { array.put(it.name) }
            context.getSharedPreferences("t2s_projects", android.content.Context.MODE_PRIVATE)
                .edit().putString("items", array.toString()).apply()
            projectName = ""
            showCreateDialog = false
            selectedDestination = 1
        }
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
                        Column(verticalArrangement = Arrangement.Center) {
                            Text("T2S Studio", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = T2SText)
                            Text("Create. Convert. Experience.", fontSize = 10.sp, color = T2SSecondaryText)
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier.padding(end = 16.dp).size(40.dp).clip(CircleShape)
                            .background(T2SCream).clickable { selectedDestination = 4 },
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
                        onClick = {
                            selectedDestination = index
                        },
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
                0 -> HomeContent(
                    projectCount = projects.size,
                    onCreate = { projectName = ""; showCreateDialog = true },
                    onProjects = { selectedDestination = 1 },
                    onRecent = { selectedDestination = 3 }
                )
                1 -> ProjectsContent(
                    projects = projects,
                    audioRecords = audioRecords,
                    selectedProjectId = selectedProjectId,
                    onCreate = { projectName = ""; showCreateDialog = true },
                    onOpenProject = { project -> selectedProjectId = project.id; selectedDestination = 2 }
                )
                2 -> SpeechStudioScreen(
                    selectedProjectId = selectedProjectId,
                    selectedProjectName = projects.firstOrNull { it.id == selectedProjectId }?.name,
                    onAudioGenerated = { record ->
                    audioRecords.add(0, record)
                    AudioHistoryStore.save(context, audioRecords)
                })
                3 -> SavedAudioScreen(
                    records = audioRecords,
                    onDelete = { record ->
                        audioRecords.removeAll { it.id == record.id }
                        File(record.filePath).delete()
                        AudioHistoryStore.save(context, audioRecords)
                    }
                )
                4 -> SimplePage("Profile", "Your account and personal preferences will appear here.")
                else -> HomeContent(
                    projectCount = projects.size,
                    onCreate = { projectName = ""; showCreateDialog = true },
                    onProjects = { selectedDestination = 1 },
                    onRecent = { selectedDestination = 3 }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create a project", color = T2SText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { createProject() }, enabled = projectName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = T2SSecondaryText) } },
            containerColor = T2SBackground
        )
    }
}

@Composable
private fun HomeContent(
    projectCount: Int,
    onCreate: () -> Unit,
    onProjects: () -> Unit,
    onRecent: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Welcome to T2S Studio", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = T2SText)
            Text("Your creative workspace starts here.", fontSize = 14.sp, color = T2SSecondaryText)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = T2SCream)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What would you like to create?", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = T2SText)
                Text("Start a new project or continue where you left off.", fontSize = 13.sp, color = T2SSecondaryText)
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Project", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("Quick access", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = T2SText)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccessCard("My Projects", "$projectCount projects", Icons.Filled.FolderOpen, Modifier.weight(1f), onProjects)
            QuickAccessCard("Recent", "Continue working", Icons.Filled.AccessTime, Modifier.weight(1f), onRecent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccessCard("Templates", "Explore designs", Icons.Filled.Tune, Modifier.weight(1f)) { }
            QuickAccessCard("Settings", "Manage preferences", Icons.Filled.Person, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun ProjectsContent(
    projects: List<StudioProject>,
    audioRecords: List<AudioRecord>,
    selectedProjectId: String?,
    onCreate: () -> Unit,
    onOpenProject: (StudioProject) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("My Projects", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
        Text("Your projects are stored locally in this app for now.", fontSize = 13.sp, color = T2SSecondaryText)
        if (projects.isEmpty()) {
            EmptyState("No projects yet", "Create your first project to see it here.", onCreate)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects) { project ->
                    Card(colors = CardDefaults.cardColors(containerColor = T2SBackground),
                        modifier = Modifier.fillMaxWidth().border(1.dp, T2SBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = T2SOrange)
                            Spacer(Modifier.width(12.dp))
                            Text(project.name, color = T2SText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentContent(projects: List<StudioProject>) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Recent Projects", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
        if (projects.isEmpty()) {
            Text("Projects you create will appear here.", color = T2SSecondaryText, fontSize = 14.sp)
        } else {
            projects.take(5).forEach { project ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = T2SOrange)
                    Spacer(Modifier.width(12.dp))
                    Text(project.name, color = T2SText, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SimplePage(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
        Text(description, fontSize = 14.sp, color = T2SSecondaryText)
    }
}

@Composable
private fun EmptyState(title: String, description: String, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(42.dp))
        Text(title, color = T2SText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(description, color = T2SSecondaryText, fontSize = 13.sp)
        Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)) { Text("Create project") }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = T2SBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().border(1.dp, T2SBorder, RoundedCornerShape(17.dp)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(icon, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(25.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = T2SText)
            Text(subtitle, fontSize = 11.sp, color = T2SSecondaryText)
        }
    }
}
