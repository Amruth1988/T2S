package com.t2sstudio.app

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import com.t2sstudio.app.ui.theme.T2SBorder
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.t2sstudio.app.data.firebase.T2SAuthRepository
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = remember { T2SAuthRepository() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf("Sign in to sync metadata. Audio remains local.") }
    var showApiSettings by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.DisposableEffect(auth) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            user = firebaseAuth.currentUser
            email = firebaseAuth.currentUser?.email.orEmpty()
        }
        val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    androidx.compose.runtime.LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        val syncRepository = com.t2sstudio.app.data.firebase.T2SFirestoreRepository()
        val projectsToSync = ProjectStore.load(context)
        val recordingsToSync = AudioHistoryStore.load(context)
        val total = projectsToSync.size + recordingsToSync.size
        if (total == 0) {
            syncStatus = "Account connected. No local project or script metadata to sync yet."
            return@LaunchedEffect
        }
        syncStatus = "Syncing $total metadata item(s)…"
        var completed = 0
        var failed = 0
        fun recordSyncResult(result: Result<Unit>) {
            completed++
            if (result.isFailure) failed++
            if (completed == total) {
                syncStatus = if (failed == 0) {
                    "Metadata sync complete: $total item(s). Audio files remain on this device."
                } else {
                    "$failed of $total metadata item(s) could not sync. Your local data is safe; check connection and Firestore rules."
                }
            }
        }
        projectsToSync.forEach { project ->
            syncRepository.saveProject(
                uid = uid,
                projectId = project.id,
                name = project.name,
                createdAt = project.createdAt,
                onComplete = ::recordSyncResult
            )
        }
        recordingsToSync.forEach { recording ->
            syncRepository.saveScriptMetadata(
                uid = uid,
                scriptId = recording.id,
                projectId = recording.projectId.takeIf { it.isNotBlank() },
                title = recording.textPreview.ifBlank { "Untitled script" },
                content = recording.scriptContent.take(180_000),
                language = recording.language,
                onComplete = ::recordSyncResult
            )
        }
    }

    if (showApiSettings) {
        ApiKeySettingsScreen()
        OutlinedButton(onClick = { showApiSettings = false }, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Back to account")
        }
        return
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color(0xFFFFE7C6), T2SBackground, androidx.compose.ui.graphics.Color(0xFFFFF8F0))
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(onClick = onBack) {
                    Text("‹  Back", color = T2SText, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.weight(1f))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                        androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .border(1.dp, T2SOrange.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text("T2S STUDIO", color = T2SOrange, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(42.dp)
                            .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            "T2S",
                            color = com.t2sstudio.app.ui.theme.T2SOrangeDark,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = (-1.1).sp,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = T2SOrange.copy(alpha = 0.42f),
                                    offset = androidx.compose.ui.geometry.Offset(1.2f, 2.0f),
                                    blurRadius = 2.8f
                                )
                            )
                        )
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            "Studi",
                            color = T2SOrange,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.padding(start = 3.dp)
                                .size(23.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(androidx.compose.ui.graphics.Color.White),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Mic,
                                contentDescription = "Microphone",
                                tint = T2SOrange,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("WRITE", "CREATE", "SPEAK").forEach { word ->
                        Text(
                            word,
                            color = T2SOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier
                                .background(T2SOrange.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.Start
                ) {
                    if (user != null) {
                        Text("You're connected", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(user?.email ?: user?.displayName ?: "T2S account", color = T2SSecondaryText, fontSize = 14.sp)
                        Text(syncStatus, color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 19.sp)
                        Button(onClick = {
                            busy = true
                            scope.launch {
                                runCatching {
                                    CredentialManager.create(context).clearCredentialState(
                                        androidx.credentials.ClearCredentialStateRequest()
                                    )
                                }
                                auth.signOut()
                                user = null
                                password = ""
                                busy = false
                                status = "Signed out. Your local projects remain on this device."
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                            Text(if (busy) "Please wait…" else "Sign out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(if (isRegister) "Create your account" else "Welcome back",
                            color = T2SText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (isRegister) "Join T2S Studio and keep your work together." else "Sign in to sync your projects, scripts and settings.",
                            color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 19.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFFFFF2E5),
                                androidx.compose.foundation.shape.RoundedCornerShape(15.dp)).padding(4.dp)
                        ) {
                            listOf(false to "Sign In", true to "Create Account").forEach { (register, label) ->
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.weight(1f).background(
                                        if (isRegister == register) T2SOrange else androidx.compose.ui.graphics.Color.Transparent,
                                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                    ).then(Modifier)
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { isRegister = register; status = "" },
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Text(label, color = if (isRegister == register) androidx.compose.ui.graphics.Color.White else T2SText,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("Email address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            androidx.compose.material3.TextButton(onClick = {
                                if (email.isBlank()) status = "Enter your email address first to receive a password reset link."
                                else { busy = true; auth.sendPasswordReset(email) { result ->
                                    busy = false
                                    status = result.fold({ "Password reset email sent. Check your inbox." }, { it.localizedMessage ?: "Could not send the reset email." })
                                } }
                            }, enabled = !busy) { Text("Forgot password?", color = T2SOrange, fontWeight = FontWeight.SemiBold) }
                        }
                        Button(onClick = {
                            if (email.isBlank() || password.length < 6) status = "Enter a valid email and a password with at least 6 characters."
                            else {
                                busy = true
                                val callback: (Result<com.google.firebase.auth.FirebaseUser>) -> Unit = { result ->
                                    busy = false
                                    result.onSuccess { user = it; status = "Account ready. Metadata sync can now use this account." }
                                        .onFailure { status = it.localizedMessage ?: "Authentication failed." }
                                }
                                if (isRegister) auth.registerWithEmail(email, password, callback) else auth.signInWithEmail(email, password, callback)
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                            Text(if (busy) "Please wait…" else if (isRegister) "Create Account  →" else "Sign In  →",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = T2SBorder)
                            Text("OR CONTINUE WITH", color = T2SSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = T2SBorder)
                        }
                        OutlinedButton(onClick = {
                            val webClientIdId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                            if (activity == null || webClientIdId == 0) status = "Google sign-in needs the Web OAuth client ID configured in Firebase Console."
                            else {
                                busy = true
                                scope.launch {
                                    try {
                                        val clientId = context.getString(webClientIdId)
                                        val credentialManager = CredentialManager.create(context)
                                        val freshAccountOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(clientId)
                                            .setAutoSelectEnabled(false)
                                            .build()
                                        val credential = try {
                                            credentialManager.getCredential(activity,
                                                GetCredentialRequest.Builder().addCredentialOption(freshAccountOption).build()).credential
                                        } catch (firstError: androidx.credentials.exceptions.GetCredentialException) {
                                            try { credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
                                            catch (_: Exception) { }
                                            val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(clientId).build()
                                            try {
                                                credentialManager.getCredential(activity,
                                                    GetCredentialRequest.Builder().addCredentialOption(signInOption).build()).credential
                                            } catch (secondError: Exception) {
                                                throw IllegalStateException("Google account selection failed. Please update Google Play services and try again. Details: ${secondError.localizedMessage ?: firstError.localizedMessage ?: "No credential returned."}", secondError)
                                            }
                                        }
                                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            val token = GoogleIdTokenCredential.createFrom(credential.data)
                                            auth.signInWithGoogleCredential(GoogleAuthProvider.getCredential(token.idToken, null)) { result ->
                                                busy = false
                                                result.onSuccess { user = it; status = "Google account connected." }
                                                    .onFailure { status = it.localizedMessage ?: "Google sign-in failed." }
                                            }
                                        } else { busy = false; status = "Google did not return a supported sign-in credential." }
                                    } catch (error: Exception) { busy = false; status = error.localizedMessage ?: "Google sign-in was cancelled or unavailable." }
                                }
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = T2SText)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("G", color = androidx.compose.ui.graphics.Color(0xFF4285F4), fontSize = 19.sp, fontWeight = FontWeight.Black)
                                Text("  Continue with Google", color = T2SText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (status.isNotBlank()) {
                        Text(status, color = if (status.contains("failed", true) || status.contains("could not", true))
                            androidx.compose.ui.graphics.Color(0xFFB42318) else T2SSecondaryText,
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f),
                    androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .border(1.dp, T2SOrange.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .padding(18.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Your studio, wherever you go", color = T2SText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("☁ Sync" to "Projects", "✍ Create" to "Scripts", "🎧 Listen" to "Audio stays local").forEach { (top, bottom) ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(top, color = T2SOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(bottom, color = T2SSecondaryText, fontSize = 10.sp)
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = onBack) {
                    Text("Continue as Guest  →", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
