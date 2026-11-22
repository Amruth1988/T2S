package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
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
fun TermsPageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.clickable(onClick = onBack), shape = CircleShape, color = T2SCream) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SOrange, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Text("Terms & Conditions", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = androidx.compose.ui.graphics.Color.White, shadowElevation = 2.dp) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Gavel, contentDescription = null, tint = T2SOrange, modifier = Modifier.size(34.dp))
                Text("Welcome to T2S Studio", color = T2SText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("These terms explain the rules for accessing and using the T2S Studio text-to-speech application, its projects, drafts, audio and voice features.", color = T2SSecondaryText, fontSize = 14.sp)
                Text("Effective date: 23 November 2026", color = T2SSecondaryText, fontSize = 11.sp)
            }
        }
        TermsSection(Icons.Filled.AccountCircle, "1. Acceptance and eligibility", "By installing, accessing or using T2S Studio, you agree to these Terms & Conditions and the app's Privacy Policy. If you do not agree, do not use the app. You must be legally capable of accepting these terms. If you use the app on behalf of another person or organization, you confirm that you are authorized to do so.")
        TermsSection(Icons.Filled.Security, "2. Your account and responsibility", "Some features require sign-in. You are responsible for the accuracy of the information you provide, protecting access to your device and account, and activity carried out through your account. Notify us if you believe your account has been accessed without permission. Do not impersonate another person or create an account using information you are not authorized to use.")
        TermsSection(Icons.Filled.ContentCopy, "3. Your scripts and content", "You retain responsibility for the text, documents, names and other material you enter or submit. You must own the material or have all permissions, licences and legal rights needed to use it. You are responsible for checking generated speech and ensuring that its use does not infringe another person's copyright, privacy, publicity or other rights.")
        TermsSection(Icons.Filled.Mic, "4. Voice recordings and voice profiles", "Only record, upload, clone, imitate or generate a voice when you have the speaker's informed permission and all other required rights. Do not impersonate a person, mislead listeners about who is speaking, or use a voice to commit fraud, harassment or other harm. You must not submit recordings of another person without appropriate authorization.")
        TermsSection(Icons.Filled.Warning, "5. Acceptable use and prohibited activities", "You must not use T2S Studio to create or distribute unlawful, threatening, defamatory, hateful, abusive, sexually exploitative or deceptive material; facilitate fraud, scams or identity theft; harass or threaten anyone; violate intellectual-property or privacy rights; produce misleading impersonations; bypass security; interfere with the app or its services; or use the app in a way that violates applicable law. We may restrict access where misuse or a security risk is identified.")
        TermsSection(Icons.Filled.AudioFile, "6. Generated audio and local files", "Generated audio and audio-history files are managed on your device. You are responsible for exporting, backing up and protecting files you want to keep. Uninstalling the app, clearing app data, changing devices or deleting files may make locally stored audio unavailable. T2S Studio does not guarantee recovery of deleted or lost local files.")
        TermsSection(Icons.Filled.Cloud, "7. Cloud data and third-party services", "Projects, scripts or drafts may be synchronized through Firebase services associated with your account. Speech generation and voice-related features may send submitted text or recordings to external providers for processing. Those services may have separate terms, privacy policies, limits, fees or availability requirements. Your use of a provider's service may also be subject to its terms. T2S Studio does not control third-party services and cannot guarantee their continued availability, output, security or performance.")
        TermsSection(Icons.Filled.Security, "8. AI-generated output", "Text-to-speech and voice features may use automated or AI-based systems. Outputs can contain errors, unexpected pronunciation or other inaccuracies. Review output before publishing, relying on it, or using it in a sensitive or consequential context. Generated output is not professional, legal, medical or financial advice, and no particular output quality or result is guaranteed.")
        TermsSection(Icons.Filled.Gavel, "9. Intellectual property and app licence", "T2S Studio and its app design, branding, software and related materials are protected by applicable intellectual-property laws. Subject to these terms, you may use the app for your personal or authorized business purposes. You may not copy, modify, reverse engineer, redistribute or commercially exploit the app itself except where applicable law permits or we give written permission. These terms do not transfer ownership of your own input content to us.")
        TermsSection(Icons.Filled.Warning, "10. Availability, changes and suspension", "Features may change, be limited or become unavailable, including because of maintenance, network conditions, provider changes or technical issues. We may update the app and these terms. We may suspend or restrict use where reasonably necessary to protect users, the service or comply with law. Updated terms will be made available in the app; continued use after an update means you accept the revised terms.")
        TermsSection(Icons.Filled.Security, "11. Disclaimer and limitation", "To the extent permitted by applicable law, the app and its features are provided on an as-available basis without promises that they will be uninterrupted, error-free or suitable for every purpose. We are not responsible for content you submit, third-party provider decisions, or how you use generated output. Nothing in these terms excludes a right or liability that cannot legally be excluded. To the extent permitted by law, T2S Studio will not be liable for indirect or consequential loss, loss of data, or loss of profits arising from use of the app.")
        TermsSection(Icons.Filled.Gavel, "12. Governing law and contact", "These terms are subject to the laws applicable in India, without limiting any mandatory consumer rights available to you. Any dispute will be handled by a competent forum as determined under applicable law. For questions, support or concerns about these terms, use the Contact option in the T2S Studio section of Account.")
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = T2SCream) {
            Text("Please note: This is general app terms content drafted for T2S Studio based on the features currently identified in the app. It is not a substitute for review by a qualified lawyer before public release or commercial use.", modifier = Modifier.padding(16.dp), color = T2SText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun TermsSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
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
