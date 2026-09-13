package za.org.rtc.community.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import za.org.rtc.community.app.AuthenticationUiState
import za.org.rtc.community.app.PasswordUiState

private val guestLabels = mapOf(
    "en" to "Continue as guest",
    "af" to "Gaan voort as gas",
    "zu" to "Qhubeka njengesivakashi",
    "xh" to "Qhubeka njengondwendwe",
    "st" to "Tswela pele o le moeti",
    "tn" to "Tswelela jaaka moeng",
)

/**
 * Adds an explicit public/read-only entry to the existing authentication experience without
 * converting the anonymous session into an authenticated resident.
 */
@Composable
internal fun PublicWelcomeGuestHost(
    language: String,
    authenticationUi: AuthenticationUiState,
    passwordUi: PasswordUiState,
    onContinueAsGuest: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onGoogleCredential: (String, String) -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onDismissAuthenticationMessage: () -> Unit,
    onRequestPasswordRecovery: (String) -> Unit,
    onDismissPasswordMessage: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PublicWelcomeScreen(
            authenticationUi = authenticationUi,
            passwordUi = passwordUi,
            onSignIn = onSignIn,
            onGoogleCredential = onGoogleCredential,
            onGoogleSignInError = onGoogleSignInError,
            onSignUp = onSignUp,
            onDismissAuthenticationMessage = onDismissAuthenticationMessage,
            onRequestPasswordRecovery = onRequestPasswordRecovery,
            onDismissPasswordMessage = onDismissPasswordMessage,
        )
        TextButton(
            onClick = onContinueAsGuest,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
        ) {
            Text(
                text = guestLabels[language] ?: guestLabels.getValue("en"),
                color = Color(0xFFCBD5E1),
            )
        }
    }
}
