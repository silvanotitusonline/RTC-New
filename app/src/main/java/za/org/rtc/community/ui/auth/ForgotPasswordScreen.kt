package za.org.rtc.community.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import za.org.rtc.community.ui.components.RtcBrandLockup
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Validates whether an email string follows standard email pattern.
 */
private fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    return trimmed.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
}

/**
 * Screen that handles password recovery by calling Supabase Auth's resetPasswordForEmail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    initialEmail: String = "",
    onNavigateBack: () -> Unit,
    viewModel: AuthStateViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var isSubmittedSuccess by rememberSaveable { mutableStateOf(false) }

    val emailError: String? = when {
        !emailTouched -> null
        email.isBlank() -> "Email address is required"
        !isValidEmail(email) -> "Please enter a valid email address (e.g., name@example.com)"
        else -> null
    }

    val isEmailValid = email.isNotBlank() && isValidEmail(email)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Recovery", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("forgot_password_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Sign In"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RtcSpacing.cardPadding, vertical = RtcSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            RtcBrandLockup(
                compact = true,
                modifier = Modifier.padding(bottom = RtcSpacing.standard)
            )

            RtcCard(
                protected = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_password_card")
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.standard),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)
                ) {
                    if (isSubmittedSuccess) {
                        // Success state after triggering resetPasswordForEmail
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Reset Link Sent",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )

                        Text(
                            text = "Check your inbox",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "If an account exists for $email, we've sent password reset instructions with a secure link to reset your password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(RtcSpacing.small))

                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("return_to_sign_in_button")
                        ) {
                            Text("Back to Sign In")
                        }

                        OutlinedButton(
                            onClick = {
                                isSubmittedSuccess = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("try_another_email_button")
                        ) {
                            Text("Try Another Email")
                        }
                    } else {
                        // Form state
                        Icon(
                            imageVector = Icons.Filled.LockReset,
                            contentDescription = "Reset Password",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )

                        Text(
                            text = "Reset your password",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Enter the email associated with your RTC Community account. We'll send you instructions to create a new password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                emailTouched = true
                            },
                            label = { Text("Email address") },
                            placeholder = { Text("resident@example.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = "Email Icon"
                                )
                            },
                            isError = emailError != null,
                            supportingText = {
                                if (emailError != null) {
                                    Text(
                                        text = emailError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("forgot_password_email_error")
                                    )
                                }
                            },
                            singleLine = true,
                            enabled = !uiState.isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isEmailValid && !uiState.isLoading) {
                                        viewModel.requestPasswordReset(email) { result ->
                                            result.fold(
                                                onSuccess = { isSubmittedSuccess = true },
                                                onFailure = { error ->
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            error.localizedMessage ?: "Failed to send reset link."
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_password_email_input")
                        )

                        Button(
                            onClick = {
                                emailTouched = true
                                if (isEmailValid && !uiState.isLoading) {
                                    viewModel.requestPasswordReset(email) { result ->
                                        result.fold(
                                            onSuccess = { isSubmittedSuccess = true },
                                            onFailure = { error ->
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        error.localizedMessage ?: "Failed to send reset link."
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = isEmailValid && !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("send_reset_link_button")
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending reset link...")
                            } else {
                                Text("Send Reset Link")
                            }
                        }

                        OutlinedButton(
                            onClick = onNavigateBack,
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cancel_reset_button")
                        ) {
                            Text("Back to Sign In")
                        }
                    }
                }
            }
        }
    }
}
