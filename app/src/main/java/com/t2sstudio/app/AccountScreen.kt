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
        modifier = Modifier.fillMaxSize().background(T2SBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // Compact, premium brand hero. Keep clear of the system status bar.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().height(224.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(androidx.compose.ui.graphics.Color(0xFFFFE9C8), androidx.compose.ui.graphics.Color(0xFFFFF4E8))
                        )
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(76.dp)
                            .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(23.dp))
                            .border(2.dp, T2SOrange, androidx.compose.foundation.shape.RoundedCornerShape(23.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text("T2S", color = T2SOrange, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Studi", color = T2SText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text("🎙", fontSize = 28.sp)
                    }
                    Text("TEXT  •  CREATE  •  SPEAK", color = T2SSecondaryText,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White,
                    androidx.compose.foundation.shape.RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("Welcome to T2S Studio", color = T2SText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Sign in to sync your projects, scripts and settings across devices.",
                    color = T2SSecondaryText, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                if (user != null) {
                    Text("Signed in", color = T2SOrange, fontWeight = FontWeight.Bold)
                    Text(syncStatus, color = T2SSecondaryText, fontSize = 13.sp)
                    Text(user?.email ?: user?.displayName ?: "T2S account", color = T2SText, fontSize = 16.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)) { Text("Sign out", fontSize = 17.sp) }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFFF1EFED), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(4.dp)) {
                        Button(onClick = { isRegister = false; status = "" }, modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (!isRegister) T2SOrange else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (!isRegister) androidx.compose.ui.graphics.Color.White else T2SText),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text("Sign In", fontWeight = FontWeight.SemiBold) }
                        Button(onClick = { isRegister = true; status = "" }, modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRegister) T2SOrange else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (isRegister) androidx.compose.ui.graphics.Color.White else T2SText),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text("Create Account", fontWeight = FontWeight.SemiBold) }
                    }
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Your account stays securely signed in", color = T2SSecondaryText, fontSize = 12.sp)
                        androidx.compose.material3.TextButton(onClick = {
                            if (email.isBlank()) status = "Enter your email address first to receive a password reset link."
                            else { busy = true; auth.sendPasswordReset(email) { result ->
                                busy = false
                                status = result.fold({ "Password reset email sent. Check your inbox." }, { it.localizedMessage ?: "Could not send the reset email." })
                            } }
                        }, enabled = !busy) { Text("Forgot password?", color = T2SOrange, fontSize = 13.sp) }
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
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = T2SOrange),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)) {
                        Text(if (busy) "Please wait…" else if (isRegister) "Create Account" else "➜  Sign In", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = T2SBorder)
                        Text("OR", color = T2SSecondaryText, fontSize = 13.sp)
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
                                    // Do not force the stale authorised-account reauthentication path.
                                    // Request an explicit account chooser first, then clear stale
                                    // Credential Manager state and retry Google's dedicated sign-in.
                                    val freshAccountOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(clientId)
                                        .setAutoSelectEnabled(false)
                                        .build()
                                    val credential = try {
                                        credentialManager.getCredential(
                                            activity,
                                            GetCredentialRequest.Builder()
                                                .addCredentialOption(freshAccountOption)
                                                .build()
                                        ).credential
                                    } catch (firstError: androidx.credentials.exceptions.GetCredentialException) {
                                        try {
                                            credentialManager.clearCredentialState(
                                                androidx.credentials.ClearCredentialStateRequest()
                                            )
                                        } catch (_: Exception) {
                                            // Clearing is best-effort; continue with explicit Google sign-in.
                                        }
                                        val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(clientId)
                                            .build()
                                        try {
                                            credentialManager.getCredential(
                                                activity,
                                                GetCredentialRequest.Builder()
                                                    .addCredentialOption(signInOption)
                                                    .build()
                                            ).credential
                                        } catch (secondError: Exception) {
                                            throw IllegalStateException(
                                                "Google account selection failed. Please update Google Play services and try again. Details: ${secondError.localizedMessage ?: firstError.localizedMessage ?: "No credential returned."}",
                                                secondError
                                            )
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
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)) {
                        Text("🌐   Continue with Google", color = T2SText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (status.isNotBlank()) Text(status, color = T2SSecondaryText, fontSize = 12.sp, lineHeight = 17.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("☁" to "Sync", "▣" to "Projects", "⚙" to "Settings").forEach { (symbol, label) ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .background(androidx.compose.ui.graphics.Color(0xFFFFF4E8), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .padding(vertical = 10.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(symbol, color = T2SOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(label, color = T2SSecondaryText, fontSize = 10.sp)
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFFFFF4E8),
                    androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("You can also use T2S Studio without an account (offline mode).",
                        color = T2SText, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    androidx.compose.material3.TextButton(onClick = onBack) {
                        Text("Continue as Guest  ❯", color = T2SOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                androidx.compose.material3.TextButton(onClick = { showApiSettings = true }) {
                    Text("Online TTS API settings", color = T2SSecondaryText, fontSize = 12.sp)
                }
            }
        }
    }
}
