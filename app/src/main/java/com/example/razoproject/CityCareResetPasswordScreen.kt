package com.example.razoproject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design System Colors matching Reset Password Screen
val ResetCardBg = Color(0xFFF8FAFC)
val ResetBorderColor = Color(0xFFCBD5E1)
val ResetGreenPrimary = Color(0xFF1E7A38)
val ResetTextDark = Color(0xFF1E293B)
val ResetTextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityCareResetPasswordScreen(
    onBackClick: () -> Unit = {},
    onResetSuccess: () -> Unit = {}
) {
    var emailOrPhone by remember { mutableStateOf("") }
    val otpDigits = remember { mutableStateListOf("", "", "", "", "", "") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isNewPassVisible by remember { mutableStateOf(false) }
    var isConfirmPassVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var timerText by remember { mutableStateOf("01:59") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResetBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Row: Back Arrow + Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDCFCE7))
                                    .clickable { onBackClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = ResetGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Reset Password",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResetGreenPrimary
                                )
                                Text(
                                    text = "Recover your resident account",
                                    fontSize = 12.sp,
                                    color = ResetTextMuted
                                )
                            }
                        }

                        // Top Progress Bar
                        LinearProgressIndicator(
                            progress = { 0.5f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ResetGreenPrimary,
                            trackColor = Color(0xFFDCFCE7)
                        )

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

                        // 1. Email or Phone Input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Email or Phone Number",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResetTextDark
                            )
                            OutlinedTextField(
                                value = emailOrPhone,
                                onValueChange = {
                                    emailOrPhone = it
                                    errorMessage = null
                                },
                                placeholder = {
                                    Text(
                                        text = "e.g. juan@kagayan.ph or 0917 123 4567",
                                        fontSize = 12.sp,
                                        color = ResetTextMuted
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ResetGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // 2. Verification Code Section
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Enter Verification Code",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResetTextDark
                            )
                            Text(
                                text = "We've sent a 6-digit code to your registered contact.",
                                fontSize = 11.sp,
                                color = ResetTextMuted,
                                textAlign = TextAlign.Center
                            )

                            // 6 OTP Digit Boxes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until 6) {
                                    OutlinedTextField(
                                        value = otpDigits[i],
                                        onValueChange = { valText ->
                                            if (valText.length <= 1) {
                                                otpDigits[i] = valText
                                            }
                                        },
                                        modifier = Modifier
                                            .width(42.dp)
                                            .height(48.dp),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ResetGreenPrimary,
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        )
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Resend code in ",
                                    fontSize = 11.sp,
                                    color = ResetTextMuted
                                )
                                Text(
                                    text = timerText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResetGreenPrimary
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // 3. Set New Password Input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Set New Password",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResetTextDark
                            )
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    errorMessage = null
                                },
                                placeholder = { Text("••••••••", fontSize = 12.sp, color = ResetTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { isNewPassVisible = !isNewPassVisible }) {
                                        Icon(
                                            imageVector = if (isNewPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = ResetTextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (isNewPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ResetGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // 4. Confirm New Password Input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Confirm New Password",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResetTextDark
                            )
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    errorMessage = null
                                },
                                placeholder = { Text("••••••••", fontSize = 12.sp, color = ResetTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { isConfirmPassVisible = !isConfirmPassVisible }) {
                                        Icon(
                                            imageVector = if (isConfirmPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = ResetTextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (isConfirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ResetGreenPrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 5. Submit Button: RESET PASSWORD
                        Button(
                            onClick = {
                                when {
                                    emailOrPhone.trim().isEmpty() -> {
                                        errorMessage = "Please enter your Email or Phone Number!"
                                    }
                                    newPassword.trim().isEmpty() -> {
                                        errorMessage = "Please enter a new password!"
                                    }
                                    newPassword != confirmPassword -> {
                                        errorMessage = "Passwords do not match!"
                                    }
                                    else -> {
                                        errorMessage = null
                                        onResetSuccess()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ResetGreenPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "RESET PASSWORD",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CityCareResetPasswordScreenPreview() {
    MaterialTheme {
        CityCareResetPasswordScreen()
    }
}
