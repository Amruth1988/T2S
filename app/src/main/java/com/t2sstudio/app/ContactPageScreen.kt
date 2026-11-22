package com.t2sstudio.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SCream
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun ContactPageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.clickable(onClick = onBack), shape = CircleShape, color = T2SCream) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SOrange,
                    modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Text("Contact Us", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = T2SCream) {
                    Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = T2SOrange,
                        modifier = Modifier.padding(12.dp).size(30.dp))
                }
                Text("We're here to help", color = T2SText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Have a question, need support, or want to share feedback about T2S Studio? Reach us using the contact details below.",
                    color = T2SSecondaryText, fontSize = 14.sp
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:anruth4official@gmail.com"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(18.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = T2SCream) {
                    Icon(Icons.Filled.Email, contentDescription = "Email", tint = T2SOrange,
                        modifier = Modifier.padding(12.dp).size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Email us", color = T2SSecondaryText, fontSize = 13.sp)
                    Text("anruth4official@gmail.com", color = T2SText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap to compose an email", color = T2SOrange, fontSize = 12.sp)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919908355501"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(18.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = T2SCream) {
                    Icon(Icons.Filled.Phone, contentDescription = "Phone", tint = T2SOrange,
                        modifier = Modifier.padding(12.dp).size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Call us", color = T2SSecondaryText, fontSize = 13.sp)
                    Text("+91 9908355501", color = T2SText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap to open your phone dialler", color = T2SOrange, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}
