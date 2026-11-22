package com.t2sstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.t2sstudio.app.data.firebase.T2SFirestoreRepository
import com.t2sstudio.app.ui.theme.T2SBackground
import com.t2sstudio.app.ui.theme.T2SOrange
import com.t2sstudio.app.ui.theme.T2SSecondaryText
import com.t2sstudio.app.ui.theme.T2SText

@Composable
fun UserProfileScreen(onBack: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    val repository = remember { T2SFirestoreRepository() }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf(user?.phoneNumber.orEmpty()) }
    var city by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        if (user == null) {
            loading = false
            error = "Please sign in to manage your profile."
        } else {
            repository.loadUserProfile(user.uid) { result ->
                result.onSuccess { profile ->
                    firstName = profile["firstName"].orEmpty()
                    lastName = profile["lastName"].orEmpty()
                    if (profile["mobile"].orEmpty().isNotBlank()) mobile = profile["mobile"].orEmpty()
                    city = profile["city"].orEmpty()
                    gender = profile["gender"].orEmpty()
                }.onFailure { error = it.localizedMessage ?: "Could not load profile." }
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(T2SBackground).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = T2SText) }
            Text("My Profile", color = T2SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Text("Keep your personal details up to date.", color = T2SSecondaryText, fontSize = 14.sp)
        if (loading) {
            CircularProgressIndicator(color = T2SOrange, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Surface(modifier = Modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it; saved = false }, label = { Text("First name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it; saved = false }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }; saved = false }, label = { Text("Mobile number *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = city, onValueChange = { city = it; saved = false }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Gender", color = T2SText, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Male", "Female", "Other").forEach { option ->
                            Surface(
                                modifier = Modifier.weight(1f).clickable { gender = option; saved = false },
                                color = if (gender == option) T2SOrange.copy(alpha = 0.16f) else T2SBackground,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(option, color = T2SText, modifier = Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                    if (error != null) Text(error!!, color = androidx.compose.ui.graphics.Color(0xFFB3261E), fontSize = 13.sp)
                    if (saved) Text("Profile saved successfully.", color = T2SOrange, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            val activeUser = user
                            when {
                                activeUser == null -> error = "Please sign in to save your profile."
                                firstName.isBlank() -> error = "First name is required."
                                mobile.isBlank() -> error = "Mobile number is required."
                                mobile.count { it.isDigit() } < 7 -> error = "Enter a valid mobile number."
                                else -> {
                                    saving = true
                                    error = null
                                    repository.saveUserProfile(activeUser.uid, firstName, lastName, mobile, city, gender) { result ->
                                        saving = false
                                        result.onSuccess { saved = true }
                                            .onFailure { error = it.localizedMessage ?: "Could not save profile. Please try again." }
                                    }
                                }
                            }
                        },
                        enabled = !saving && !loading && user != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = T2SOrange, contentColor = T2SBackground)
                    ) {
                        if (saving) CircularProgressIndicator(color = T2SBackground) else Text("Save profile")
                    }
                }
            }
        }
    }
}
