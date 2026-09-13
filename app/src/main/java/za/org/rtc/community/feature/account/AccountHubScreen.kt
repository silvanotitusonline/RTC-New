package za.org.rtc.community.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun AccountHubScreen(
    session: RtcSession,
    onOpenSettings: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenMarketplaceBusiness: (() -> Unit)? = null,
) {
    RtcScreenScaffold {
        item { RtcSectionHeader("Account", "Identity, messages, marketplace, support and settings.") }
        item {
            RtcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    ProfileAvatar(session = session)
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text(session.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(session.handle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            session.authenticatedEmail ?: "Signed-in resident account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (onOpenMarketplaceBusiness != null) {
            item {
                AccountHubAction(
                    title = "Marketplace & Business Hub",
                    description = "Manage, edit, or register your local business profiles, team invitations, photos, and services.",
                    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    onClick = onOpenMarketplaceBusiness,
                )
            }
        }
        item {
            AccountHubAction(
                title = "Messages",
                description = "Open resident messages and support conversations.",
                icon = { Icon(Icons.Filled.MailOutline, contentDescription = null) },
                onClick = onOpenMessages,
            )
        }
        item {
            AccountHubAction(
                title = "Support",
                description = "Contact Support and track your existing support cases.",
                icon = { Icon(Icons.Filled.HelpOutline, contentDescription = null) },
                onClick = onOpenSupport,
            )
        }
        item {
            AccountHubAction(
                title = "Settings",
                description = "Profile, privacy, security, notifications, accessibility and account controls.",
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun AccountHubAction(
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
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
