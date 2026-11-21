package com.t2sstudio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun ApiKeySettingsScreen() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(IndiaApiKeyStore.get(context) != null) }
    var status by remember { mutableStateOf("") }
    var vakyamKey by remember { mutableStateOf("") }
    var vakyamConfigured by remember { mutableStateOf(VakyamApiKeyStore.get(context) != null) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Online TTS access", color = T2SText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Optional free India API key. Online Piper speech generation will require a key from 15 December 2026. The provider documents up to 500 model requests per day per free key. Android and imported offline voices continue working without one.",
            color = T2SSecondaryText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Text(
            if (configured) "A key is encrypted on this device; the provider checks it when you generate speech." else "No API key saved. Keyless access may work until 15 December 2026.",
            color = if (configured) T2SOrange else T2SSecondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("India API key") },
            placeholder = { Text("india_live_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp)
        )
        Text(
            "The key is encrypted with Android Keystore on this device. It is not included in the APK, source code, or app logs. Never share your key.",
            color = T2SSecondaryText,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Button(
            onClick = {
                runCatching { IndiaApiKeyStore.save(context, apiKey) }
                    .onSuccess {
                        configured = true
                        apiKey = ""
                        status = "Free API key saved securely."
                    }
                    .onFailure { status = it.message ?: "Could not save the API key." }
            },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
        ) { Text("Save key", fontWeight = FontWeight.Bold) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    IndiaApiKeyStore.clear(context)
                    configured = false
                    apiKey = ""
                    status = "Saved API key removed from this device."
                },
                enabled = configured,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = T2SBackground, contentColor = T2SText)
            ) { Text("Remove key") }
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.dharun.dev/keys"))) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
            ) { Text("How to get a key") }
        }
        Text("Vakyam Raaga — additional online voices", color = T2SText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Optional provider. Its Free plan currently includes 10,000 characters per month (about 13 minutes), with 16 published voices available across Telugu, Hindi and Indian English. Usage is subject to the provider's limits; no paid upgrade is required to use the free allowance.",
            color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 19.sp
        )
        Text(
            if (vakyamConfigured) "Vakyam key is encrypted on this device." else "Vakyam is not connected. Add your own free-plan key to load its voice catalogue.",
            color = if (vakyamConfigured) T2SOrange else T2SSecondaryText,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = vakyamKey,
            onValueChange = { vakyamKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Vakyam API key") },
            placeholder = { Text("vak_live_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp)
        )
        Button(
            onClick = {
                runCatching { VakyamApiKeyStore.save(context, vakyamKey) }
                    .onSuccess { vakyamConfigured = true; vakyamKey = ""; status = "Vakyam key saved securely. Reopen Voice Search to load its voices." }
                    .onFailure { status = it.message ?: "Could not save the Vakyam key." }
            },
            enabled = vakyamKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
        ) { Text("Save Vakyam key", fontWeight = FontWeight.Bold) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { VakyamApiKeyStore.clear(context); vakyamConfigured = false; vakyamKey = ""; status = "Vakyam key removed from this device." },
                enabled = vakyamConfigured,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = T2SBackground, contentColor = T2SText)
            ) { Text("Remove Vakyam key") }
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dashboard.vakyam.ai/api-keys"))) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
            ) { Text("Get free key") }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(status, color = T2SSecondaryText, fontSize = 12.sp)
        }
    }
}
