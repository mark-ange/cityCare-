package com.example.razoproject

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facebook.CallbackManager
import com.facebook.FacebookSdk
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

// Color Palette matching Eco UI Design
val EcoAuthGreen = Color(0xFF1E7A38)
val EcoAuthDarkGreen = Color(0xFF0F382C)
val EcoAuthTextDark = Color(0xFF1E293B)
val EcoAuthTextMuted = Color(0xFF64748B)
val EcoAuthTabBg = Color(0xFFE2E8F0)
val EcoAuthInputBg = Color(0xFFF8FAFC)

// Aliases for compatibility
val FigmaGreenButton = EcoAuthGreen
val FigmaTextDark = EcoAuthTextDark

// -----------------------------------------------------------------------------
// UPDATED OFFICIAL FACEBOOK & GOOGLE LOGO COMPOSABLES
// -----------------------------------------------------------------------------
@Composable
fun FacebookLogoIcon(modifier: Modifier = Modifier.size(20.dp)) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF1877F2)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "f",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.offset(x = 1.dp, y = (-1).dp)
        )
    }
}

@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) * 0.42f
        val stroke = radius * 0.45f

        // Top Red Arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 215f,
            sweepAngle = 105f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Bottom-Left Yellow Arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 80f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Bottom Green Arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 35f,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Right Blue Arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -25f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Blue Center Crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(center.x - stroke * 0.1f, center.y),
            end = Offset(center.x + radius + stroke * 0.2f, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Square
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityCareLoginScreen(
    onLoginSuccess: () -> Unit = {},
    onAnonymousReport: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sign in, 1: Register

    // Sign In State
    var emailAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Register State
    var regFullName by remember { mutableStateOf("") }
    var regPhoneNumber by remember { mutableStateOf("") }
    var regEmailOptional by remember { mutableStateOf("") }
    var regBarangay by remember { mutableStateOf("Iponan") }
    var regPurokZone by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regAgreeTerms by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) }
    var isBarangayDropdownExpanded by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticatingSocial by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val barangayList = listOf("Iponan", "Bulua", "Canitoan", "Carmen", "Patag", "Kauswagan")

    // Official Google Sign-In Setup using Client ID from google-services.json
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken("705374451507-oii7ck2c6nduelsjtkpu4240anniqhp7.apps.googleusercontent.com")
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Official ActivityResult Launcher for Google Sign-In extracting Real Google Profile Data
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAuthenticatingSocial = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val realEmail = account?.email ?: ""
            val displayName = account?.displayName ?: account?.givenName ?: realEmail.substringBefore("@")
            val photoUrl = account?.photoUrl?.toString()

            errorMessage = null
            isAuthenticatingSocial = true
            coroutineScope.launch {
                val success = CityCareApiService.loginWithGoogleSocial(realEmail, displayName, photoUrl)
                isAuthenticatingSocial = false
                if (success) {
                    onLoginSuccess()
                } else {
                    errorMessage = "Unable to authenticate Google account ($realEmail)."
                }
            }
        } catch (e: ApiException) {
            isAuthenticatingSocial = false
            errorMessage = when (e.statusCode) {
                10 -> "Google Sign-In Config Error (Code 10): Developer SHA-1 fingerprint is not registered in Firebase / Google Cloud Console."
                12500 -> "Google Sign-In Failed (Code 12500): Check SHA-1 certificate fingerprint and package name in Google Developer Console."
                12502 -> "Google Sign-In is already in progress. Wait for the current sign-in prompt to close, then try again."
                12501 -> "Google Sign-In canceled by user."
                7 -> "Network Error (Code 7): Please check your internet connection."
                else -> "Google Sign-In error (Code ${e.statusCode}): ${e.localizedMessage ?: "Authentication failed"}. Verify SHA-1 setup in Firebase Console."
            }
        }
    }

    val facebookConfigured = remember(context) {
        context.getString(R.string.facebook_app_id).isNotBlank() && FacebookSdk.isInitialized()
    }
    val callbackManager = remember(facebookConfigured) {
        if (facebookConfigured) CallbackManager.Factory.create() else null
    }
    val facebookLauncher = if (callbackManager != null) {
        rememberLauncherForActivityResult(
            contract = LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null)
        ) { }
    } else {
        null
    }

    DisposableEffect(callbackManager) {
        if (callbackManager != null) {
            LoginManager.getInstance().registerCallback(
                callbackManager,
                object : FacebookCallback<LoginResult> {
                    override fun onSuccess(result: LoginResult) {
                        isAuthenticatingSocial = true
                        coroutineScope.launch {
                            val (success, message) = CityCareApiService.loginWithFacebookAccessToken(
                                accessToken = result.accessToken.token,
                                context = context
                            )
                            isAuthenticatingSocial = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                errorMessage = message
                            }
                        }
                    }

                    override fun onCancel() {
                        isAuthenticatingSocial = false
                        errorMessage = "Facebook Sign-In canceled."
                    }

                    override fun onError(error: FacebookException) {
                        isAuthenticatingSocial = false
                        errorMessage = error.localizedMessage?.let { "Facebook Sign-In Error: $it" }
                            ?: "Facebook sign-in failed. Please try again."
                    }
                }
            )
            onDispose {
                LoginManager.getInstance().unregisterCallback(callbackManager)
            }
        } else {
            onDispose {}
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Eco Welcome Hero Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(EcoAuthDarkGreen, EcoAuthGreen)
                            )
                        )
                        .statusBarsPadding()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Eco,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (selectedTab == 0) "CityCare CDO Resident Portal" else "Join CityCare CDO",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF80ED99)
                        )

                        Text(
                            text = if (selectedTab == 0) "Sign In to CityCare" else "Create Resident Account",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Text(
                            text = if (selectedTab == 0) {
                                "Access your resident account to report concerns, track garbage collection, and earn eco points."
                            } else {
                                "Sign up to report waste issues, stay updated with CLENRO collection schedules, and help our environment."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 2. Main Form Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Pill Tab Bar ("Sign in" vs "Register")
                    Surface(
                        color = EcoAuthTabBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedTab = 0
                                        errorMessage = null
                                    },
                                color = if (selectedTab == 0) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                shadowElevation = if (selectedTab == 0) 2.dp else 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Sign In",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EcoAuthTextDark
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedTab = 1
                                        errorMessage = null
                                    },
                                color = if (selectedTab == 1) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                shadowElevation = if (selectedTab == 1) 2.dp else 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Register",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EcoAuthTextDark
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Error / Success Banners
                    errorMessage?.let { msg ->
                        Surface(
                            color = Color(0xFFFFE4E6),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = Color(0xFFE11D48),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    successMessage?.let { msg ->
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = Color(0xFF059669),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // TAB 0: SIGN IN FORM
                    if (selectedTab == 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Email Input
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Email address or Resident ID",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EcoAuthTextDark
                                )
                                OutlinedTextField(
                                    value = emailAddress,
                                    onValueChange = {
                                        emailAddress = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("Enter your email or ID", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EcoAuthGreen,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Password Input
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Password",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EcoAuthTextDark
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("••••••••", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password visibility",
                                                tint = EcoAuthTextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EcoAuthGreen,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Forgot Password Link
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EcoAuthGreen,
                                    modifier = Modifier.clickable { onForgotPasswordClick() }
                                )
                            }

                            // Primary Sign In Button
                            Button(
                                onClick = {
                                    val cleanEmail = emailAddress.trim()
                                    val cleanPass = password.trim()

                                    if (cleanEmail.isEmpty()) {
                                        errorMessage = "Please enter your Email address or Resident ID!"
                                    } else if (cleanPass.isEmpty()) {
                                        errorMessage = "Please enter your Password!"
                                    } else {
                                        errorMessage = null
                                        isAuthenticatingSocial = true
                                        coroutineScope.launch {
                                            val isValid = CityCareApiService.loginUserOnline(cleanEmail, cleanPass)
                                            isAuthenticatingSocial = false
                                            if (isValid) {
                                                onLoginSuccess()
                                            } else {
                                                errorMessage = "Login Failed: Invalid Email/Resident ID or Password. Please check your credentials or register a new account."
                                            }
                                        }
                                    }
                                },
                                enabled = !isAuthenticatingSocial,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EcoAuthGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isAuthenticatingSocial) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        text = "SIGN IN",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Other Sign In Options Header
                            Text(
                                text = "Or continue with",
                                fontSize = 11.sp,
                                color = EcoAuthTextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // UPDATED SOCIAL LOGIN BUTTONS WITH REAL GOOGLE OAUTH
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            errorMessage = null
                                            if (facebookLauncher == null) {
                                                errorMessage = "Facebook sign-in is not configured. Add the Facebook App ID and Client Token to your Gradle properties, then rebuild."
                                            } else if (!isAuthenticatingSocial) {
                                                isAuthenticatingSocial = true
                                                facebookLauncher.launch(listOf("email", "public_profile"))
                                            }
                                        },
                                        enabled = !isAuthenticatingSocial,
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FacebookLogoIcon(modifier = Modifier.size(20.dp))
                                            Text("Facebook", fontSize = 12.sp, color = EcoAuthTextDark, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Real Google Sign-In Button (Always prompts Account Picker)
                                    OutlinedButton(
                                        onClick = {
                                            if (isAuthenticatingSocial) return@OutlinedButton
                                            errorMessage = null
                                            isAuthenticatingSocial = true
                                            googleSignInClient.signOut().addOnCompleteListener {
                                                googleLauncher.launch(googleSignInClient.signInIntent)
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        enabled = !isAuthenticatingSocial,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GoogleLogoIcon(modifier = Modifier.size(20.dp))
                                            Text(
                                                if (isAuthenticatingSocial) "Signing in..." else "Google",
                                                fontSize = 12.sp,
                                                color = EcoAuthTextDark,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB 1: FULL REGISTRATION FORM
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Personal Information",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EcoAuthTextDark
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Full Name", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    OutlinedTextField(
                                        value = regFullName,
                                        onValueChange = {
                                            regFullName = it
                                            errorMessage = null
                                        },
                                        placeholder = { Text("Juan Dela Cruz", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Email Address / Gmail", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    OutlinedTextField(
                                        value = regEmailOptional,
                                        onValueChange = {
                                            regEmailOptional = it
                                            errorMessage = null
                                        },
                                        placeholder = { Text("juan@example.com", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Phone Number (Optional)", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    OutlinedTextField(
                                        value = regPhoneNumber,
                                        onValueChange = {
                                            regPhoneNumber = it
                                            errorMessage = null
                                        },
                                        placeholder = { Text("0912 345 6789", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                    )
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Text(
                                    text = "Location",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EcoAuthTextDark
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Select Barangay", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    ExposedDropdownMenuBox(
                                        expanded = isBarangayDropdownExpanded,
                                        onExpandedChange = { isBarangayDropdownExpanded = !isBarangayDropdownExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = regBarangay,
                                            onValueChange = { },
                                            readOnly = true,
                                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isBarangayDropdownExpanded,
                                            onDismissRequest = { isBarangayDropdownExpanded = false }
                                        ) {
                                            barangayList.forEach { bgy ->
                                                DropdownMenuItem(
                                                    text = { Text(bgy, fontSize = 13.sp) },
                                                    onClick = {
                                                        regBarangay = bgy
                                                        isBarangayDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Default Purok / Zone / Street", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    OutlinedTextField(
                                        value = regPurokZone,
                                        onValueChange = { regPurokZone = it },
                                        placeholder = { Text("e.g., Zone 3, Acacia St.", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                    )
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Text(
                                    text = "Security",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EcoAuthTextDark
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Password (min. 6 chars, 1 uppercase, 1 symbol)", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    OutlinedTextField(
                                        value = regPassword,
                                        onValueChange = {
                                            regPassword = it
                                            errorMessage = null
                                        },
                                        placeholder = { Text("e.g. Mark1234!", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Confirm Password", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EcoAuthTextMuted)
                                    OutlinedTextField(
                                        value = regConfirmPassword,
                                        onValueChange = {
                                            regConfirmPassword = it
                                            errorMessage = null
                                        },
                                        placeholder = { Text("e.g. Mark1234!", fontSize = 12.sp, color = EcoAuthTextMuted) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoAuthGreen, unfocusedBorderColor = Color(0xFFCBD5E1))
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { regAgreeTerms = !regAgreeTerms }
                                ) {
                                    Checkbox(
                                        checked = regAgreeTerms,
                                        onCheckedChange = { regAgreeTerms = it },
                                        colors = CheckboxDefaults.colors(checkedColor = EcoAuthGreen)
                                    )
                                    Text(
                                        text = "I agree to the Terms of Service and Privacy Policy.",
                                        fontSize = 11.sp,
                                        color = EcoAuthTextDark,
                                        lineHeight = 15.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val cleanName = regFullName.trim()
                                        val cleanPhone = regPhoneNumber.trim()
                                        val cleanEmail = regEmailOptional.trim()
                                        val cleanPass = regPassword.trim()

                                        val hasUppercase = cleanPass.any { it.isUpperCase() }
                                        val hasSpecialChar = cleanPass.any { !it.isLetterOrDigit() }

                                        when {
                                            cleanName.isEmpty() -> errorMessage = "Please enter your Full Name!"
                                            cleanEmail.isEmpty() -> errorMessage = "Please enter your Email address / Gmail!"
                                            cleanPass.length < 6 -> errorMessage = "Password must be at least 6 characters long!"
                                            !hasUppercase -> errorMessage = "Password must contain at least one uppercase letter (e.g. Mark1234!)."
                                            !hasSpecialChar -> errorMessage = "Password must contain at least one special character (e.g. Mark1234!)."
                                            cleanPass != regConfirmPassword.trim() -> errorMessage = "Passwords do not match!"
                                            !regAgreeTerms -> errorMessage = "Please agree to the Terms of Service and Privacy Policy!"
                                            else -> {
                                                errorMessage = null
                                                isRegistering = true
                                                val account = CitizenAccount(
                                                    name = cleanName,
                                                    purok = regPurokZone.ifEmpty { "Zone 3" },
                                                    identifier = cleanEmail,
                                                    password = cleanPass,
                                                    email = cleanEmail,
                                                    phoneNumber = cleanPhone,
                                                    barangay = regBarangay
                                                )
                                                coroutineScope.launch {
                                                    val (isSuccess, msg) = CityCareApiService.registerCitizenOnline(account)
                                                    isRegistering = false
                                                    if (isSuccess) {
                                                        successMessage = "Registration Successful! Account created. You can now sign in."
                                                        emailAddress = cleanEmail.ifEmpty { cleanPhone }
                                                        password = cleanPass
                                                        selectedTab = 0
                                                    } else {
                                                        errorMessage = msg
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isRegistering && !isAuthenticatingSocial,
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(25.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EcoAuthGreen, contentColor = Color.White)
                                ) {
                                    if (isRegistering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = Color.White,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("CREATE ACCOUNT", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CityCareLoginScreenPreview() {
    MaterialTheme {
        CityCareLoginScreen()
    }
}
