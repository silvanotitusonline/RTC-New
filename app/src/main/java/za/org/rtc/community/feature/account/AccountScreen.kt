package za.org.rtc.community.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
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

    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item {
                RtcSectionHeader(
                    "Account",
                    "Identity, provider tools, marketplace and support.",
                )
            }
            item {
                RtcCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                        ProfileAvatar(session = session)
                        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                            Text(
                                text = session.displayName.ifBlank { "Resident" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = session.handle.ifBlank { "Signed-in resident account" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = session.authenticatedEmail ?: "Signed-in resident account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            RtcStatusChip(
                                session.role.name.replace("_", " "),
                                RtcStatusTone.NEUTRAL,
                            )
                        }
                    }
                }
            }
            item {
                AccountRow(
                    title = "Marketplace & Business Hub",
                    description = "Manage, edit, or register your local business profiles, team invitations, photos and services.",
                    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    onClick = { onMarketplace("account/marketplace/my-businesses") },
                )
            }
            item {
                AccountRow(
                    title = "Provider profile",
                    description = "Become a provider or manage your Service Centre provider profile.",
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    onClick = { onMarketplace("account/marketplace/my-businesses") },
                )
            }
            item {
                AccountRow(
                    title = "Settings",
                    description = "Profile, privacy, security, notifications, accessibility and account controls.",
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = onHelp,
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
                RtcCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Text("Provider status and account identity remain attached to this same resident account.")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            icon()
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
