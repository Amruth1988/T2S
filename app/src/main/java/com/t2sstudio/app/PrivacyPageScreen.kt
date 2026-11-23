package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun PrivacyPageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = onBack, shape = CircleShape, color = T2SCream) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SOrange, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Text("Privacy Policy", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = androidx.compose.ui.graphics.Color.White, shadowElevation = 2.dp) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(34.dp))
                Text("Your privacy matters", color = T2SText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("This policy explains what T2S Studio handles and how app features use your information. Please read it before using speech, voice and cloud features.", color = T2SSecondaryText, fontSize = 14.sp)
                Text("Last updated: 24 November 2026", color = T2SSecondaryText, fontSize = 11.sp)
            }
        }
        PrivacySection(Icons.Filled.Person, "1. Account information", "T2S Studio uses Firebase Authentication to manage sign-in. Your account identifier and the sign-in details provided to Firebase are used to authenticate you and associate your cloud workspace with your account. T2S Studio does not need your password to provide speech features.")
        PrivacySection(Icons.Filled.TextFields, "2. Scripts, drafts and projects", "Project details, script and draft content, titles and language information may be stored in Firebase Cloud Firestore under your account so that your workspace can be synchronized. Script text you submit for speech generation is sent to the selected speech-generation service to process your request.")
        PrivacySection(Icons.Filled.AudioFile, "3. Generated audio and recordings", "Generated audio files and audio history are stored locally on your device. Audio bytes are not uploaded to T2S Firestore by the app's audio-history sync. If you choose to register a voice sample for cloning, the recording is sent to the selected voice provider (currently Sarvam AI) to create a provider-hosted voice. T2S keeps the local recording and voice-profile metadata in account-scoped app storage; generated audio remains on your device unless you explicitly share or export it.")
        PrivacySection(Icons.Filled.Mic, "4. Microphone permission", "The app requests microphone access for recording voice samples. The microphone is used when you actively start a recording. You can deny permission, but recording-based features will not work.")
        PrivacySection(Icons.Filled.CloudSync, "5. Third-party services", "T2S Studio uses Firebase Authentication and Cloud Firestore for account and workspace features, Cloudflare Workers for authenticated online TTS routing and quota enforcement, and external speech/voice providers including India TTS (api.dharun.dev), Vakyam and Sarvam AI. Text submitted for online speech generation is sent through the selected service to its provider. Voice samples submitted for cloning are sent to Sarvam AI. These providers process data under their own terms and privacy policies; their retention, deletion and processing practices are outside T2S Studio's control.")
        PrivacySection(Icons.Filled.Storage, "6. Local storage and deletion", "Generated audio, local projects, voice samples and audio history are stored in app-specific, account-scoped directories on your device. You can export your account data as a ZIP from Account. The in-app Delete account action requests deletion of the Firebase Authentication account, UID-owned Firestore workspace data and hashed online-TTS usage records, then clears this account's local app data. Files copied to public Downloads, files you shared, backups made outside the app, and voice models or recordings retained by third-party providers are not removed by this action. Provider-side deletion must be requested from the provider where available.")
        PrivacySection(Icons.Filled.Lock, "7. Security", "T2S Studio uses Firebase Authentication, account-scoped Firestore paths, Firebase App Check for protected backend calls, and server-side provider credentials. Online TTS usage limits use a SHA-256 hash of the Firebase UID in the Cloudflare D1 ledger rather than storing the raw UID there. No internet service or storage method can be guaranteed completely secure. Do not submit passwords, financial details, confidential business information or other sensitive content in scripts or recordings.")
        PrivacySection(Icons.Filled.Person, "8. Children's privacy", "T2S Studio is a general-purpose productivity app and is not designed specifically for children. A parent or guardian should supervise a minor's use and any sharing of voice recordings or personal information.")
        PrivacySection(Icons.Filled.TextFields, "9. Changes and contact", "This policy may be updated as app features and service providers change. The latest version will be available in the app. For privacy questions or requests, use the Contact option in the T2S Studio section of Account.")
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = T2SCream) {
            Text("Important: Information sent to Firebase or an external speech/voice provider is subject to that provider's systems and policies. Please review their privacy terms before using those features.", modifier = Modifier.padding(16.dp), color = T2SText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun PrivacySection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color.White, shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(17.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = T2SCream) {
                Icon(icon, contentDescription = null, tint = T2SOrange, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = T2SText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(body, color = T2SSecondaryText, fontSize = 13.sp)
            }
        }
    }
}
