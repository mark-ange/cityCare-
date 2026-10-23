package com.example.razoproject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Registration Screen Styling matching Eco UI
val RegGreenPrimary = Color(0xFF1E7A38)
val RegCardBg = Color(0xFFF8FAFC)
val RegBorderColor = Color(0xFFCBD5E1)
val RegTextDark = Color(0xFF1E293B)
val RegTextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityCareRegisterScreen(
    onRegisterSuccess: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var emailOptional by remember { mutableStateOf("") }
    var selectedBarangay by remember { mutableStateOf("Iponan") }
    var purokZone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreeToTerms by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isBarangayDropdownExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val barangayList = listOf("Iponan", "Bulua", "Canitoan", "Carmen", "Patag", "Kauswagan")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Top Logo Emblem
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RegGreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Eco,
                        contentDescription = "CityCare Emblem",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title & Subtitle
                Text(
                    text = "Create Resident Account",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RegGreenPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Register to report issues and improve our community.",
                    fontSize = 12.sp,
                    color = RegTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card Container
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RegBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Error Banner
                        errorMessage?.let { msg ->
                            Surface(
                                color = Color(0xFFFFE4E6),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    color = Color(0xFFE11D48),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // SECTION 1: Personal Information
                        Text(
                            text = "Personal Information",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RegTextDark
                        )

                        // Full Name
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Full Name", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    errorMessage = null
                                },
                                placeholder = { Text("Juan Dela Cruz", fontSize = 12.sp, color = RegTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // Phone Number
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Phone Number", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = {
                                    phoneNumber = it
                                    errorMessage = null
                                },
                                placeholder = { Text("0912 345 6789", fontSize = 12.sp, color = RegTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // Email (Optional)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Email (Optional)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            OutlinedTextField(
                                value = emailOptional,
                                onValueChange = { emailOptional = it },
                                placeholder = { Text("juan@example.com", fontSize = 12.sp, color = RegTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // SECTION 2: Location
                        Text(
                            text = "Location",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RegTextDark
                        )

                        // Select Barangay
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Select Barangay", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            ExposedDropdownMenuBox(
                                expanded = isBarangayDropdownExpanded,
                                onExpandedChange = { isBarangayDropdownExpanded = !isBarangayDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedBarangay,
                                    onValueChange = { },
                                    readOnly = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = RegGreenPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = RegTextMuted
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = RegGreenPrimary,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = isBarangayDropdownExpanded,
                                    onDismissRequest = { isBarangayDropdownExpanded = false }
                                ) {
                                    barangayList.forEach { bgy ->
                                        DropdownMenuItem(
                                            text = { Text(bgy, fontSize = 13.sp) },
                                            onClick = {
                                                selectedBarangay = bgy
                                                isBarangayDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Default Purok / Zone / Street
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Default Purok / Zone / Street", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            OutlinedTextField(
                                value = purokZone,
                                onValueChange = { purokZone = it },
                                placeholder = { Text("e.g., Zone 3, Acacia St.", fontSize = 12.sp, color = RegTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // SECTION 3: Security
                        Text(
                            text = "Security",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RegTextDark
                        )

                        // Password
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Password", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    errorMessage = null
                                },
                                placeholder = { Text("••••••••", fontSize = 12.sp, color = RegTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // Confirm Password
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Confirm Password", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RegTextDark)
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    errorMessage = null
                                },
                                placeholder = { Text("••••••••", fontSize = 12.sp, color = RegTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // Terms & Privacy Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { agreeToTerms = !agreeToTerms }
                        ) {
                            Checkbox(
                                checked = agreeToTerms,
                                onCheckedChange = { agreeToTerms = it },
                                colors = CheckboxDefaults.colors(checkedColor = RegGreenPrimary)
                            )
                            Text(
                                text = "I agree to the Terms of Service and Privacy Policy.",
                                fontSize = 11.sp,
                                color = RegTextDark,
                                lineHeight = 15.sp
                            )
                        }

                        // CREATE ACCOUNT BUTTON
                        Button(
                            onClick = {
                                val cleanName = fullName.trim()
                                val cleanPhone = phoneNumber.trim()
                                val cleanEmail = emailOptional.trim()
                                val cleanPass = password.trim()

                                when {
                                    cleanName.isEmpty() -> {
                                        errorMessage = "Please enter your Full Name!"
                                    }
                                    cleanPhone.isEmpty() && cleanEmail.isEmpty() -> {
                                        errorMessage = "Please enter your Phone Number or Email address!"
                                    }
                                    cleanPass.length < 6 -> {
                                        errorMessage = "Password must be at least 6 characters long!"
                                    }
                                    cleanPass != confirmPassword.trim() -> {
                                        errorMessage = "Passwords do not match!"
                                    }
                                    !agreeToTerms -> {
                                        errorMessage = "Please agree to the Terms of Service and Privacy Policy."
                                    }
                                    else -> {
                                        errorMessage = null
                                        isLoading = true
                                        val account = CitizenAccount(
                                            name = cleanName,
                                            purok = purokZone.ifEmpty { "Zone 3" },
                                            identifier = cleanPhone.ifEmpty { cleanEmail },
                                            password = cleanPass,
                                            email = cleanEmail,
                                            phoneNumber = cleanPhone,
                                            barangay = selectedBarangay
                                        )

                                        coroutineScope.launch {
                                            val (isSuccess, msg) = CityCareApiService.registerCitizenOnline(account)
                                            isLoading = false
                                            if (isSuccess) {
                                                onRegisterSuccess()
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RegGreenPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "CREATE ACCOUNT",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Link: Already have an account? Log in
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account? ",
                        fontSize = 13.sp,
                        color = RegTextMuted
                    )
                    Text(
                        text = "Log in",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RegGreenPrimary,
                        modifier = Modifier.clickable { onLoginClick() }
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CityCareRegisterScreenPreview() {
    MaterialTheme {
        CityCareRegisterScreen()
    }
}
