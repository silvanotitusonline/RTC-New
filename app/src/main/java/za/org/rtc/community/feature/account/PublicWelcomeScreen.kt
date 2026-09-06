package za.org.rtc.community.feature.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.app.AuthenticationUiState
import za.org.rtc.community.app.PasswordUiState
import za.org.rtc.community.ui.auth.GoogleAuthDiagnosticScreen
import za.org.rtc.community.ui.auth.RtcGoogleSignInButton
import za.org.rtc.community.ui.components.RtcLogoMark
import za.org.rtc.community.ui.theme.RtcSpacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid Aurora Glass Quiet Canvas Welcome & Authentication experience.
 * Features slow, smooth, non-distracting animated perimeter aurora illumination
 * on a pitch-black canvas with high-contrast typography and tactile interactions.
 */
@Composable
internal fun PublicWelcomeScreen(
    authenticationUi: AuthenticationUiState,
    passwordUi: PasswordUiState,
    onSignIn: (String, String) -> Unit,
    onGoogleCredential: (String, String) -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onDismissAuthenticationMessage: () -> Unit,
    onRequestPasswordRecovery: (String) -> Unit,
    onDismissPasswordMessage: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val creatingAccount = mode == "CREATE"
    val isForgotPassword = mode == "FORGOT_PASSWORD"

    // Coordinated entry animation sequence
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // 1. Logo fades and scales in first
    val logoAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 40, easing = FastOutSlowInEasing),
        label = "logoAlpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = tween(durationMillis = 500, delayMillis = 40, easing = FastOutSlowInEasing),
        label = "logoScale",
    )

    // 2. App title and subtitle follow the logo
    val titleAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "titleAlpha",
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 14f,
        animationSpec = tween(durationMillis = 450, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "titleOffsetY",
    )

    // 3. Login and action buttons enter smoothly
    val actionsAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "actionsAlpha",
    )
    val actionsOffsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 20f,
        animationSpec = tween(durationMillis = 500, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "actionsOffsetY",
    )

    if (showDiagnostics && BuildConfig.DEBUG) {
        GoogleAuthDiagnosticScreen(onDismissRequest = { showDiagnostics = false })
    }

    Scaffold(
        containerColor = Color(0xFF000000),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            // Animated Liquid Aurora Glass with soft perimeter illumination
            LiquidAuroraGlassBackground(modifier = Modifier.fillMaxSize())

            if (mode == null) {
                // Main Quiet Canvas Welcome Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(0.7f))

                    // Hero Logo with subtle ambient backlight
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = logoAlpha
                                scaleX = logoScale
                                scaleY = logoScale
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        var tapCount by remember { mutableIntStateOf(0) }
                        RtcLogoMark(
                            size = 180.dp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (BuildConfig.DEBUG) {
                                        tapCount++
                                        if (tapCount >= 5) {
                                            tapCount = 0
                                            showDiagnostics = true
                                        }
                                    }
                                }
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Title and Tagline
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = titleAlpha
                                translationY = titleOffsetY
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "RTC ",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = "Community",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                ),
                                color = Color(0xFFE2E8F0),
                            )
                        }
                        Text(
                            text = "Your community, closer.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.2.sp,
                            ),
                            color = Color(0xFF94A3B8),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Action Buttons Container with Quiet Canvas Spacing
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = actionsAlpha
                                translationY = actionsOffsetY
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val haptic = LocalHapticFeedback.current

                        // 1. "Log in" Solid Pill Button
                        val logInInteraction = remember { MutableInteractionSource() }
                        val isLogInPressed by logInInteraction.collectIsPressedAsState()
                        LaunchedEffect(isLogInPressed) {
                            if (isLogInPressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        val logInScale by animateFloatAsState(
                            targetValue = if (isLogInPressed) 0.97f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "logInScale",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer {
                                    scaleX = logInScale
                                    scaleY = logInScale
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFAF7F2))
                                .clickable(
                                    interactionSource = logInInteraction,
                                    indication = ripple(bounded = false, radius = 220.dp, color = Color(0xFFE2E8F0)),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        mode = "SIGN_IN"
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Log in",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = Color(0xFF09090B),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. "Sign up" Dark Glass Outlined Button
                        val signUpInteraction = remember { MutableInteractionSource() }
                        val isSignUpPressed by signUpInteraction.collectIsPressedAsState()
                        LaunchedEffect(isSignUpPressed) {
                            if (isSignUpPressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        val signUpScale by animateFloatAsState(
                            targetValue = if (isSignUpPressed) 0.97f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "signUpScale",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer {
                                    scaleX = signUpScale
                                    scaleY = signUpScale
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A0C10).copy(alpha = 0.85f))
                                .clickable(
                                    interactionSource = signUpInteraction,
                                    indication = ripple(bounded = false, radius = 220.dp, color = Color.White.copy(alpha = 0.2f)),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        mode = "CREATE"
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Sign up",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                ),
                                color = Color.White,
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Divider: line - or - line
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF27272A),
                                thickness = 1.dp,
                            )
                            Text(
                                text = "or",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp,
                                ),
                                color = Color(0xFF71717A),
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF27272A),
                                thickness = 1.dp,
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 3. "Sign in with Google" Button
                        RtcGoogleSignInButton(
                            enabled = !authenticationUi.isWorking && !passwordUi.isWorking,
                            text = "Sign in with Google",
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF0A0C10).copy(alpha = 0.85f),
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(16.dp),
                            textColor = Color.White,
                            onCredential = onGoogleCredential,
                            onFailure = onGoogleSignInError,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))
                }
            } else {
                // Auth Forms (Sign In / Sign Up / Forgot Password) with Consistent Quiet Canvas Spacing
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (isForgotPassword) {
                                        mode = "SIGN_IN"
                                    } else {
                                        mode = null
                                    }
                                    onDismissAuthenticationMessage()
                                    onDismissPasswordMessage()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isForgotPassword) "Reset Password" else if (creatingAccount) "Create Account" else "Sign In",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                ),
                                color = Color.White,
                            )
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isForgotPassword) "Enter your registered email to receive recovery instructions."
                                else if (creatingAccount) "Join RTC Community with a verified account."
                                else "Sign in with your email address and password.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }

                    if (!isForgotPassword) {
                        item {
                            RtcGoogleSignInButton(
                                enabled = !authenticationUi.isWorking && !passwordUi.isWorking,
                                onCredential = onGoogleCredential,
                                onFailure = onGoogleSignInError,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF0F131D).copy(alpha = 0.8f),
                                    contentColor = Color.White,
                                ),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                shape = RoundedCornerShape(14.dp),
                                textColor = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                text = if (creatingAccount) "Sign up with Google" else "Sign in with Google",
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF27272A))
                                Text(
                                    text = if (creatingAccount) "or register with email" else "or continue with email",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF71717A),
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF27272A))
                            }
                        }
                    }

                    if (creatingAccount) {
                        item {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Your name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = darkTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email address") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = darkTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!isForgotPassword) {
                        item {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = darkTextFieldColors(),
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    val description = if (isPasswordVisible) "Hide password" else "Show password"
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(imageVector = image, contentDescription = description, tint = Color(0xFF94A3B8))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (creatingAccount) {
                        item {
                            DarkPasswordRequirementsChecklist(password = password)
                        }
                        item {
                            OutlinedTextField(
                                value = passwordConfirmation,
                                onValueChange = { passwordConfirmation = it },
                                label = { Text("Confirm password") },
                                supportingText = {
                                    if (passwordConfirmation.isNotEmpty() && passwordConfirmation != password) {
                                        Text("Passwords must match.", color = Color(0xFFF87171))
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = darkTextFieldColors(),
                                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val image = if (isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    val description = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                        Icon(imageVector = image, contentDescription = description, tint = Color(0xFF94A3B8))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (!creatingAccount && !isForgotPassword) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    enabled = !authenticationUi.isWorking && !passwordUi.isWorking,
                                    onClick = { mode = "FORGOT_PASSWORD" },
                                ) {
                                    Text("Forgot password?", color = Color(0xFFFBBF24))
                                }
                            }
                        }
                    }

                    authenticationUi.message?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = if (authenticationUi.confirmationRequired) Color(0xFFFBBF24) else Color(0xFFF87171),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    passwordUi.message?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = if (passwordUi.isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    val isCreatePasswordValid = password.length >= 8 &&
                            password.any(Char::isUpperCase) &&
                            password.any(Char::isDigit) &&
                            password.any { !it.isLetterOrDigit() }

                    item {
                        val isEnabled = !authenticationUi.isWorking && !passwordUi.isWorking && when {
                            isForgotPassword -> email.isNotBlank()
                            creatingAccount -> displayName.isNotBlank() && email.isNotBlank() && isCreatePasswordValid && password == passwordConfirmation
                            else -> email.isNotBlank() && password.isNotBlank()
                        }

                        Button(
                            enabled = isEnabled,
                            onClick = {
                                if (isForgotPassword) {
                                    onRequestPasswordRecovery(email)
                                } else if (creatingAccount) {
                                    onSignUp(email, password, displayName)
                                } else {
                                    onSignIn(email, password)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFAF7F2),
                                contentColor = Color(0xFF09090B),
                                disabledContainerColor = Color(0xFF27272A),
                                disabledContentColor = Color(0xFF71717A),
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            Text(
                                text = if (authenticationUi.isWorking || passwordUi.isWorking) "Please wait…"
                                else if (isForgotPassword) "Send recovery email"
                                else if (creatingAccount) "Create account"
                                else "Sign in",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (!isForgotPassword) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (creatingAccount) "Already have an account?" else "Don't have an account?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                )
                                TextButton(
                                    enabled = !authenticationUi.isWorking && !passwordUi.isWorking,
                                    onClick = {
                                        mode = if (creatingAccount) "SIGN_IN" else "CREATE"
                                        onDismissAuthenticationMessage()
                                        onDismissPasswordMessage()
                                    },
                                ) {
                                    Text(
                                        text = if (creatingAccount) "Sign in" else "Sign up",
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Liquid Aurora Glass with exact edge wave lines matching reference design.
 * Features ultra-slow, soothing lakeside wave undulation with soft illumination and dynamic dominant hue perimeter edge glow.
 */
@Composable
private fun LiquidAuroraGlassBackground(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAuroraSoothing")

    // Ultra-slow, soothing lakeside wave undulations (smooth 60fps continuous animation)
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase1",
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 64000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase2",
    )

    // Gentle color morphing phase (60-second ultra-slow cycle)
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "colorPhase",
    )

    // Steady, deep diaphragmatic breathing pulse (16s cycle)
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathingPhase",
    )

    // Gentle breeze gust phase (28-second wind swell cycle)
    val breezePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breezePhase",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Solid Pitch Black Canvas
        drawRect(color = Color(0xFF000000))

        // Steady diaphragmatic breathing pulse (0.0 to 1.0)
        val breath = ((sin(breathingPhase) + 1f) / 2f)

        // Toned-down, delicate aura width & subtle breathing glow
        val breathGlowAlpha = 0.02f + (0.02f * breath)
        val outerSoftAuraWidth = (12.dp + (4.dp * breath)).toPx()
        val outerSoftAuraAlpha = 0.08f + (0.04f * breath)
        val innerSoftAuraWidth = (5.dp + (2.dp * breath)).toPx()
        val innerSoftAuraAlpha = 0.18f + (0.08f * breath)
        val threadCoreWidth = (1.4.dp + (0.3.dp * breath)).toPx()

        // Dynamic Dominant Color Hue Calculation across full Aurora spectrum
        val colorProgress = ((sin(colorPhase) + 1f) / 2f).coerceIn(0f, 1f)
        val dominantColor = when {
            colorProgress < 0.14f -> {
                val t = colorProgress / 0.14f
                Color(red = 0xFB / 255f + (0xF5 - 0xFB) / 255f * t, green = 0xBF / 255f + (0x9E - 0xBF) / 255f * t, blue = 0x24 / 255f + (0x0B - 0x24) / 255f * t)
            }
            colorProgress < 0.28f -> {
                val t = (colorProgress - 0.14f) / 0.14f
                Color(red = 0xF5 / 255f + (0xEA - 0xF5) / 255f * t, green = 0x9E / 255f + (0x58 - 0x9E) / 255f * t, blue = 0x0B / 255f + (0x0C - 0x0B) / 255f * t)
            }
            colorProgress < 0.42f -> {
                val t = (colorProgress - 0.28f) / 0.14f
                Color(red = 0xEA / 255f + (0xDC - 0xEA) / 255f * t, green = 0x58 / 255f + (0x26 - 0x58) / 255f * t, blue = 0x0C / 255f + (0x26 - 0x0C) / 255f * t)
            }
            colorProgress < 0.56f -> {
                val t = (colorProgress - 0.42f) / 0.14f
                Color(red = 0xDC / 255f + (0xE1 - 0xDC) / 255f * t, green = 0x26 / 255f + (0x1D - 0x26) / 255f * t, blue = 0x26 / 255f + (0x48 - 0x26) / 255f * t)
            }
            colorProgress < 0.70f -> {
                val t = (colorProgress - 0.56f) / 0.14f
                Color(red = 0xE1 / 255f + (0x8B - 0xE1) / 255f * t, green = 0x1D / 255f + (0x5C - 0x1D) / 255f * t, blue = 0x48 / 255f + (0xF6 - 0x48) / 255f * t)
            }
            colorProgress < 0.84f -> {
                val t = (colorProgress - 0.70f) / 0.14f
                Color(red = 0x8B / 255f + (0x06 - 0x8B) / 255f * t, green = 0x5C / 255f + (0xB6 - 0x5C) / 255f * t, blue = 0xF6 / 255f + (0xD4 - 0xF6) / 255f * t)
            }
            else -> {
                val t = (colorProgress - 0.84f) / 0.16f
                Color(red = 0x06 / 255f + (0x10 - 0x06) / 255f * t, green = 0xB6 / 255f + (0xB9 - 0xB6) / 255f * t, blue = 0xD4 / 255f + (0x81 - 0xD4) / 255f * t)
            }
        }

        // 2. Harmonious Ambient Screen Perimeter Edge Glow cast by Dominant Hue with breathing pulse
        drawRect(
            brush = Brush.radialGradient(
                0.0f to dominantColor.copy(alpha = breathGlowAlpha + 0.03f),
                0.5f to dominantColor.copy(alpha = breathGlowAlpha * 0.5f),
                1.0f to Color.Transparent,
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.90f,
            ),
            size = size,
        )

        // Minimized Edge Water Reflections (Imitates light reflecting off water surface with ultra-soft, low brightness)
        val waterReflectAlpha = 0.03f + (0.02f * breath)
        val leftWaterReflect = Brush.horizontalGradient(
            0.0f to dominantColor.copy(alpha = waterReflectAlpha * 1.6f),
            0.015f to dominantColor.copy(alpha = waterReflectAlpha),
            0.045f to Color.Transparent,
            startX = 0f,
            endX = w * 0.045f,
        )
        drawRect(brush = leftWaterReflect, size = size)

        val rightWaterReflect = Brush.horizontalGradient(
            0.955f to Color.Transparent,
            0.985f to dominantColor.copy(alpha = waterReflectAlpha),
            1.0f to dominantColor.copy(alpha = waterReflectAlpha * 1.6f),
            startX = w * 0.955f,
            endX = w,
        )
        drawRect(brush = rightWaterReflect, size = size)

        // Subtle top & bottom lake edge water glimmers
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to dominantColor.copy(alpha = waterReflectAlpha * 1.3f),
                0.04f to Color.Transparent,
                startY = 0f,
                endY = h * 0.04f,
            ),
            size = size,
        )
        drawRect(
            brush = Brush.verticalGradient(
                0.96f to Color.Transparent,
                1.0f to dominantColor.copy(alpha = waterReflectAlpha * 1.3f),
                startY = h * 0.96f,
                endY = h,
            ),
            size = size,
        )

        // 3. Ultra-subtle central warm radial bloom behind logo
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to dominantColor.copy(alpha = breathGlowAlpha + 0.02f),
                0.5f to Color(0xFFDC2626).copy(alpha = 0.02f),
                1.0f to Color.Transparent,
                center = Offset(w * 0.5f, h * 0.28f),
                radius = w * 0.60f,
            ),
            radius = w * 0.60f,
            center = Offset(w * 0.5f, h * 0.28f),
        )

        // Color shift & gentle breeze parameters
        val colorShift = sin(colorPhase) * 0.08f
        val breezeGust = (sin(breezePhase) * 0.5f + 0.5f) // Smooth 0.0 to 1.0 wind swell intensity

        // -------------------------------------------------------------
        // LEFT EDGE: Dynamic Full-Spectrum Aurora Wave Thread & Aura (Gentle Breeze Wave)
        // -------------------------------------------------------------
        val leftPoints = mutableListOf<Offset>()
        val leftReflectionPoints = mutableListOf<Offset>()
        val step = 10f
        var y = 0f
        while (y <= h) {
            val normY = y / h
            // Primary ultra-slow wave
            val wave1 = sin(normY * 1.5f * PI.toFloat() + wavePhase1) * (w * 0.009f)
            // Travelling breeze ripple modulated by breeze gust swell
            val breezeWave = sin(normY * 1.8f * PI.toFloat() - wavePhase2 + breezePhase) * (w * 0.007f * (0.5f + 0.8f * breezeGust))
            // Soft wind bowing displacement (thread gently arches inward/outward as wind passes)
            val windBow = sin(normY * PI.toFloat() + breezePhase * 0.5f) * (w * 0.005f * breezeGust)
            val x = (w * 0.016f) + wave1 + breezeWave + windBow
            leftPoints.add(Offset(x, y))
            leftReflectionPoints.add(Offset(-x, y))
            y += step
        }

        val leftPath = Path().apply {
            if (leftPoints.isNotEmpty()) {
                moveTo(leftPoints[0].x, leftPoints[0].y)
                for (i in 1 until leftPoints.size) {
                    lineTo(leftPoints[i].x, leftPoints[i].y)
                }
            }
        }
        val leftReflectionPath = Path().apply {
            if (leftReflectionPoints.isNotEmpty()) {
                moveTo(leftReflectionPoints[0].x, leftReflectionPoints[0].y)
                for (i in 1 until leftReflectionPoints.size) {
                    lineTo(leftReflectionPoints[i].x, leftReflectionPoints[i].y)
                }
            }
        }

        val leftGradient = Brush.verticalGradient(
            0.00f to Color(0xFFFBBF24).copy(alpha = 0.95f), // Sun Gold
            (0.15f + colorShift).coerceIn(0.08f, 0.28f) to Color(0xFFF59E0B).copy(alpha = 0.92f), // Golden Amber
            (0.30f + colorShift).coerceIn(0.20f, 0.42f) to Color(0xFFEA580C).copy(alpha = 0.90f), // Flame Orange
            (0.45f + colorShift).coerceIn(0.35f, 0.58f) to Color(0xFFDC2626).copy(alpha = 0.88f), // Sun Core Red
            (0.60f + colorShift).coerceIn(0.50f, 0.72f) to Color(0xFFE11D48).copy(alpha = 0.88f), // Rose Pink
            (0.75f + colorShift).coerceIn(0.65f, 0.85f) to Color(0xFF8B5CF6).copy(alpha = 0.88f), // Aurora Violet
            (0.88f + colorShift).coerceIn(0.78f, 0.95f) to Color(0xFF06B6D4).copy(alpha = 0.88f), // Electric Cyan
            1.00f to Color(0xFF10B981).copy(alpha = 0.90f), // Emerald Green
        )

        // Delicate Soft Breathing Multi-Layer Aura & Sleek Inner Core Thread
        // 0. Mirrored dynamic edge reflection
        drawPath(
            path = leftReflectionPath,
            brush = leftGradient,
            style = Stroke(width = outerSoftAuraWidth * 1.5f, cap = StrokeCap.Round),
            alpha = outerSoftAuraAlpha * 1.2f,
        )
        // 1. Broad soft diffuse aura
        drawPath(
            path = leftPath,
            brush = leftGradient,
            style = Stroke(width = outerSoftAuraWidth, cap = StrokeCap.Round),
            alpha = outerSoftAuraAlpha,
        )
        // 2. Focused mid soft aura
        drawPath(
            path = leftPath,
            brush = leftGradient,
            style = Stroke(width = innerSoftAuraWidth, cap = StrokeCap.Round),
            alpha = innerSoftAuraAlpha,
        )
        // 3. Sleek core glass thread
        drawPath(
            path = leftPath,
            brush = leftGradient,
            style = Stroke(width = threadCoreWidth, cap = StrokeCap.Round),
            alpha = 0.95f,
        )

        // -------------------------------------------------------------
        // RIGHT EDGE: Dynamic Full-Spectrum Aurora Wave Thread & Aura (Gentle Breeze Wave)
        // -------------------------------------------------------------
        val rightPoints = mutableListOf<Offset>()
        val rightReflectionPoints = mutableListOf<Offset>()
        y = 0f
        while (y <= h) {
            val normY = y / h
            // Primary ultra-slow wave
            val wave1 = sin(normY * 1.6f * PI.toFloat() - wavePhase2) * (w * 0.010f)
            // Travelling breeze ripple
            val breezeWave = cos(normY * 1.9f * PI.toFloat() + wavePhase1 - breezePhase) * (w * 0.007f * (0.5f + 0.8f * breezeGust))
            // Soft wind bowing displacement
            val windBow = cos(normY * PI.toFloat() - breezePhase * 0.5f) * (w * 0.005f * breezeGust)
            val x = w - (w * 0.016f) + wave1 + breezeWave + windBow
            rightPoints.add(Offset(x, y))
            rightReflectionPoints.add(Offset(w + (w - x), y))
            y += step
        }

        val rightPath = Path().apply {
            if (rightPoints.isNotEmpty()) {
                moveTo(rightPoints[0].x, rightPoints[0].y)
                for (i in 1 until rightPoints.size) {
                    lineTo(rightPoints[i].x, rightPoints[i].y)
                }
            }
        }
        val rightReflectionPath = Path().apply {
            if (rightReflectionPoints.isNotEmpty()) {
                moveTo(rightReflectionPoints[0].x, rightReflectionPoints[0].y)
                for (i in 1 until rightReflectionPoints.size) {
                    lineTo(rightReflectionPoints[i].x, rightReflectionPoints[i].y)
                }
            }
        }

        val rightGradient = Brush.verticalGradient(
            0.00f to Color(0xFF8B5CF6).copy(alpha = 0.95f), // Aurora Violet
            (0.15f - colorShift).coerceIn(0.08f, 0.28f) to Color(0xFF06B6D4).copy(alpha = 0.92f), // Electric Cyan
            (0.30f - colorShift).coerceIn(0.20f, 0.42f) to Color(0xFF10B981).copy(alpha = 0.90f), // Emerald Green
            (0.45f - colorShift).coerceIn(0.35f, 0.58f) to Color(0xFFFBBF24).copy(alpha = 0.88f), // Sun Gold
            (0.60f - colorShift).coerceIn(0.50f, 0.72f) to Color(0xFFF59E0B).copy(alpha = 0.88f), // Golden Amber
            (0.75f - colorShift).coerceIn(0.65f, 0.85f) to Color(0xFFEA580C).copy(alpha = 0.88f), // Flame Orange
            (0.88f - colorShift).coerceIn(0.78f, 0.95f) to Color(0xFFDC2626).copy(alpha = 0.88f), // Sun Core Red
            1.00f to Color(0xFFE11D48).copy(alpha = 0.90f), // Rose Pink
        )

        // Delicate Soft Breathing Multi-Layer Aura & Sleek Inner Core Thread
        // 0. Mirrored dynamic edge reflection
        drawPath(
            path = rightReflectionPath,
            brush = rightGradient,
            style = Stroke(width = outerSoftAuraWidth * 1.5f, cap = StrokeCap.Round),
            alpha = outerSoftAuraAlpha * 1.2f,
        )
        // 1. Broad soft diffuse aura
        drawPath(
            path = rightPath,
            brush = rightGradient,
            style = Stroke(width = outerSoftAuraWidth, cap = StrokeCap.Round),
            alpha = outerSoftAuraAlpha,
        )
        // 2. Focused mid soft aura
        drawPath(
            path = rightPath,
            brush = rightGradient,
            style = Stroke(width = innerSoftAuraWidth, cap = StrokeCap.Round),
            alpha = innerSoftAuraAlpha,
        )
        // 3. Sleek core glass thread
        drawPath(
            path = rightPath,
            brush = rightGradient,
            style = Stroke(width = threadCoreWidth, cap = StrokeCap.Round),
            alpha = 0.95f,
        )
    }
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF090B10),
    unfocusedContainerColor = Color(0xFF090B10),
    disabledContainerColor = Color(0xFF090B10),
    focusedBorderColor = Color(0xFFFBBF24),
    unfocusedBorderColor = Color(0xFF334155),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFFFBBF24),
    unfocusedLabelColor = Color(0xFF94A3B8),
    cursorColor = Color(0xFFFBBF24),
)

@Composable
private fun DarkPasswordRequirementsChecklist(
    password: String,
    modifier: Modifier = Modifier,
) {
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any(Char::isUpperCase)
    val hasNumber = password.any(Char::isDigit)
    val hasSymbol = password.any { !it.isLetterOrDigit() }

    val metCount = listOf(hasMinLength, hasUppercase, hasNumber, hasSymbol).count { it }
    val progress = metCount / 4f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F131D).copy(alpha = 0.7f),
        border = BorderStroke(
            width = 1.dp,
            color = if (metCount == 4) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF334155),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Password requirements",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$metCount of 4 fulfilled",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (metCount == 4) Color(0xFF34D399) else Color(0xFF94A3B8),
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = if (metCount == 4) Color(0xFF34D399) else Color(0xFFFBBF24),
                trackColor = Color(0xFF1E293B),
            )

            Spacer(modifier = Modifier.height(2.dp))

            DarkRequirementCheckItem(fulfilled = hasMinLength, label = "At least 8 characters")
            DarkRequirementCheckItem(fulfilled = hasUppercase, label = "Upper case letters (A-Z)")
            DarkRequirementCheckItem(fulfilled = hasNumber, label = "At least one number (0-9)")
            DarkRequirementCheckItem(fulfilled = hasSymbol, label = "At least one symbol (!@#$%...)")
        }
    }
}

@Composable
private fun DarkRequirementCheckItem(
    fulfilled: Boolean,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (fulfilled) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (fulfilled) "Requirement met" else "Requirement missing",
            tint = if (fulfilled) Color(0xFF34D399) else Color(0xFF475569),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (fulfilled) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
            fontWeight = if (fulfilled) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
