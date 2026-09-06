package za.org.rtc.community.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Component that observes Google Auth errors and provides a retry mechanism.
 */
@Composable
fun GoogleAuthErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthenticationErrorDialog(
        errorType = "UNKNOWN",
        errorMessageText = errorMessage,
        rawExceptionMessage = errorMessage,
        onDismissRequest = onDismiss,
        onRetry = onRetry
    )
}
