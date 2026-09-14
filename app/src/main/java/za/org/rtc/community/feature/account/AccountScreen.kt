package za.org.rtc.community.feature.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun AccountScreen(
    viewModel: RtcViewModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onHelp: () -> Unit,
    onMarketplace: (String) -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val authenticationUi by viewModel.authenticationUi.collectAsStateWithLifecycle()
    var profileEditorOpen by rememberSaveable { mutableStateOf(false) }
    var logoutConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingPhotoUri = uri
    }

    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item { RtcSectionHeader("Account", "Identity, messages, marketplace, settings and support.") }
            item {
                RtcCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                        ProfileAvatar(session = session)
                        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                            Text(session.displayName.ifBlank { "Resident" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(session.handle.ifBlank { "Signed-in resident account" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.authenticatedEmail ?: "Signed-in resident account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RtcStatusChip(session.role.name.replace("_", " "), RtcStatusTone.NEUTRAL)
                        }
                    }
                }
            }
            item {
                AccountRow(
                    title = "Edit profile",
                    description = "Update your public profile and choose a private account photo.",
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { profileEditorOpen = true },
                )
            }
            item {
                AccountRow(
                    title = "Marketplace & Business Hub",
                    description = "Manage, edit, or register your local business profiles, team invitations, photos and services.",
                    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    onClick = { onMarketplace(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                )
            }
            item {
                AccountRow(
                    title = "Messages",
                    description = "Open your resident inbox directly on the Messages tab.",
                    icon = { Icon(Icons.Filled.MailOutline, contentDescription = null) },
                    onClick = { onMarketplace(RtcRoute.ACCOUNT_MESSAGES) },
                )
            }
            item {
                AccountRow(
                    title = "Settings",
                    description = "Privacy, security, notifications, accessibility and account controls.",
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = { onMarketplace(RtcRoute.ACCOUNT_SETTINGS) },
                )
            }
            item {
                AccountRow(
                    title = "Support",
                    description = "Contact Support and track your existing support cases.",
                    icon = { Icon(Icons.Filled.HelpOutline, contentDescription = null) },
                    onClick = onHelp,
                )
            }
            item {
                AccountRow(
                    title = "Log out",
                    description = "Sign out of this device and return to the welcome screen.",
                    icon = {
                        Icon(
                            Icons.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    destructive = true,
                    onClick = { logoutConfirmationOpen = true },
                )
            }
        }
    }

    if (profileEditorOpen) {
        ProfileEditorSheet(
            session = session,
            pendingPhotoUri = pendingPhotoUri,
            photoSaveInProgress = authenticationUi.isWorking,
            photoSaveSucceeded = authenticationUi.isSuccess,
            photoMessage = authenticationUi.message,
            onSave = { name, bio, interests ->
                viewModel.updateProfile(name, bio, interests.map(String::trim).filter(String::isNotBlank))
            },
            onChooseGallery = {
                profilePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onSavePhoto = viewModel::uploadProfilePhoto,
            onClearSelectedPhoto = { pendingPhotoUri = null },
            onRemovePhoto = viewModel::deleteProfilePhoto,
            onDismiss = {
                pendingPhotoUri = null
                viewModel.dismissAuthenticationMessage()
                profileEditorOpen = false
            },
        )
    }

    if (logoutConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { logoutConfirmationOpen = false },
            icon = {
                Icon(
                    Icons.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Log out?") },
            text = { Text("You’ll be signed out of this device and returned to the welcome screen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutConfirmationOpen = false
                        viewModel.signOutToPublicWelcome()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirmationOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AccountRow(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            icon()
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
