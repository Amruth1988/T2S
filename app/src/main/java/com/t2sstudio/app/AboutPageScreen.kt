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
fun AboutPageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(T2SBackground)
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
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = T2SOrange,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Text(
                "About Us",
                color = T2SText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "T2S Studio",
                color = T2SOrange,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Your words, brought to life.",
                color = T2SText,
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "T2S Studio is a text-to-speech workspace designed to make creating spoken content simple and convenient. Write or prepare your text, turn it into speech, and keep your audio and projects organized in one place.",
                color = T2SSecondaryText,
                fontSize = 15.sp,
                lineHeight = 23.sp
            )
        }

        AboutTextSection(
            title = "Our purpose",
            body = "We believe that turning written words into clear, useful audio should be straightforward. T2S Studio brings the essential tools for speech creation and script management together in an easy-to-use Android app."
        )

        AboutTextSection(
            title = "Create speech",
            body = "Prepare your text and generate spoken audio using the languages and voices supported by the available speech service. You can listen to your result and save the generated audio in supported formats."
        )

        AboutTextSection(
            title = "Your voices",
            body = "Keep your voice profiles and recordings organized. Where supported, choose a saved voice for your projects and preview available voice samples before using them."
        )

        AboutTextSection(
            title = "Projects and drafts",
            body = "Save scripts as drafts, organize your work into projects, and return to your content whenever you are ready to continue."
        )

        AboutTextSection(
            title = "Your audio, your workflow",
            body = "Keep generated audio on your device so it is available for playback and, where supported, sharing or exporting. You stay in control of how you use and distribute your content."
        )

        AboutTextSection(
            title = "Built to keep things simple",
            body = "T2S Studio aims to bring writing, voice selection, speech generation and audio organization into one practical workspace. An internet connection may be required for online speech services, and availability can depend on the service provider."
        )

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun AboutTextSection(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            title,
            color = T2SText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            body,
            color = T2SSecondaryText,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}
