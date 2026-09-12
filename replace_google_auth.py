import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/RtcGoogleSignInButton.kt", "r") as f:
    content = f.read()

# Replace imports
import_replacement = """import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
"""
content = re.sub(r'import androidx.compose.ui.window.Dialog.*', import_replacement, content, flags=re.MULTILINE)

# Replace the Composable body
body_start = content.find("fun RtcGoogleSignInButton(")
body_end = len(content)

new_body = """fun RtcGoogleSignInButton(
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
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorType by remember { mutableStateOf("UNKNOWN") }
    var errorMessageText by remember { mutableStateOf("") }
    var rawExceptionMessage by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    AuthDiagnosticLogger.logSuccess()
                    onCredential(idToken, "")
                } else {
                    errorType = "UNKNOWN"
                    errorMessageText = "No ID token found in Google Sign-In response."
                    showErrorDialog = true
                }
            } catch (e: ApiException) {
                rawExceptionMessage = e.message ?: e.toString()
                
                // If cancelled (12501)
                if (e.statusCode == 12501) {
                    onFailure("Google Sign-In was cancelled.")
                    return@rememberLauncherForActivityResult
                }
                
                errorType = "UNKNOWN"
                errorMessageText = "Google Sign-In failed. Error Code: ${e.statusCode}"
                showErrorDialog = true
                AuthDiagnosticLogger.logError(errorType, rawExceptionMessage)
            }
        } else {
            onFailure("Google Sign-In was cancelled.")
        }
    }

    fun triggerGoogleSignIn() {
        if (!GooglePlayServicesHelper.isPlayServicesAvailable(context)) {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            val activity = context as? android.app.Activity
            if (activity != null && availability.isUserResolvableError(resultCode)) {
                availability.showErrorDialogFragment(activity, resultCode, 9001)
                return
            }
        }
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(RTC_GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
            
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            launcher.launch(googleSignInClient.signInIntent)
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
"""

content = content[:body_start] + new_body
with open("app/src/main/java/za/org/rtc/community/ui/auth/RtcGoogleSignInButton.kt", "w") as f:
    f.write(content)
