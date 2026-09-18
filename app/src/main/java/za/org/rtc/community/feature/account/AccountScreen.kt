package za.org.rtc.community.feature.account

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader

@Composable
fun AccountScreen(
    viewModel: RtcViewModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onHelp: () -> Unit,
    onMarketplace: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val passwordUi by viewModel.passwordUi.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }
    var localityDraft by rememberSaveable { mutableStateOf("") }

    if (showPasswordDialog) {
        PasswordUpdateDialog(
            isRecoveryFlow = false,
            passwordUi = passwordUi,
            onUpdate = { newPw, curPw -> viewModel.updatePassword(newPw, curPw) },
            onDismiss = {
                showPasswordDialog = false
                viewModel.dismissPasswordUi()
            },
        )
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.signOutToPublicWelcome()
            },
            onDismiss = {
                showLogoutDialog = false
            },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy and data") },
            text = {
                OutlinedTextField(
                    value = localityDraft,
                    onValueChange = { localityDraft = it.take(120) },
                    label = { Text("Declared locality") },
                    supportingText = { Text("Used to tailor local community information. Leave blank to clear it.") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveDeclaredLocality(localityDraft)
                    showPrivacyDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Cancel") } },
        )
    }

    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        if (showSettings) {
            RtcScreenScaffold {
                item {
                    RtcSectionHeader(
                        title = "Settings",
                        subtitle = "Preferences, security and application controls.",
                        trailing = {
                            TextButton(onClick = { showSettings = false }) {
                                Text("Done")
                            }
                        }
                    )
                }
                item {
                    AccountSettingsMenu(
                        onOpenNotifications = onOpenNotifications,
                        onOpenSecurity = { showPasswordDialog = true },
                        onOpenPrivacyAndData = {
                            localityDraft = session.declaredLocality.orEmpty()
                            showPrivacyDialog = true
                        },
                        onOpenAccessibility = { viewModel.toggleReadingMode() },
                        themePreference = session.darkMode,
                        onSetTheme = viewModel::setTheme,
                        readingMode = session.readingMode,
                        supportNotifications = session.supportNotifications,
                        communityNotifications = session.communityNotifications,
                        onSetNotificationPreference = viewModel::setNotificationPreference,
                        onOpenMarketplaceBusiness = { onMarketplace(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                        onOpenMarketplaceRoute = { onMarketplace(it) },
                        onSignOut = { showLogoutDialog = true },
                    )
                }
            }
        } else {
            AccountHubScreen(
                session = session,
                onOpenSettings = { showSettings = true },
                onOpenProviderProfile = onOpenProfile,
                onOpenSupport = onHelp,
                onOpenMarketplaceBusiness = { onMarketplace(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                onSignOut = { showLogoutDialog = true },
            )
        }
    }
}
