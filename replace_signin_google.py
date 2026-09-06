import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

import_str = """
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
"""
content = re.sub(r'import androidx.compose.ui.text.input.PasswordVisualTransformation', 'import androidx.compose.ui.text.input.PasswordVisualTransformation' + import_str, content)

# We need to inject the ActivityResultLauncher right before the return of the composable or around line 100.
# We'll put it after `var showForgotPasswordModal by rememberSaveable { mutableStateOf(false) }`
launcher_code = """
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningInLocally = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    AuthDiagnosticLogger.logSuccess()
                    viewModel.signInWithGoogle(idToken, "")
                } else {
                    snackbarHostState.showSnackbar("No ID token found in Google Sign-In response.")
                }
            } catch (e: ApiException) {
                if (e.statusCode != 12501) {
                    snackbarHostState.showSnackbar("Google sign-in failed. Error Code: ${e.statusCode}")
                    AuthDiagnosticLogger.logError("GOOGLE_SIGN_IN_ERROR", e.message ?: e.toString())
                }
            }
        }
    }
"""
content = re.sub(r'(var showForgotPasswordModal by rememberSaveable \{ mutableStateOf\(false\) \})', r'\1' + launcher_code, content)

# Now replace the onClick block for GoogleCredentialSignInButton.
# It starts at: onClick = {
#                 isSigningInLocally = true
#                 coroutineScope.launch {
# ... ends at ...
#                 }
#             },

# We will just regex replace the whole onClick of GoogleCredentialSignInButton.
# Let's find GoogleCredentialSignInButton(
pattern = r'GoogleCredentialSignInButton\(\s*isLoading = isAnyLoading,\s*enabled = !isAnyLoading,\s*onClick = \{.*?\},\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.height\(50\.dp\)\s*\.testTag\("google_sign_in_button"\)\s*\)'

replacement = """GoogleCredentialSignInButton(
                        isLoading = isAnyLoading,
                        enabled = !isAnyLoading,
                        onClick = {
                            isSigningInLocally = true
                            if (!GooglePlayServicesHelper.isPlayServicesAvailable(context)) {
                                isSigningInLocally = false
                                val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                                val resultCode = availability.isGooglePlayServicesAvailable(context)
                                val activity = context as? android.app.Activity
                                if (activity != null && availability.isUserResolvableError(resultCode)) {
                                    availability.showErrorDialogFragment(activity, resultCode, 9001)
                                } else {
                                    snackbarHostState.showSnackbar("Google Play Services is not available.")
                                }
                                return@GoogleCredentialSignInButton
                            }
                            
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(RTC_GOOGLE_WEB_CLIENT_ID)
                                .requestEmail()
                                .requestProfile()
                                .build()
                                
                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_sign_in_button")
                    )"""

# Regex replacing across multiple lines
content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "w") as f:
    f.write(content)
