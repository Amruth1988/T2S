package com.t2sstudio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun CentralApiKeyAdminScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    val functions = remember { FirebaseFunctions.getInstance("asia-south1") }
    var isAdmin by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf("sarvam") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Checking administrator access…") }
    var configured by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    fun refreshStatus() {
        val user = auth.currentUser
        if (user == null) {
            isAdmin = false
            status = "Sign in with the T2S administrator account."
            return
        }
        loading = true
        user.getIdToken(true).addOnSuccessListener { token ->
            isAdmin = token.claims["admin"] == true
            if (isAdmin != true) {
                status = "This account does not have the administrator permission."
                loading = false
            } else {
                functions.getHttpsCallable("getCentralProviderStatus").call()
                    .addOnSuccessListener { result ->
                        val data = result.data as? Map<*, *> ?: emptyMap<Any, Any>()
                        configured = data.entries.associate { it.key.toString() to (it.value == true) }
                        status = "Central provider status refreshed."
                        loading = false
                    }.addOnFailureListener {
                        status = it.localizedMessage ?: "Could not load central provider status."
                        loading = false
                    }
            }
        }.addOnFailureListener {
            isAdmin = false
            status = it.localizedMessage ?: "Could not verify administrator access."
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Central API keys", color = T2SText, fontSize = 24.sp)
        Text("Administrator-only setup. Keys are sent to the secured backend and are never returned to this app.", color = T2SSecondaryText, fontSize = 13.sp)
        Text(status, color = T2SOrange, fontSize = 13.sp)
        if (loading) CircularProgressIndicator(color = T2SOrange)
        if (isAdmin == true) {
            listOf("sarvam", "vakyam", "india").forEach { id ->
                Text("${id.replaceFirstChar { it.uppercase() }}: " + if (configured[id] == true) "Configured" else "Not configured",
                    color = T2SText, fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sarvam", "vakyam", "india").forEach { id ->
                    OutlinedButton(onClick = { provider = id; apiKey = "" }) {
                        Text(id.replaceFirstChar { it.uppercase() })
                    }
                }
            }
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${provider.replaceFirstChar { it.uppercase() }} API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = {
                    loading = true
                    status = "Saving key securely…"
                    functions.getHttpsCallable("saveCentralProviderKey")
                        .call(mapOf("provider" to provider, "apiKey" to apiKey.trim()))
                        .addOnSuccessListener {
                            apiKey = ""
                            status = "Key saved in Secret Manager. Refreshing status…"
                            refreshStatus()
                        }.addOnFailureListener {
                            status = it.localizedMessage ?: "Could not save central key."
                            loading = false
                        }
                },
                enabled = apiKey.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save central key") }
            OutlinedButton(onClick = { refreshStatus() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh provider status")
            }
        } else if (isAdmin == false) {
            Text("Ask the T2S project owner to grant the admin custom claim to this Firebase account. The app cannot grant this permission itself.",
                color = T2SSecondaryText, fontSize = 13.sp)
        }
    }
}
