package com.t2sstudio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun ApiKeySettingsScreen() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Online TTS access", color = T2SText, fontSize = 24.sp)
        Text(
            "India TTS and Vakyam are managed centrally by the T2S administrator. You do not need to enter, save or share an API key on this device.",
            color = T2SSecondaryText, fontSize = 14.sp, lineHeight = 21.sp
        )
        Text(
            "When the backend is configured and available, online voice catalogues and speech generation use the secured T2S service. Your provider credentials are not included in the app or sent to your phone.",
            color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 20.sp
        )
        Text(
            "If an online provider is unavailable, contact the T2S administrator to check its central configuration.",
            color = T2SSecondaryText, fontSize = 13.sp, lineHeight = 20.sp
        )
    }
}
