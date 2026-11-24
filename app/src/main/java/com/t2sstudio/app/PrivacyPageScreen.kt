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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
        modifier = Modifier.fillMaxSize().background(T2SBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(onClick = onBack, shape = CircleShape, color = T2SCream) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SOrange,
                    modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Text("Privacy Policy", color = T2SText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your privacy matters", color = T2SOrange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("How T2S Studio handles your information", color = T2SText, fontSize = 26.sp,
                lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "This Privacy Policy explains what information is handled when you use T2S Studio, why it is used, and the choices available to you. Some features rely on online services to provide account synchronization and speech or voice processing.",
                color = T2SSecondaryText, fontSize = 14.sp, lineHeight = 22.sp
            )
            Text("Last updated: 25 November 2026", color = T2SSecondaryText, fontSize = 12.sp)
        }

        PrivacyTextSection("1. Account information",
            "T2S Studio uses Firebase Authentication to manage sign-in. Your account identifier and sign-in details provided to Firebase are used to authenticate you and associate cloud workspace information with your account. T2S Studio does not need your password to provide speech features.")

        PrivacyTextSection("2. Scripts, drafts and projects",
            "Project details, script and draft content, titles and language information may be stored in Firebase Cloud Firestore under your account so your workspace can be synchronized. Script text you submit for speech generation is sent through the selected speech service to process your request.")

        PrivacyTextSection("3. Generated audio and voice recordings",
            "Generated audio files and audio history are stored locally on your device. Audio bytes are not uploaded to T2S Firestore by the app's audio-history sync. If you choose to register a voice sample for cloning, the recording is sent to the selected voice provider (currently Sarvam AI) to create a provider-hosted voice. T2S keeps local recordings and voice-profile metadata in account-scoped app storage. Generated audio remains on your device unless you explicitly share or export it.")

        PrivacyTextSection("4. Microphone permission",
            "The app requests microphone access for recording voice samples. The microphone is used when you actively start a recording. You can deny permission, but recording-based features will not work.")

        PrivacyTextSection("5. Third-party services",
            "T2S Studio uses Firebase Authentication and Cloud Firestore for account and workspace features, Cloudflare Workers for authenticated online TTS routing and quota enforcement, and external speech or voice providers including India TTS (api.dharun.dev), Vakyam and Sarvam AI. Text submitted for online speech generation is sent through the selected service to its provider. Voice samples submitted for cloning are sent to Sarvam AI. These providers process data under their own terms and privacy policies. Their retention, deletion and processing practices are outside T2S Studio's control.")

        PrivacyTextSection("6. Local storage, export and deletion",
            "Generated audio, local projects, voice samples and audio history are stored in app-specific, account-scoped directories on your device. You can export available account data as a ZIP from Account. The in-app Delete account action requests deletion of the Firebase Authentication account, UID-owned Firestore workspace data and hashed online-TTS usage records, then clears this account's local app data. Files copied to public Downloads, files you shared, backups made outside the app, and voice models or recordings retained by third-party providers are not removed by this action. Provider-side deletion must be requested from the provider where available.")

        PrivacyTextSection("7. Security",
            "T2S Studio uses Firebase Authentication, account-scoped Firestore paths, Firebase App Check for protected backend calls, and server-side provider credentials. Online TTS quota records use a SHA-256 hash of the Firebase UID in Cloudflare D1 and Firestore usage ledgers rather than storing the raw UID in those usage records. No internet service or storage method can be guaranteed completely secure. Avoid submitting passwords, financial details, confidential business information or other sensitive content in scripts or recordings.")

        PrivacyTextSection("8. Children's privacy",
            "T2S Studio is a general-purpose productivity app and is not designed specifically for children. A parent or guardian should supervise a minor's use and any sharing of voice recordings or personal information.")

        PrivacyTextSection("9. Changes to this policy",
            "This policy may be updated as app features and service providers change. The latest version will be made available in the app. Please review it periodically.")

        PrivacyTextSection("10. Contact and privacy requests",
            "For privacy questions or requests, use the Contact option in the T2S Studio section of Account. Requests involving data held by an external speech or voice provider may need to be directed to that provider.")

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun PrivacyTextSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = T2SText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = T2SSecondaryText, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
