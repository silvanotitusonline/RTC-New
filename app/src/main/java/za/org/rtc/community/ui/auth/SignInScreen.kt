package za.org.rtc.community.ui.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale

import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import za.org.rtc.community.ui.components.RtcBrandLockup
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing
import java.security.MessageDigest
import java.util.UUID

private const val TAG = "SignInScreen"

/**
 * SignInScreen composable providing email/password authentication with real-time StateFlow
 * input validation, Remember Me checkbox persisted to DataStore, loading indicators with
 * button disabling, Forgot Password link to Supabase password recovery flow, and Google Sign-In.
 */
@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit = {},
    onDismissToGuest: (() -> Unit)? = null,
    onNavigateToForgotPassword: ((email: String) -> Unit)? = null,
    viewModel: AuthStateViewModel = hiltViewModel(),
    formViewModel: SignInFormViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by formViewModel.formState.collectAsStateWithLifecycle()

    var isSigningInLocally by remember { mutableStateOf(false) }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var showForgotPasswordModal by rememberSaveable { mutableStateOf(false) }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("SignInScreen", "Google Sign-In intent returned. ResultCode: ${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                Log.i("SignInScreen", "Google Sign-In account selection successful. Exchanging token...")
                AuthenticationLogger.logState(
                    AuthenticationLogger.TransitionState.STARTED,
                    "Google account selected successfully. Starting verification."
                )
                AuthDiagnosticLogger.logSuccess()
                isSigningInLocally = false // Hand over loading state to ViewModel
                viewModel.signInWithGoogle(idToken, "")
            } else {
                isSigningInLocally = false
                Log.e("SignInScreen", "Google Sign-In failed: idToken is null")
                AuthenticationLogger.logState(
                    AuthenticationLogger.TransitionState.FAILURE,
                    "idToken was null in the Google sign-in response."
                )
                coroutineScope.launch { snackbarHostState.showSnackbar("No ID token found in Google Sign-In response.") }
            }
        } catch (e: ApiException) {
            isSigningInLocally = false
            Log.e("SignInScreen", "Google Sign-In API Exception. Status Code: ${e.statusCode}", e)
            AuthenticationLogger.logState(
                AuthenticationLogger.TransitionState.FAILURE,
                "Google Sign-In API Exception: Code ${e.statusCode} | Msg: ${e.message}"
            )
            if (e.statusCode != 12501 && e.statusCode != 0) { // 12501 is cancelled
                val errorMsg = if (e.statusCode == 10) {
                    "Code 10: App SHA-1 fingerprint not registered in Google Cloud Console"
                } else {
                    "Google sign-in failed. Error Code: ${e.statusCode}"
                }
                coroutineScope.launch { snackbarHostState.showSnackbar(errorMsg) }
                AuthDiagnosticLogger.logError("GOOGLE_SIGN_IN_ERROR", "Status Code: ${e.statusCode} Msg: ${e.message ?: e.toString()}")
            } else if (result.resultCode != android.app.Activity.RESULT_OK && e.statusCode != 12501) {
                Log.w("SignInScreen", "Sign-in intent canceled by user or OS.")
            }
        } catch (e: Exception) {
            isSigningInLocally = false
            Log.e("SignInScreen", "Unexpected error parsing Google Sign-In intent", e)
            AuthenticationLogger.logState(
                AuthenticationLogger.TransitionState.FAILURE,
                "Unexpected error: ${e.message}"
            )
            coroutineScope.launch { snackbarHostState.showSnackbar("Unexpected authentication error.") }
        }
    }


    // Reactively notify on successful authentication
    LaunchedEffect(uiState.authStatus) {
        if (uiState.authStatus is AuthStatus.Authenticated) {
            onSignInSuccess()
        }
    }

    // Display error message in snackbar if present
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            isSigningInLocally = false
            coroutineScope.launch { snackbarHostState.showSnackbar(error) }
            viewModel.clearError()
        }
    }

    if (showForgotPasswordModal) {
        ForgotPasswordScreen(
            initialEmail = formState.email,
            onNavigateBack = { showForgotPasswordModal = false },
            viewModel = viewModel
        )
        return
    }

    val isAnyLoading = uiState.isLoading || isSigningInLocally

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RtcSpacing.cardPadding, vertical = RtcSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App Branding & Title
            RtcBrandLockup(
                modifier = Modifier.padding(bottom = RtcSpacing.section)
            )

            RtcCard(
                protected = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_in_card")
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.standard),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Secure Authentication",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )

                    Text(
                        text = "Sign in to your account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Access community updates, municipal reports, marketplace services, and administrative workspaces with verified credentials.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(RtcSpacing.compact))

                    // Email Input Field with StateFlow real-time validation error
                    OutlinedTextField(
                        value = formState.email,
                        onValueChange = { formViewModel.onEmailChanged(it) },
                        label = { Text("Email address") },
                        placeholder = { Text("resident@example.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = "Email Icon"
                            )
                        },
                        isError = formState.emailError != null,
                        supportingText = {
                            if (formState.emailError != null) {
                                Text(
                                    text = formState.emailError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("email_error_message")
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isAnyLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    // Password Input Field with StateFlow complexity validation error
                    OutlinedTextField(
                        value = formState.password,
                        onValueChange = { formViewModel.onPasswordChanged(it) },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Password Icon"
                            )
                        },
                        trailingIcon = {
                            val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (isPasswordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        isError = formState.passwordError != null,
                        supportingText = {
                            if (formState.passwordError != null) {
                                Text(
                                    text = formState.passwordError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("password_error_message")
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isAnyLoading,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                formViewModel.markAllTouched()
                                if (formState.isFormValid && !isAnyLoading) {
                                    viewModel.signInWithEmail(
                                        email = formState.email,
                                        password = formState.password,
                                        rememberMe = formState.rememberMe
                                    )
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    // Remember Me Checkbox & Forgot Password Link row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("remember_me_container")
                        ) {
                            Checkbox(
                                checked = formState.rememberMe,
                                onCheckedChange = { formViewModel.onRememberMeChanged(it) },
                                enabled = !isAnyLoading,
                                modifier = Modifier.testTag("remember_me_checkbox")
                            )
                            Text(
                                text = "Remember me",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = {
                                if (onNavigateToForgotPassword != null) {
                                    onNavigateToForgotPassword(formState.email)
                                } else {
                                    showForgotPasswordModal = true
                                }
                            },
                            enabled = !isAnyLoading,
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Email / Password Login Button with CircularProgressIndicator and disabled state
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            formViewModel.markAllTouched()
                            if (formState.isFormValid && !isAnyLoading) {
                                viewModel.signInWithEmail(
                                    email = formState.email,
                                    password = formState.password,
                                    rememberMe = formState.rememberMe
                                )
                            }
                        },
                        enabled = formState.isFormValid && !isAnyLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_in_submit_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(22.dp)
                                    .testTag("login_progress_indicator"),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Signing in...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Sign In", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Divider for Alternative Login Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = RtcSpacing.compact),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "or continue with",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = RtcSpacing.standard)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Google Sign-In Button utilizing androidx.credentials
                    GoogleCredentialSignInButton(
                        isLoading = isSigningInLocally,
                        enabled = !isAnyLoading,
                        onClick = {
                            AuthenticationLogger.logState(
                                AuthenticationLogger.TransitionState.STARTED,
                                "Google Sign-In flow initiated by button click."
                            )
                            isSigningInLocally = true
                            if (!GooglePlayServicesHelper.isPlayServicesAvailable(context)) {
                                isSigningInLocally = false
                                val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                                val resultCode = availability.isGooglePlayServicesAvailable(context)
                                val activity = context as? android.app.Activity
                                if (activity != null && availability.isUserResolvableError(resultCode)) {
                                    availability.showErrorDialogFragment(activity, resultCode, 9001)
                                } else {
                                    // Should show snackbar but we're out of scope for suspend, so just ignore or set error state
                                }
                                return@GoogleCredentialSignInButton
                            }
                            
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(RTC_GOOGLE_WEB_CLIENT_ID)
                                .requestEmail()
                                .requestProfile()
                                .build()
                                
                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
                            googleSignInClient.signOut().addOnCompleteListener(mainExecutor) {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_sign_in_button")
                    )

                    if (onDismissToGuest != null) {
                        OutlinedButton(
                            onClick = onDismissToGuest,
                            enabled = !isAnyLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("continue_as_guest_button")
                        ) {
                            Text("Continue as Guest")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(RtcSpacing.standard))

            // Security assurance footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.tiny),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Encrypted Connection",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "End-to-end encrypted session with Supabase GoTrue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (uiState.isAuthenticating) {
                BreathingLoaderOverlay()
            }
        }
    }
}
}

/**
 * Custom styled button following Google Sign-In visual guidelines with
 * CredentialManager integration feedback.
 */
@Composable
private fun GoogleCredentialSignInButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF131314),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF131314).copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Signing in with Google...", fontWeight = FontWeight.SemiBold)
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF4285F4)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * A full-screen semi-transparent overlay with a 'breathing' animation circle
 * that displays during Google token validation.
 */
@Composable
private fun BreathingLoaderOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {}, // Scrim/interactable blocker
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Outer breathing glow circle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(scale)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
                // Middle solid ring
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Authenticating with Google...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Exchanging security tokens safely",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

