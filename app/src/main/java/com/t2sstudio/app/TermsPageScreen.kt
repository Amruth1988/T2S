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
fun TermsPageScreen(onBack: () -> Unit) {
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
            Text("Terms & Conditions", color = T2SOrange, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Using T2S Studio", color = T2SOrange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Clear terms for a responsible experience", color = T2SOrange, fontSize = 26.sp,
                lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "These Terms & Conditions explain the rules that apply when you access or use the T2S Studio text-to-speech application, including its projects, drafts, audio and voice features. Please read them carefully before using the app.",
                color = T2SSecondaryText, fontSize = 14.sp, lineHeight = 22.sp
            )
            Text("Effective date: 25 November 2026", color = T2SSecondaryText, fontSize = 12.sp)
        }

        TermsTextSection("1. Acceptance and eligibility",
            "By installing, accessing or using T2S Studio, you agree to these Terms & Conditions and the app's Privacy Policy. If you do not agree, do not use the app. You must be legally capable of accepting these terms. If you use the app on behalf of another person or organization, you confirm that you are authorized to do so.")

        TermsTextSection("2. Your account and responsibility",
            "Some features require sign-in. You are responsible for the accuracy of the information you provide, protecting access to your device and account, and activity carried out through your account. Notify us if you believe your account has been accessed without permission. Do not impersonate another person or create an account using information you are not authorized to use.")

        TermsTextSection("3. Your scripts and content",
            "You retain responsibility for the text, documents, names and other material you enter or submit. You must own the material or have all permissions, licences and legal rights needed to use it. You are responsible for checking generated speech and ensuring that its use does not infringe another person's copyright, privacy, publicity or other rights.")

        TermsTextSection("4. Voice recordings and voice profiles",
            "Only record, upload, clone, imitate or generate a voice when you have the speaker's informed permission and all other required rights. Do not impersonate a person, mislead listeners about who is speaking, or use a voice to commit fraud, harassment or other harm. You must not submit recordings of another person without appropriate authorization.")

        TermsTextSection("5. Acceptable use",
            "You must not use T2S Studio to create or distribute unlawful, threatening, defamatory, hateful, abusive, sexually exploitative or deceptive material; facilitate fraud, scams or identity theft; harass or threaten anyone; violate intellectual-property or privacy rights; produce misleading impersonations; bypass security; interfere with the app or its services; or use the app in a way that violates applicable law. We may restrict access where misuse or a security risk is identified.")

        TermsTextSection("6. Generated audio and local files",
            "Generated audio, local projects, audio history and saved voice recordings are stored in app-specific storage on your device. You may export available account data from Account. You are responsible for separately backing up files you want to keep. Uninstalling the app, clearing app data, changing devices or deleting files may make local content unavailable. Files copied to public Downloads or shared outside the app are not removed when you delete your T2S account.")

        TermsTextSection("7. Cloud data and third-party services",
            "Projects, scripts and drafts may be synchronized through Firebase Authentication and Cloud Firestore. Online speech generation is routed through Cloudflare Workers to India TTS (api.dharun.dev) or Vakyam; Sarvam AI is used for voice registration and cloned-voice generation. Text or voice recordings you submit are sent to the relevant provider for processing. Provider use is subject to that provider's own terms, privacy policy, retention practices, limits and availability. Online generation is subject to per-account and provider-wide quotas and may be unavailable when limits or configuration requirements are reached. T2S Studio does not control third-party provider services or guarantee their output, security or performance.")

        TermsTextSection("8. AI-generated output",
            "Text-to-speech and voice features may use automated or AI-based systems. Outputs can contain errors, unexpected pronunciation or other inaccuracies. Review output before publishing, relying on it, or using it in a sensitive or consequential context. Generated output is not professional, legal, medical or financial advice, and no particular output quality or result is guaranteed.")

        TermsTextSection("9. Intellectual property and app licence",
            "T2S Studio and its app design, branding, software and related materials are protected by applicable intellectual-property laws. Subject to these terms, you may use the app for your personal or authorized business purposes. You may not copy, modify, reverse engineer or redistribute the app itself except where applicable law permits or we give written permission. These terms do not transfer ownership of your own input content to us.")

        TermsTextSection("10. Availability, changes and suspension",
            "Features may change, be limited or become unavailable, including because of maintenance, network conditions, provider changes or technical issues. We may update the app and these terms. We may suspend or restrict use where reasonably necessary to protect users, the service or comply with law. Updated terms will be made available in the app; continued use after an update means you accept the revised terms.")

        TermsTextSection("11. Disclaimer and limitation",
            "To the extent permitted by applicable law, the app and its features are provided on an as-available basis without promises that they will be uninterrupted, error-free or suitable for every purpose. We are not responsible for content you submit, third-party provider decisions, or how you use generated output. Nothing in these terms excludes a right or liability that cannot legally be excluded. To the extent permitted by law, T2S Studio will not be liable for indirect or consequential loss, loss of data, or loss of profits arising from use of the app.")

        TermsTextSection("12. Governing law and contact",
            "These terms are subject to the laws applicable in India, without limiting any mandatory consumer rights available to you. Any dispute will be handled by a competent forum as determined under applicable law. You may export available account data or request account deletion from Account. Deletion removes T2S-controlled account data and local app data where the cleanup succeeds, but does not delete files already shared or copied outside the app, nor provider-hosted cloned voice models or records retained by external providers. For provider-side deletion, contact the relevant provider. For questions, support or concerns about these terms, use the Contact option in the T2S Studio section of Account.")

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun TermsTextSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = T2SOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = T2SSecondaryText, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
