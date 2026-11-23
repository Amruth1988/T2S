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
    var dailyCharacters by remember { mutableStateOf("10000") }
    var monthlyCharacters by remember { mutableStateOf("100000") }
    var dailyRequests by remember { mutableStateOf("100") }

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
                        val root = result.getData() as? Map<*, *> ?: emptyMap<Any, Any>()
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
            "The app encrypts keys with the backend public key before saving. Only the backend can decrypt them; Firebase never stores plaintext.",
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
            Text("Usage safety limits — ${provider.replaceFirstChar { it.uppercase() }}", color = T2SText, fontSize = 18.sp)
            Text("Set ceilings below the provider free/prepaid allowance. These are app-side limits, not a live wallet balance.", color = T2SSecondaryText, fontSize = 12.sp)
            OutlinedTextField(value = dailyCharacters, onValueChange = { dailyCharacters = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Maximum characters per day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(value = monthlyCharacters, onValueChange = { monthlyCharacters = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Maximum characters per month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(value = dailyRequests, onValueChange = { dailyRequests = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Maximum requests per day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Button(onClick = {
                val daily = dailyCharacters.toLongOrNull(); val monthly = monthlyCharacters.toLongOrNull(); val requests = dailyRequests.toLongOrNull()
                if (daily == null || monthly == null || requests == null || daily !in 1..Int.MAX_VALUE.toLong() || monthly !in 1..Int.MAX_VALUE.toLong() || requests !in 1..Int.MAX_VALUE.toLong()) { status = "Enter positive whole-number limits."; return@Button }
                loading = true; status = "Saving usage limits…"
                functions.getHttpsCallable("setProviderUsageLimits").call(mapOf("provider" to provider, "enabled" to true, "dailyCharacters" to daily.toInt(), "monthlyCharacters" to monthly.toInt(), "dailyRequests" to requests.toInt())).addOnSuccessListener { status = "Usage limits saved."; loading = false }.addOnFailureListener { status = it.localizedMessage ?: "Could not save usage limits."; loading = false }
            }, enabled = !loading && isAdmin == true, modifier = Modifier.fillMaxWidth()) { Text("Save limits and enable provider") }
            OutlinedButton(onClick = {
                loading = true
                functions.getHttpsCallable("setProviderUsageLimits").call(mapOf("provider" to provider, "enabled" to false, "dailyCharacters" to (dailyCharacters.toIntOrNull() ?: 0), "monthlyCharacters" to (monthlyCharacters.toIntOrNull() ?: 0), "dailyRequests" to (dailyRequests.toIntOrNull() ?: 0))).addOnSuccessListener { status = "Provider generation paused."; loading = false }.addOnFailureListener { status = it.localizedMessage ?: "Could not pause provider."; loading = false }
            }, enabled = !loading && isAdmin == true, modifier = Modifier.fillMaxWidth()) { Text("Pause provider generation") }
            Button(
                onClick = {
                    loading = true
                    status = "Encrypting and saving key…"
                    functions.getHttpsCallable("getProviderEncryptionPublicKey").call()
                        .addOnSuccessListener { publicKeyResult ->
                            val publicKey = (publicKeyResult.getData() as? Map<*, *>)?.get("publicKey")?.toString()
                            if (publicKey.isNullOrBlank()) {
                                status = "Could not obtain the encryption public key."
                                loading = false
                                return@addOnSuccessListener
                            }
                            val encryptedKey = runCatching { ProviderKeyEncryptor.encrypt(apiKey, publicKey) }
                                .getOrElse { status = it.message ?: "Could not encrypt key."; loading = false; return@addOnSuccessListener }
                            functions.getHttpsCallable("saveCentralProviderKey")
                                .call(mapOf("provider" to provider, "encryptedKey" to encryptedKey))
                                .addOnSuccessListener {
                            apiKey = ""
                            status = "Encrypted key saved in Firebase. Refreshing dates…"
                            refreshStatus()
                                }.addOnFailureListener {
                                    status = it.localizedMessage ?: "Could not save central key."
                                    loading = false
                                }
                        }.addOnFailureListener {
                            status = it.localizedMessage ?: "Could not get encryption public key."
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
