package za.org.rtc.community.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Alias/Wrapper for GoogleAuthDiagnosticScreen matching requested naming.
 */
@Composable
fun OAuthDiagnosticScreen(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GoogleAuthDiagnosticScreen(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}
