import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

pattern = r'val googleSignInLauncher = rememberLauncherForActivityResult\(.*?\)\s*\{ result ->.*?isSigningInLocally = false.*?if \(result\.resultCode == android\.app\.Activity\.RESULT_OK\) \{.*?val task = GoogleSignIn\.getSignedInAccountFromIntent\(result\.data\).*?try \{.*?val account = task\.getResult\(ApiException::class\.java\).*?val idToken = account\?\.idToken.*?if \(idToken != null\) \{.*?AuthDiagnosticLogger\.logSuccess\(\).*?viewModel\.signInWithGoogle\(idToken, ""\).*?\} else \{.*?coroutineScope\.launch \{ snackbarHostState\.showSnackbar\("No ID token found in Google Sign-In response\."\) \}.*?\}.*?\} catch \(e: ApiException\) \{.*?if \(e\.statusCode != 12501\) \{.*?coroutineScope\.launch \{ snackbarHostState\.showSnackbar\("Google sign-in failed\. Error Code: \$\{e\.statusCode\}"\) \}.*?AuthDiagnosticLogger\.logError\("GOOGLE_SIGN_IN_ERROR", e\.message \?: e\.toString\(\)\).*?\}.*?\}.*?\}.*?\}'

replacement = """val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningInLocally = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                AuthDiagnosticLogger.logSuccess()
                viewModel.signInWithGoogle(idToken, "")
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("No ID token found in Google Sign-In response.") }
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501 && e.statusCode != 0) { // 12501 is cancelled
                coroutineScope.launch { snackbarHostState.showSnackbar("Google sign-in failed. Error Code: ${e.statusCode}") }
                AuthDiagnosticLogger.logError("GOOGLE_SIGN_IN_ERROR", "Status Code: ${e.statusCode} Msg: ${e.message ?: e.toString()}")
            } else if (result.resultCode != android.app.Activity.RESULT_OK && e.statusCode != 12501) {
                // Sometime result code is CANCELED (0) but it's actually an error. 
                // Wait, if user actually cancels, statusCode is 12501. 
            }
        }
    }"""

content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "w") as f:
    f.write(content)
