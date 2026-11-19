package com.t2sstudio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projects: List<T2SProject>,
    selectedProjectId: String?,
    onSelectProject: (String?) -> Unit,
    onCreateProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var newProjectName by remember { mutableStateOf("") }
    var editingProjectId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<T2SProject?>(null) }
    var message by remember { mutableStateOf("Projects keep related speech recordings together on this device.") }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Projects", color = T2SText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Create a project, select it before generating speech, and find its recordings in Recent.",
            color = T2SSecondaryText,
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newProjectName,
                onValueChange = { newProjectName = it },
                modifier = Modifier.weight(1f),
                label = { Text("New project name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = {
                    if (newProjectName.isBlank()) {
                        message = "Enter a project name first."
                    } else {
                        onCreateProject(newProjectName.trim())
                        newProjectName = ""
                        message = "Project created."
                    }
                },
                enabled = newProjectName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
            ) { Text("Add") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your projects", color = T2SText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("${projects.size} total", color = T2SSecondaryText, fontSize = 11.sp)
        }

        if (projects.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = T2SCream)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("No projects yet", color = T2SText, fontWeight = FontWeight.SemiBold)
                    Text("Create one above to organise your speech recordings.", color = T2SSecondaryText, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = if (project.id == selectedProjectId) T2SCream else T2SBackground)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editingProjectId == project.id) {
                                OutlinedTextField(
                                    value = editingName,
                                    onValueChange = { editingName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Project name") },
                                    singleLine = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (editingName.isBlank()) message = "Project name cannot be empty."
                                            else {
                                                onRenameProject(project.id, editingName.trim())
                                                editingProjectId = null
                                                message = "Project renamed."
                                            }
                                        },
                                        enabled = editingName.isNotBlank()
                                    ) { Text("Save") }
                                    TextButton(onClick = { editingProjectId = null }) { Text("Cancel") }
                                }
                            } else {
                                Text(project.name, color = T2SText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "Created " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(project.createdAt)),
                                    color = T2SSecondaryText,
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            onSelectProject(project.id)
                                            message = "${project.name} selected for new speech."
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (project.id == selectedProjectId) T2SOrange else T2SCream, contentColor = if (project.id == selectedProjectId) T2SBackground else T2SText)
                                    ) { Text(if (project.id == selectedProjectId) "Selected" else "Use project") }
                                    OutlinedButton(
                                        onClick = {
                                            editingProjectId = project.id
                                            editingName = project.name
                                        }
                                    ) { Text("Rename") }
                                    TextButton(onClick = { deleteTarget = project }) { Text("Delete", color = T2SSecondaryText) }
                                }
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = {
            onSelectProject(null)
            message = "New speech will be saved as Unassigned."
        }) { Text("Use Unassigned for new speech", color = T2SOrange) }
        Text(message, color = T2SSecondaryText, fontSize = 12.sp)
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete project?") },
            text = { Text("Delete \"${project.name}\"? Its saved audio will remain in Recent as Unassigned.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(project.id)
                    if (editingProjectId == project.id) editingProjectId = null
                    deleteTarget = null
                    message = "Project deleted. Its audio recordings were kept."
                }) { Text("Delete", color = T2SOrange) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
