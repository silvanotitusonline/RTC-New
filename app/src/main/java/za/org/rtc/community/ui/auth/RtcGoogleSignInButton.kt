package za.org.rtc.community.ui.auth

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

/**
 * Custom 4-color Google 'G' brand mark for authentic identity recognition.
 */
@Composable
fun GoogleBrandIcon(modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val strokeWidth = w * 0.22f
        val center = Offset(w / 2f, size.height / 2f)
        val radius = (w - strokeWidth) / 2f

        // Blue arc (right/top-right)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green arc (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow arc (bottom-left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Red arc (top-left)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue horizontal bar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(center.x, center.y),
            end = Offset(center.x + radius + strokeWidth / 2f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Square
        )
    }
}

/**
 * Public OAuth client identifier for RTC Community's native Google sign-in flow.
 */
internal const val RTC_GOOGLE_WEB_CLIENT_ID =
    "281489677261-j6isgtjd4mqv4os6fakt2qeloogpav8p.apps.googleusercontent.com"

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

@Composable
fun RtcGoogleSignInButton(
    enabled: Boolean,
    onCredential: (idToken: String, rawNonce: String) -> Unit,
    onFailure: (message: String) -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Continue with Google",
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    textColor: Color? = null,
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val coroutineScope = rememberCoroutineScope()

    // Detailed error states
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorType by remember { mutableStateOf("UNKNOWN") } // NETWORK, CONFIGURATION, UNKNOWN
    var errorMessageText by remember { mutableStateOf("") }
    var rawExceptionMessage by remember { mutableStateOf("") }

    fun triggerGoogleSignIn() {
        AuthDiagnosticLogger.logAttempt()

        if (!GooglePlayServicesHelper.isPlayServicesAvailable(context)) {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            val activity = context as? android.app.Activity
            if (activity != null && availability.isUserResolvableError(resultCode)) {
                availability.showErrorDialogFragment(activity, resultCode, 9001)
                return
            }
        }

        coroutineScope.launch {
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = sha256Hex(rawNonce)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(RTC_GOOGLE_WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .setAutoSelectEnabled(false)
                .associateLinkedAccounts(RTC_GOOGLE_WEB_CLIENT_ID, listOf("email", "profile"))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(
                    context = context,
                    request = request,
                )
                if (result.credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    errorType = "UNKNOWN"
                    errorMessageText = "Received an unsupported credential type."
                    showErrorDialog = true
                    return@launch
                }
                val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                AuthDiagnosticLogger.logSuccess()
                onCredential(googleCredential.idToken, rawNonce)
            } catch (e: Exception) {
                rawExceptionMessage = e.localizedMessage ?: e.message ?: e.toString()
                val className = e.javaClass.simpleName
                
                // Identify Cancellation: User explicitly dismissed bottom sheet or credential picker
                if (className.contains("Cancellation", ignoreCase = true) || 
                    rawExceptionMessage.contains("cancel", ignoreCase = true)
                ) {
                    AuthDiagnosticLogger.logError("CANCELLED", rawExceptionMessage)
                    // Do not show an intrusive error dialog for simple user cancellations, just trigger onFailure
                    onFailure("Google Sign-In was cancelled.")
                    return@launch
                }

                // Identify No Accounts / Credentials Found:
                // User requirement: Do NOT make the "No Google Accounts" error message pop up.
                // Instead, trigger the native Android prompt or in-app redirection to sign into and authorize their Google account.
                if (className.contains("NoCredential", ignoreCase = true) ||
                    rawExceptionMessage.contains("no credential", ignoreCase = true) ||
                    rawExceptionMessage.contains("no account", ignoreCase = true) ||
                    className.contains("NoAccount", ignoreCase = true)
                ) {
                    AuthDiagnosticLogger.logError("PROMPTING_GOOGLE_ACCOUNT_SETUP", rawExceptionMessage)
                    try {
                        val addAccountIntent = android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                            putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(addAccountIntent)
                    } catch (eAdd: Exception) {
                        try {
                            val syncIntent = android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(syncIntent)
                        } catch (_: Exception) {
                            try {
                                val webIntent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://accounts.google.com")
                                ).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(webIntent)
                            } catch (_: Exception) {}
                        }
                    }
                    return@launch
                }

                // Identify Network Issues
                if (className.contains("Network", ignoreCase = true) ||
                    e is java.net.UnknownHostException ||
                    e is java.net.ConnectException ||
                    rawExceptionMessage.contains("network", ignoreCase = true) ||
                    rawExceptionMessage.contains("resolve host", ignoreCase = true) ||
                    rawExceptionMessage.contains("connect", ignoreCase = true)
                ) {
                    errorType = "NETWORK"
                    errorMessageText = "Unable to connect to Google authentication servers. Please verify your network connection and try again."
                    showErrorDialog = true
                }
                // Identify Configuration Issues (SHA-1 signature mismatch, invalid client ID, etc.)
                else if (className.contains("Configuration", ignoreCase = true) ||
                    rawExceptionMessage.contains("configuration", ignoreCase = true) ||
                    rawExceptionMessage.contains("developer", ignoreCase = true) ||
                    rawExceptionMessage.contains("client id", ignoreCase = true) ||
                    rawExceptionMessage.contains("sha", ignoreCase = true)
                ) {
                    errorType = "CONFIGURATION"
                    errorMessageText = "Authentication configuration error. This usually indicates an unregistered SHA-1 signature or an invalid Web Client ID in the Google Cloud Console."
                    showErrorDialog = true
                }
                // Fallback / Unknown authentication error
                else {
                    errorType = "UNKNOWN"
                    errorMessageText = "Google Sign-In could not be completed because of an unexpected exception."
                    showErrorDialog = true
                }
                AuthDiagnosticLogger.logError(errorType, rawExceptionMessage)
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "googleButtonScale",
    )

    OutlinedButton(
        enabled = enabled,
        shape = shape,
        colors = colors ?: ButtonDefaults.outlinedButtonColors(),
        border = border ?: ButtonDefaults.outlinedButtonBorder,
        modifier = modifier.graphicsLayer {
            scaleX = buttonScale
            scaleY = buttonScale
        },
        interactionSource = interactionSource,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            triggerGoogleSignIn()
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoogleBrandIcon(modifier = Modifier.size(20.dp))
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                color = textColor ?: Color.Unspecified,
            )
        }
    }

    // Modern Material 3 Descriptive Error Dialog
    if (showErrorDialog) {
        AuthenticationErrorDialog(
            errorType = errorType,
            errorMessageText = errorMessageText,
            rawExceptionMessage = rawExceptionMessage,
            onDismissRequest = { showErrorDialog = false },
            onRetry = { triggerGoogleSignIn() },
        )
    }
}
