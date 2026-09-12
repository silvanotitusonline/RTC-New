package za.org.rtc.community.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun AccountScreen(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onHelp: () -> Unit,
    onMarketplace: (String) -> Unit,
    onStaffAccess: () -> Unit,
) {
    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item {
                RtcSectionHeader(
                    "My RTC",
                    "Local preferences, community resources and protected staff access.",
                )
            }
            item {
                RtcCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                            Text(
                                "No resident account required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "RTC keeps resident drafts and local preferences on this device. A device continuity key is not an account and never grants staff or administrator access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                AccountRow(
                    title = "Browse Marketplace",
                    description = "Discover local businesses and services without creating a resident account.",
                    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    onClick = { onMarketplace(RtcRoute.MARKETPLACE_HOME) },
                )
            }
            item {
                AccountRow(
                    title = "Settings & accessibility",
                    description = "Reading mode, appearance, local preferences and help.",
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = onHelp,
                )
            }
            item {
                AccountRow(
                    title = "Help & support information",
                    description = "Read support guidance and community help resources.",
                    icon = { Icon(Icons.Filled.HelpOutline, contentDescription = null) },
                    onClick = onHelp,
                )
            }
            item {
                AccountRow(
                    title = "Staff & administrator access",
                    description = "Protected sign-in for authorised RTC staff only. Residents do not need this.",
                    icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                    onClick = onStaffAccess,
                )
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
