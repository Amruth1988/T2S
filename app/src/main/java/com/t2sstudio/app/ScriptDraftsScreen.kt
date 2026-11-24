package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScriptDraftsScreen(
    drafts: List<T2SScriptDraft>,
    onOpen: (T2SScriptDraft) -> Unit,
    onDelete: (T2SScriptDraft) -> Unit,
    onCreateDraft: () -> Unit,
    onBack: () -> Unit = {}
) {
    var draftPendingDeletion by remember { mutableStateOf<T2SScriptDraft?>(null) }

    draftPendingDeletion?.let { draft ->
        AlertDialog(
            onDismissRequest = { draftPendingDeletion = null },
            title = { Text("Delete draft?", color = T2SText, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${draft.title}\"? This action cannot be undone.", color = T2SSecondaryText) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(draft)
                    draftPendingDeletion = null
                }) {
                    Text("Delete", color = T2SOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { draftPendingDeletion = null }) {
                    Text("Cancel", color = T2SSecondaryText)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = T2SText,
            textContentColor = T2SSecondaryText
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("My drafts", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T2SText)
                Text("${drafts.size} saved ${if (drafts.size == 1) "draft" else "drafts"}", fontSize = 13.sp, color = T2SSecondaryText)
            }
            Box(
                modifier = Modifier.size(46.dp).background(T2SOrange.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(24.dp))
            }
        }

        if (drafts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(82.dp).background(T2SOrange.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null,
                                tint = T2SOrange, modifier = Modifier.size(40.dp))
                        }
                        Text("Your drafts will live here", color = T2SText, fontSize = 19.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            "Save a script while creating it, then return anytime to edit or continue.",
                            color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onCreateDraft,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)
                        ) {
                            Icon(Icons.Filled.NoteAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create a script", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        Text("Drafts sync to Firebase when you are signed in", color = T2SSecondaryText, fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 20.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp), clip = false),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(draft.title, color = T2SText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
                            Text(draft.content.take(180).replace('\n', ' '), color = T2SSecondaryText,
                                fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${draft.language} • ${draft.projectName} • ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(draft.updatedAt))}",
                                    modifier = Modifier.weight(1f),
                                    color = T2SSecondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                                IconButton(onClick = { onOpen(draft) }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Continue editing draft",
                                        tint = T2SOrange,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                IconButton(onClick = { draftPendingDeletion = draft }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete draft", tint = T2SSecondaryText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
