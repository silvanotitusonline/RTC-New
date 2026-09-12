package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import za.org.rtc.community.app.AuthenticationUiState
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

/** Protected-account entry point. Normal resident use never requires this screen. */
@Composable
internal fun StaffAccessScreen(
    authenticationUi: AuthenticationUiState,
    onSignIn: (String, String) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    RtcScreenScaffold {
        item {
            RtcSectionHeader(
                "Staff & administrator access",
                "RTC residents do not need an account. This sign-in is only for authorised staff workspaces.",
            )
        }
        item {
            RtcCard {
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Staff email") },
                        enabled = !authenticationUi.isWorking,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !authenticationUi.isWorking,
                    )
                    Button(
                        onClick = { onSignIn(email.trim(), password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authenticationUi.isWorking && email.isNotBlank() && password.isNotBlank(),
                    ) {
                        Text(if (authenticationUi.isWorking) "Verifying…" else "Sign in to staff workspace")
                    }
                    authenticationUi.message?.takeIf(String::isNotBlank)?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (authenticationUi.isSuccess) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                    }
                }
            }
        }
        item {
            TextButton(onClick = onBack) { Text("Back to RTC") }
        }
    }
}
