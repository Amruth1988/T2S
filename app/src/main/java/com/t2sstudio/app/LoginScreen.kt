package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun LoginScreen(onBack: () -> Unit, onSignedIn: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(20.dp))
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "T2S Studio — Text to Speech",
            modifier = Modifier.fillMaxWidth().height(104.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(20.dp))
        Text("Welcome to T2S Studio", color = T2SOrange, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (createAccount) "Create your account" else "Sign in to continue", color = T2SText, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; message = null },
            modifier = Modifier.fillMaxWidth(), label = { Text("Email address") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; message = null },
            modifier = Modifier.fillMaxWidth(), label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val cleanEmail = email.trim()
                if (cleanEmail.isBlank() || password.length < 6) {
                    message = "Enter a valid email and a password with at least 6 characters."
                } else {
                    busy = true
                    message = null
                    val task = if (createAccount) auth.createUserWithEmailAndPassword(cleanEmail, password)
                    else auth.signInWithEmailAndPassword(cleanEmail, password)
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) onSignedIn()
                        else message = result.exception?.localizedMessage ?: "Authentication failed. Please try again."
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange)
        ) {
            if (busy) CircularProgressIndicator() else Text(if (createAccount) "Create account" else "Sign in")
        }
        if (!createAccount) {
            OutlinedButton(
                onClick = {
                    val cleanEmail = email.trim()
                    if (cleanEmail.isBlank()) message = "Enter your email address first."
                    else {
                        busy = true
                        auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener { result ->
                            busy = false
                            message = if (result.isSuccessful) "Password reset email sent."
                            else result.exception?.localizedMessage ?: "Could not send reset email."
                        }
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { Text("Forgot password?") }
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = T2SText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { createAccount = !createAccount; message = null },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text(if (createAccount) "Already have an account? Sign in" else "Create a new account") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
