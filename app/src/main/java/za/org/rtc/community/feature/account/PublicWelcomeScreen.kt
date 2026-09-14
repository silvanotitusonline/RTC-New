package za.org.rtc.community.feature.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
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
import za.org.rtc.community.core.LaunchTreatment
import za.org.rtc.community.ui.auth.GoogleAuthDiagnosticScreen
import za.org.rtc.community.ui.auth.RtcGoogleSignInButton
import za.org.rtc.community.ui.components.RtcLogoMark
import za.org.rtc.community.ui.config.LocalRtcUiConfiguration

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
    onContinueAsGuest: () -> Unit = {},
) {
    val welcome = LocalRtcUiConfiguration.current.welcome
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

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val logoAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500, 40, FastOutSlowInEasing),
        label = "logoAlpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = tween(500, 40, FastOutSlowInEasing),
        label = "logoScale",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(450, 200, FastOutSlowInEasing),
        label = "titleAlpha",
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 14f,
        animationSpec = tween(450, 200, FastOutSlowInEasing),
        label = "titleOffsetY",
    )
    val actionsAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500, 350, FastOutSlowInEasing),
        label = "actionsAlpha",
    )
    val actionsOffsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 20f,
        animationSpec = tween(500, 350, FastOutSlowInEasing),
        label = "actionsOffsetY",
    )

    if (showDiagnostics && BuildConfig.DEBUG) {
        GoogleAuthDiagnosticScreen(onDismissRequest = { showDiagnostics = false })
    }

    Scaffold(containerColor = Color(0xFF000000)) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000))) {
            when (welcome.launchTreatment) {
                LaunchTreatment.IMMERSIVE -> LiquidAuroraGlassBackground(modifier = Modifier.fillMaxSize())
                LaunchTreatment.MINIMAL -> Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                LaunchTreatment.BRAND_PANEL -> LiquidAuroraGlassBackground(modifier = Modifier.fillMaxSize())
            }

            if (mode == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(0.7f))

                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = logoAlpha
                            scaleX = logoScale
                            scaleY = logoScale
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        var tapCount by remember { mutableIntStateOf(0) }
                        // Transparent mark: R.drawable.rtc_logo_mark_transparent
                        RtcLogoMark(
                            size = 180.dp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (BuildConfig.DEBUG && ++tapCount >= 5) {
                                        tapCount = 0
                                        showDiagnostics = true
                                    }
                                }
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Column(
                        modifier = Modifier.graphicsLayer {
                            alpha = titleAlpha
                            translationY = titleOffsetY
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = welcome.headline.ifBlank { "RTC Community" },
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = welcome.supportingText.ifBlank { "Your community, closer." },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.2.sp,
                            ),
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = actionsAlpha
                            translationY = actionsOffsetY
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val haptic = LocalHapticFeedback.current
                        val logInInteraction = remember { MutableInteractionSource() }
                        val isLogInPressed by logInInteraction.collectIsPressedAsState()
                        LaunchedEffect(isLogInPressed) {
                            if (isLogInPressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        val logInScale by animateFloatAsState(
                            targetValue = if (isLogInPressed) 0.97f else 1f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                            label = "logInScale",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer { scaleX = logInScale; scaleY = logInScale }
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFAF7F2))
                                .clickable(
                                    interactionSource = logInInteraction,
                                    indication = ripple(false, 220.dp, Color(0xFFE2E8F0)),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        mode = "CREATE"
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = welcome.primaryActionLabel.ifBlank { "Get started" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                color = Color(0xFF09090B),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val signUpInteraction = remember { MutableInteractionSource() }
                        val isSignUpPressed by signUpInteraction.collectIsPressedAsState()
                        LaunchedEffect(isSignUpPressed) {
                            if (isSignUpPressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        val signUpScale by animateFloatAsState(
                            targetValue = if (isSignUpPressed) 0.97f else 1f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                            label = "signUpScale",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer { scaleX = signUpScale; scaleY = signUpScale }
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A0C10).copy(alpha = 0.85f))
                                .clickable(
                                    interactionSource = signUpInteraction,
                                    indication = ripple(false, 220.dp, Color.White.copy(alpha = 0.2f)),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        mode = "SIGN_IN"
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = welcome.secondaryActionLabel.ifBlank { "Sign in" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                                color = Color.White,
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF27272A), thickness = 1.dp)
                            Text("or", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color(0xFF71717A))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF27272A), thickness = 1.dp)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

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
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        TextButton(
                            onClick = onContinueAsGuest,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(
                                text = "Explore as Guest",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                ),
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
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
                                    if (isForgotPassword) mode = "SIGN_IN" else mode = null
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
                                text = if (isForgotPassword) "Reset Password" else if (creatingAccount) "Create your RTC account" else "Sign in to RTC Community",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                                color = Color.White,
                            )
                        }
                    }

                    item {
                        Text(
                            text = if (isForgotPassword) "Enter your registered email to receive recovery instructions."
                            else if (creatingAccount) "Join RTC Community with a verified account."
                            else "Sign in with your email address and password.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                            color = Color(0xFF94A3B8),
                        )
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
                                modifier = Modifier.fillMaxWidth().height(50.dp),
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
                                    val icon = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(imageVector = icon, contentDescription = if (isPasswordVisible) "Hide password" else "Show password", tint = Color(0xFF94A3B8))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (creatingAccount) {
                        item { DarkPasswordRequirementsChecklist(password = password) }
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
                                    val icon = if (isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                        Icon(imageVector = icon, contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password", tint = Color(0xFF94A3B8))
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
                            modifier = Modifier.fillMaxWidth().height(50.dp),
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
