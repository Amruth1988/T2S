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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun AccountPageScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    var showOnlineTtsSettings by remember { mutableStateOf(false) }
    if (showOnlineTtsSettings) {
        Column(modifier = Modifier.fillMaxSize().background(T2SBackground)) {
            ApiKeySettingsScreen()
            androidx.compose.material3.OutlinedButton(
                onClick = { showOnlineTtsSettings = false },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
            ) { Text("Back to Account", color = T2SText) }
        }
        return
    }
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SText)
            }
            Text("Account", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = T2SCream,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Account avatar",
                    tint = T2SOrange, modifier = Modifier.size(76.dp))
                Text(user?.displayName ?: if (user == null) "Guest account" else "T2S Studio user",
                    color = T2SText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(user?.email ?: user?.phoneNumber ?: "Not signed in",
                    color = T2SSecondaryText, fontSize = 14.sp)
                Text(
                    if (user == null) "Sign in to sync your projects and script metadata across devices."
                    else "Your T2S account is connected. Your audio files and original voice recordings remain on this device.",
                    color = T2SSecondaryText, fontSize = 14.sp
                )
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
                ) {
                    Text(if (user == null) "Sign in / Create account" else "Account sign-in options")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { showOnlineTtsSettings = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Online TTS access", color = T2SText)
                }
            }
        }
    }
}
