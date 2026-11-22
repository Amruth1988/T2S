package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun AccountPageScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onNavigate: (Int) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) {
        UserProfileScreen(onBack = { showProfile = false })
        return
    }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(modifier = Modifier.size(76.dp), shape = CircleShape, color = T2SCream) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = T2SOrange, modifier = Modifier.padding(7.dp))
            }
            Text(user?.displayName ?: if (user == null) "Guest user" else "T2S Studio user",
                color = T2SText, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(user?.email ?: user?.phoneNumber ?: "Not signed in",
                color = T2SSecondaryText, fontSize = 16.sp)
            Text("Text to Speech Studio", color = T2SText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccountAction("Projects", Icons.Filled.Folder, Modifier.weight(1f)) { onNavigate(1) }
            QuickAccountAction("My Voices", Icons.Filled.RecordVoiceOver, Modifier.weight(1f)) { onNavigate(3) }
            QuickAccountAction("Recent", Icons.Filled.AccessTime, Modifier.weight(1f)) { onNavigate(2) }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = T2SCream,
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = T2SBackground) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = T2SOrange,
                        modifier = Modifier.padding(16.dp).size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Your voice, your creativity", color = T2SText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Create, manage and revisit your speech projects.",
                        color = T2SSecondaryText, fontSize = 14.sp)
                }
            }
        }

        AccountSectionCard(
            title = "Your information",
            subtitle = "Personal account details",
            icon = Icons.Filled.AccountCircle
        ) {
            AccountMenuRow("Profile", "Update your personal details", Icons.Filled.AccountCircle) { showProfile = true }
            AccountMenuRow("My Voices", "Manage your saved voice profiles", Icons.Filled.RecordVoiceOver) { onNavigate(3) }
            AccountMenuRow("My Drafts", "Continue your saved scripts", Icons.Filled.Description) { onNavigate(4) }
        }

        AccountSectionCard(
            title = "Your T2S workspace",
            subtitle = "Your creative tools and activity",
            icon = Icons.Filled.Folder
        ) {
            AccountMenuRow("Projects", "Manage your projects", Icons.Filled.Folder) { onNavigate(1) }
            AccountMenuRow("Sign out", "Sign out of your T2S account", Icons.Filled.Logout) { auth.signOut() }
            AccountMenuRow("My Voices", "Manage your saved voice profiles", Icons.Filled.RecordVoiceOver) { onNavigate(3) }
            AccountMenuRow("Recent audio", "Listen to your generated speech", Icons.Filled.AccessTime) { onNavigate(2) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (user == null) "Sign in / Create account" else "Account sign-in options", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun AccountSectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = T2SCream,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = T2SOrange, modifier = Modifier.padding(9.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = T2SText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = T2SSecondaryText, fontSize = 12.sp)
                }
            }
            androidx.compose.material3.HorizontalDivider(
                color = T2SBackground,
                thickness = 1.dp
            )
            content()
        }
    }
}

@Composable
private fun QuickAccountAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = androidx.compose.ui.graphics.Color.White,
        shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(27.dp))
            Text(label, color = T2SText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccountMenuRow(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = T2SText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = T2SSecondaryText, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = T2SSecondaryText)
    }
}
