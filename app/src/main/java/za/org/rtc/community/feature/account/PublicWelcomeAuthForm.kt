package za.org.rtc.community.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.app.AuthenticationUiState
import za.org.rtc.community.app.PasswordUiState
import za.org.rtc.community.ui.auth.RtcGoogleSignInButton

@Composable
internal fun PublicWelcomeAuthForm(
    mode: String?,
    onModeChange: (String?) -> Unit,
    padding: PaddingValues,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordConfirmation: String,
    onPasswordConfirmationChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    isConfirmPasswordVisible: Boolean,
    onToggleConfirmPasswordVisibility: () -> Unit,
    authenticationUi: AuthenticationUiState,
    passwordUi: PasswordUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onRequestPasswordRecovery: (String) -> Unit,
    onGoogleCredential: (String, String) -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onDismissAuthenticationMessage: () -> Unit,
    onDismissPasswordMessage: () -> Unit,
) {
    val creatingAccount = mode == "CREATE"
    val isForgotPassword = mode == "FORGOT_PASSWORD"

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
                            onModeChange("SIGN_IN")
                        } else {
                            onModeChange(null)
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
                    onValueChange = onDisplayNameChange,
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
                onValueChange = onEmailChange,
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
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = darkTextFieldColors(),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (isPasswordVisible) "Hide password" else "Show password"
                        IconButton(onClick = onTogglePasswordVisibility) {
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
                    onValueChange = onPasswordConfirmationChange,
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
                        IconButton(onClick = onToggleConfirmPasswordVisibility) {
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
                        onClick = { onModeChange("FORGOT_PASSWORD") },
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
                            onModeChange(if (creatingAccount) "SIGN_IN" else "CREATE")
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
