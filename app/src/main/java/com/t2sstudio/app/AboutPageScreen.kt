package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun AboutPageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = onBack, shape = CircleShape, color = T2SCream) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SOrange, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Text("About T2S Studio", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = androidx.compose.ui.graphics.Color.White, shadowElevation = 2.dp) {
            Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(22.dp), color = T2SCream) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = "T2S Studio", tint = T2SOrange, modifier = Modifier.padding(20.dp).size(48.dp))
                }
                Text("T2S Studio", color = T2SText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("Text to Speech Studio", color = T2SOrange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Turn your words into speech. Create, organize and revisit your spoken content in one simple Android studio.", color = T2SSecondaryText, fontSize = 14.sp)
            }
        }
        AboutFeature(Icons.Filled.Language, "Create speech", "Generate speech in supported languages including English, Hindi and Telugu using the online speech service.")
        AboutFeature(Icons.Filled.RecordVoiceOver, "Voice profiles", "Manage your saved voice profiles and choose voices for your speech.")
        AboutFeature(Icons.Filled.Folder, "Projects and drafts", "Organize your work into projects, save scripts as drafts and return to them whenever you need.")
        AboutFeature(Icons.Filled.Smartphone, "Your audio on your device", "Generated MP3, WAV or OGG audio is saved on your device for playback, sharing and download.")
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = T2SCream) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Designed for simple creation", color = T2SText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("T2S Studio brings speech creation, voice management and script organization together in one place. Speech generation requires an internet connection and depends on the availability of the online service.", color = T2SSecondaryText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun AboutFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color.White, shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = T2SCream) {
                Icon(icon, contentDescription = null, tint = T2SOrange, modifier = Modifier.padding(11.dp).size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = T2SText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(description, color = T2SSecondaryText, fontSize = 13.sp)
            }
        }
    }
}
