import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

# Add isSigningInLocally = false to the error message effect
pattern_error = r'LaunchedEffect\(uiState\.errorMessage\) \{\s*uiState\.errorMessage\?\.let \{ error ->\s*coroutineScope\.launch \{ snackbarHostState\.showSnackbar\(error\) \}\s*viewModel\.clearError\(\)\s*\}'
replacement_error = """LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            isSigningInLocally = false
            coroutineScope.launch { snackbarHostState.showSnackbar(error) }
            viewModel.clearError()
        }"""
content = re.sub(pattern_error, replacement_error, content)

# Also reset when UI state stops loading if it was loading
# Actually, best is to just set it to false before calling viewModel, 
# because viewModel handles its own uiState.isLoading!
# Let's see how I changed the launcher:
#                 AuthDiagnosticLogger.logSuccess()
#                 // Keeping the loading state active while exchanging token
#                 viewModel.signInWithGoogle(idToken, "")
# We should do:
#                 isSigningInLocally = false
#                 viewModel.signInWithGoogle(...) 
# Since viewModel.signInWithGoogle immediately sets uiState.isLoading = true, there won't be a gap. Wait, is it immediate?
# `signInWithGoogle` in ViewModel uses `viewModelScope.launch { _uiState.update { it.copy(isLoading = true...) } }`
# There could be a 1-frame flicker if we set isSigningInLocally = false. 
# But it's much safer than risking an infinite spinner.
# Let's change the launcher back to `isSigningInLocally = false` right before `viewModel.signInWithGoogle`.

pattern_launcher_success = r'AuthDiagnosticLogger\.logSuccess\(\)\s*// Keeping the loading state active while exchanging token\s*viewModel\.signInWithGoogle\(idToken, ""\)'
replacement_launcher_success = """AuthDiagnosticLogger.logSuccess()
                isSigningInLocally = false // Hand over loading state to ViewModel
                viewModel.signInWithGoogle(idToken, "")"""
content = re.sub(pattern_launcher_success, replacement_launcher_success, content)


with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "w") as f:
    f.write(content)
