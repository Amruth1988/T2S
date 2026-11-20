package com.t2sstudio.app

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
fun AccountScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = remember { T2SAuthRepository() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showApiSettings by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        val syncRepository = com.t2sstudio.app.data.firebase.T2SFirestoreRepository()
        ProjectStore.load(context).forEach { project ->
            syncRepository.saveProject(
                uid = uid,
                projectId = project.id,
                name = project.name,
                createdAt = project.createdAt
            ) { /* Local data remains authoritative if sync is unavailable. */ }
        }
    }

    if (showApiSettings) {
        ApiKeySettingsScreen()
        OutlinedButton(onClick = { showApiSettings = false }, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Back to account")
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your T2S account", color = T2SText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Sign in to sync your project and script metadata across devices. Audio files and original voice recordings stay on this device.",
            color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 19.sp
        )

        if (user != null) {
            Text("Signed in", color = T2SOrange, fontWeight = FontWeight.Bold)
            Text(user?.email ?: user?.displayName ?: "T2S account", color = T2SText, fontSize = 16.sp)
            Button(
                onClick = { auth.signOut(); user = null; password = ""; status = "Signed out. Your local projects remain on this device." },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
            ) { Text("Sign out") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { isRegister = false; status = "" }, modifier = Modifier.weight(1f)) {
                    Text("Sign in")
                }
                OutlinedButton(onClick = { isRegister = true; status = "" }, modifier = Modifier.weight(1f)) {
                    Text("Create account")
                }
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 6) {
                        status = "Enter a valid email and a password with at least 6 characters."
                    } else {
                        busy = true
                        val callback: (Result<com.google.firebase.auth.FirebaseUser>) -> Unit = { result ->
                            busy = false
                            result.onSuccess { user = it; status = "Account ready. Metadata sync can now use this account." }
                                .onFailure { status = it.localizedMessage ?: "Authentication failed." }
                        }
                        if (isRegister) auth.registerWithEmail(email, password, callback)
                        else auth.signInWithEmail(email, password, callback)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
            ) { Text(if (busy) "Please wait…" else if (isRegister) "Create account" else "Sign in", fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick = {
                    if (email.isBlank()) {
                        status = "Enter your email address first to receive a password reset link."
                    } else {
                        busy = true
                        auth.sendPasswordReset(email) { result ->
                            busy = false
                            status = result.fold(
                                { "Password reset email sent. Check your inbox." },
                                { it.localizedMessage ?: "Could not send the reset email." }
                            )
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Forgot password?") }

            OutlinedButton(
                onClick = {
                    val webClientIdId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    if (activity == null || webClientIdId == 0) {
                        status = "Google sign-in needs the Web OAuth client ID configured in Firebase Console. Email sign-in is ready once Email/Password is enabled."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(context.getString(webClientIdId))
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleOption)
                                    .build()
                                val result = CredentialManager.create(context).getCredential(activity, request)
                                val credential = result.credential
                                if (credential is CustomCredential &&
                                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                                    val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
                                    auth.signInWithGoogleCredential(firebaseCredential) { signIn ->
                                        busy = false
                                        signIn.onSuccess { user = it; status = "Google account connected." }
                                            .onFailure { status = it.localizedMessage ?: "Google sign-in failed." }
                                    }
                                } else {
                                    busy = false
                                    status = "Google did not return a supported sign-in credential."
                                }
                            } catch (error: Exception) {
                                busy = false
                                status = error.localizedMessage ?: "Google sign-in was cancelled or unavailable."
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue with Google") }
        }

        if (status.isNotBlank()) {
            Text(status, color = T2SSecondaryText, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("You can continue using T2S offline without signing in. Local audio and projects are not deleted when you sign out.", color = T2SSecondaryText, fontSize = 12.sp, lineHeight = 17.sp)
        OutlinedButton(onClick = { showApiSettings = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Online TTS API settings")
        }
    }
}
