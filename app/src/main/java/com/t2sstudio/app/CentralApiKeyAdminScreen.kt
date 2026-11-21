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
import java.text.DateFormat
import java.util.Date

private data class CentralProviderStatus(
    val id: String,
    val name: String,
    val configured: Boolean,
    val active: Boolean,
    val createdAt: Long?,
    val updatedAt: Long?,
    val rotatedAt: Long?,
)

@Composable
fun CentralApiKeyAdminScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    val functions = remember { FirebaseFunctions.getInstance("asia-south1") }
    var isAdmin by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf("sarvam") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Checking administrator access…") }
    var providers by remember { mutableStateOf<List<CentralProviderStatus>>(emptyList()) }

    fun dateLabel(value: Long?): String =
        value?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
            ?: "—"

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
                status = "This account does not have administrator permission."
                loading = false
            } else {
                functions.getHttpsCallable("getCentralProviderStatus").call()
                    .addOnSuccessListener { result ->
                        val root = result.data as? Map<*, *> ?: emptyMap<Any, Any>()
                        val data = root["providers"] as? Map<*, *> ?: emptyMap<Any, Any>()
                        providers = listOf("sarvam", "vakyam", "india").mapNotNull { id ->
                            val item = data[id] as? Map<*, *> ?: return@mapNotNull null
                            CentralProviderStatus(
                                id = id,
                                name = item["providerName"]?.toString() ?: id,
                                configured = item["configured"] == true,
                                active = item["isActive"] == true,
                                createdAt = (item["createdAt"] as? Number)?.toLong(),
                                updatedAt = (item["updatedAt"] as? Number)?.toLong(),
                                rotatedAt = (item["lastRotatedAt"] as? Number)?.toLong(),
                            )
                        }
                        status = "Provider details refreshed."
                        loading = false
                    }.addOnFailureListener {
                        status = it.localizedMessage ?: "Could not load provider status."
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
        Text(
            "Keys are encrypted by Cloud KMS before being saved in Firebase. The Android app never downloads or displays the decrypted keys.",
            color = T2SSecondaryText, fontSize = 13.sp
        )
        Text(status, color = T2SOrange, fontSize = 13.sp)
        if (loading) CircularProgressIndicator(color = T2SOrange)
        if (isAdmin == true) {
            providers.forEach { item ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name + " — " + when {
                        !item.configured -> "Not configured"
                        item.active -> "Active"
                        else -> "Disabled"
                    }, color = T2SText, fontSize = 15.sp)
                    Text("Added: ${dateLabel(item.createdAt)}", color = T2SSecondaryText, fontSize = 11.sp)
                    Text("Updated: ${dateLabel(item.updatedAt)}", color = T2SSecondaryText, fontSize = 11.sp)
                    Text("Last rotated: ${dateLabel(item.rotatedAt)}", color = T2SSecondaryText, fontSize = 11.sp)
                    if (item.configured) {
                        OutlinedButton(
                            onClick = {
                                loading = true
                                functions.getHttpsCallable("setCentralProviderActive")
                                    .call(mapOf("provider" to item.id, "isActive" to !item.active))
                                    .addOnSuccessListener { status = "${item.name} status updated."; refreshStatus() }
                                    .addOnFailureListener { status = it.localizedMessage ?: "Could not update provider."; loading = false }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (item.active) "Disable ${item.name}" else "Enable ${item.name}") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sarvam", "vakyam", "india").forEach { id ->
                    OutlinedButton(onClick = { provider = id; apiKey = "" }, enabled = !loading) {
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
                    status = "Encrypting and saving key…"
                    functions.getHttpsCallable("saveCentralProviderKey")
                        .call(mapOf("provider" to provider, "apiKey" to apiKey.trim()))
                        .addOnSuccessListener {
                            apiKey = ""
                            status = "Encrypted key saved in Firebase. Refreshing dates…"
                            refreshStatus()
                        }.addOnFailureListener {
                            status = it.localizedMessage ?: "Could not save central key."
                            loading = false
                        }
                },
                enabled = apiKey.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save / rotate provider key") }
            OutlinedButton(onClick = { refreshStatus() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh provider status")
            }
        } else if (isAdmin == false) {
            Text(
                "Ask the T2S project owner to grant the admin custom claim to this Firebase account. The app cannot grant this permission itself.",
                color = T2SSecondaryText, fontSize = 13.sp
            )
        }
    }
}
