import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

# We will just regex replace the whole onClick of GoogleCredentialSignInButton.
# Let's find GoogleCredentialSignInButton(
pattern = r'GoogleCredentialSignInButton\(\s*isLoading = isSigningInLocally,\s*enabled = !isAnyLoading,\s*onClick = \{.*?\},\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.height\(50\.dp\)\s*\.testTag\("google_sign_in_button"\)\s*\)'

replacement = """GoogleCredentialSignInButton(
                        isLoading = isSigningInLocally,
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
