package za.org.rtc.community.feature.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel

/**
 * Route-scoped account settings surface. Dialog state belongs to this destination so it cannot
 * leak into Account, Help, Inbox, or Marketplace back-stack entries.
 */
@Composable
fun AccountSettingsRoute(
    onOpenNotifications: () -> Unit,
    onOpenMarketplaceRoute: (String) -> Unit,
    viewModel: RtcViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val passwordUi by viewModel.passwordUi.collectAsStateWithLifecycle()
    val localityUi by viewModel.declaredLocalityUi.collectAsStateWithLifecycle()
    var passwordOpen by rememberSaveable { mutableStateOf(false) }
    var localityOpen by rememberSaveable { mutableStateOf(false) }
    var accessibilityOpen by rememberSaveable { mutableStateOf(false) }

    AccountSettingsMenu(
        onOpenNotifications = onOpenNotifications,
        onOpenSecurity = { passwordOpen = true },
        onOpenPrivacyAndData = { localityOpen = true },
        onOpenAccessibility = { accessibilityOpen = true },
        onOpenMarketplaceRoute = onOpenMarketplaceRoute,
    )

    if (passwordOpen) {
        PasswordUpdateDialog(
            isRecoveryFlow = false,
            passwordUi = passwordUi,
            onUpdate = viewModel::updatePassword,
            onDismiss = {
                viewModel.dismissPasswordUi()
                passwordOpen = false
            },
        )
    }

    if (localityOpen) {
        DeclaredLocalitySheet(
            initialLocality = session.declaredLocality,
            localityUi = localityUi,
            onSave = viewModel::saveDeclaredLocality,
            onDismiss = {
                viewModel.dismissDeclaredLocalityMessage()
                localityOpen = false
            },
        )
    }

    if (accessibilityOpen) {
        AlertDialog(
            onDismissRequest = { accessibilityOpen = false },
            title = { Text("Accessibility") },
            text = {
                Text(
                    if (session.readingMode) {
                        "Reading mode is enabled. Turn it off to return to the standard content density."
                    } else {
                        "Reading mode increases readability and reduces visual density across supported resident screens."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleReadingMode()
                        accessibilityOpen = false
                    },
                ) {
                    Text(if (session.readingMode) "Turn off reading mode" else "Turn on reading mode")
                }
            },
            dismissButton = {
                TextButton(onClick = { accessibilityOpen = false }) { Text("Cancel") }
            },
        )
    }
}
