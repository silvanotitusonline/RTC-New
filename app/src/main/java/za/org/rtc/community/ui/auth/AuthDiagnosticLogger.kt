package za.org.rtc.community.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AuthDiagnosticLogger {
    var lastAuthStatus by mutableStateOf("Waiting for credential request...")
    var lastAuthException by mutableStateOf<String?>(null)
    
    fun logAttempt() {
        lastAuthStatus = "Attempting Google Sign-In..."
        lastAuthException = null
    }

    fun logSuccess() {
        lastAuthStatus = "Success! Credentials retrieved."
        lastAuthException = null
    }

    fun logError(exceptionName: String, message: String) {
        lastAuthStatus = "Failed: $exceptionName"
        lastAuthException = message
    }
}
