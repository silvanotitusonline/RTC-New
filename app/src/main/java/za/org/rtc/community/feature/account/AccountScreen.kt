package za.org.rtc.community.feature.account

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val passwordUi by viewModel.passwordUi.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }

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
                        onOpenNotifications = {},
                        onOpenSecurity = { showPasswordDialog = true },
                        onOpenPrivacyAndData = {},
                        onOpenAccessibility = {},
                        onOpenMarketplaceBusiness = { onMarketplace(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                        onOpenMarketplaceRoute = { onMarketplace(it) },
                    )
                }
            }
        } else {
            AccountHubScreen(
                session = session,
                onOpenSettings = { showSettings = true },
                onOpenSupport = onHelp,
                onOpenMarketplaceBusiness = { onMarketplace(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
            )
        }
    }
}
