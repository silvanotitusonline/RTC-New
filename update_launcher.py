import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

pattern = r'val googleSignInLauncher = rememberLauncherForActivityResult\(.*?\)\s*\{ result ->.*?isSigningInLocally = false.*?val task = GoogleSignIn\.getSignedInAccountFromIntent\(result\.data\).*?try \{.*?val account = task\.getResult\(ApiException::class\.java\).*?val idToken = account\?\.idToken.*?if \(idToken != null\) \{.*?AuthDiagnosticLogger\.logSuccess\(\).*?viewModel\.signInWithGoogle\(idToken, ""\).*?\} else \{.*?coroutineScope\.launch \{ snackbarHostState\.showSnackbar\("No ID token found in Google Sign-In response\."\) \}.*?\}.*?\} catch \(e: ApiException\) \{.*?if \(e\.statusCode != 12501 && e\.statusCode != 0\) \{ // 12501 is cancelled.*?val errorMsg = if \(e\.statusCode == 10\) "Code 10: App SHA-1 fingerprint not registered in Google Cloud Console" else "Google sign-in failed\. Error Code: \$\{e\.statusCode\}".*?coroutineScope\.launch \{ snackbarHostState\.showSnackbar\(errorMsg\) \}.*?AuthDiagnosticLogger\.logError\("GOOGLE_SIGN_IN_ERROR", "Status Code: \$\{e\.statusCode\} Msg: \$\{e\.message \?: e\.toString\(\)\}"\).*?\} else if \(result\.resultCode != android\.app\.Activity\.RESULT_OK && e\.statusCode != 12501\) \{.*?// Sometime result code is CANCELED \(0\) but it\'s actually an error\. .*?// Wait, if user actually cancels, statusCode is 12501\. .*?\}.*?\}.*?\}'

replacement = """val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Google Sign-In intent returned. ResultCode: ${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                Log.i(TAG, "Google Sign-In account selection successful. Exchanging token...")
                AuthDiagnosticLogger.logSuccess()
                // Keeping the loading state active while exchanging token
                viewModel.signInWithGoogle(idToken, "")
            } else {
                isSigningInLocally = false
                Log.e(TAG, "Google Sign-In failed: idToken is null")
                coroutineScope.launch { snackbarHostState.showSnackbar("No ID token found in Google Sign-In response.") }
            }
        } catch (e: ApiException) {
            isSigningInLocally = false
            Log.e(TAG, "Google Sign-In API Exception. Status Code: ${e.statusCode}", e)
            if (e.statusCode != 12501 && e.statusCode != 0) { // 12501 is cancelled
                val errorMsg = if (e.statusCode == 10) {
                    "Code 10: App SHA-1 fingerprint not registered in Google Cloud Console"
                } else {
                    "Google sign-in failed. Error Code: ${e.statusCode}"
                }
                coroutineScope.launch { snackbarHostState.showSnackbar(errorMsg) }
                AuthDiagnosticLogger.logError("GOOGLE_SIGN_IN_ERROR", "Status Code: ${e.statusCode} Msg: ${e.message ?: e.toString()}")
            } else if (result.resultCode != android.app.Activity.RESULT_OK && e.statusCode != 12501) {
                Log.w(TAG, "Sign-in intent canceled by user or OS.")
            }
        } catch (e: Exception) {
            isSigningInLocally = false
            Log.e(TAG, "Unexpected error parsing Google Sign-In intent", e)
            coroutineScope.launch { snackbarHostState.showSnackbar("Unexpected authentication error.") }
        }
    }"""

content = re.sub(pattern, replacement, content, flags=re.DOTALL)

# Ensure TAG is defined or imported, SignInScreen usually has a TAG
# Let's check if TAG exists, if not, replace TAG with "SignInScreen"
content = content.replace("TAG", '"SignInScreen"')

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "w") as f:
    f.write(content)
