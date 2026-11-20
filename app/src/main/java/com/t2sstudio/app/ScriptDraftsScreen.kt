package com.t2sstudio.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScriptDraftsScreen(drafts:List<T2SScriptDraft>,onOpen:(T2SScriptDraft)->Unit,onDelete:(T2SScriptDraft)->Unit) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Script Drafts",color=T2SText,fontSize=24.sp,fontWeight=FontWeight.Bold)
        Text("Drafts are stored separately from generated audio and remain on this device.",color=T2SSecondaryText,fontSize=13.sp)
        if(drafts.isEmpty()) Text("No drafts yet. Open Create, write a script, and tap Save draft.",color=T2SSecondaryText)
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(drafts,key={it.id}) { draft ->
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=T2SBackground)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text(draft.title,color=T2SText,fontWeight=FontWeight.SemiBold,maxLines=2)
                        Text("${draft.language} • ${draft.projectName} • ${SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date(draft.updatedAt))}",color=T2SSecondaryText,fontSize=11.sp)
                        Text(draft.content.take(180).replace('\n',' '),color=T2SSecondaryText,fontSize=12.sp,maxLines=2)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick={onOpen(draft)}) { Icon(Icons.Filled.Edit,contentDescription="Open draft",tint=T2SOrange) }
                            IconButton(onClick={onDelete(draft)}) { Icon(Icons.Filled.Delete,contentDescription="Delete draft",tint=T2SSecondaryText) }
                        }
                    }
                }
            }
        }
    }
}
