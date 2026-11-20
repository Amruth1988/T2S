package com.t2sstudio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun ProfileScreen(onOpenLogin: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profile", color = T2SText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (user == null) {
            Text("You are using T2S offline. Sign in to sync project and script metadata across devices. Audio files and original voice recordings remain on this device.", color = T2SSecondaryText, fontSize = 14.sp)
            Button(
                onClick = onOpenLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
            ) { Text("Sign in to T2S") }
        } else {
            Text("Signed in", color = T2SOrange, fontWeight = FontWeight.Bold)
            Text(user?.email ?: user?.displayName ?: "T2S account", color = T2SText, fontSize = 16.sp)
            OutlinedButton(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Account and sync settings")
            }
        }
    }
}
