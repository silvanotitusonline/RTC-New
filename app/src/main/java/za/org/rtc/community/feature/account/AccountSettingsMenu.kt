package za.org.rtc.community.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun AccountSettingsMenu(
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenPrivacyAndData: () -> Unit,
    onOpenAccessibility: () -> Unit,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onSetTheme: (ThemePreference) -> Unit = {},
    readingMode: Boolean = false,
    supportNotifications: Boolean = true,
    communityNotifications: Boolean = true,
    onSetNotificationPreference: (String, Boolean) -> Unit = { _, _ -> },
    onOpenMarketplaceBusiness: (() -> Unit)? = null,
    onOpenMarketplaceRoute: ((String) -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
) {
    val handleMarketplaceRoute: (String) -> Unit = { route ->
        onOpenMarketplaceRoute?.invoke(route) ?: onOpenMarketplaceBusiness?.invoke()
    }

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (onOpenMarketplaceRoute != null || onOpenMarketplaceBusiness != null) {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text(
                    "Marketplace & Business Profile Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                AccountSettingsRow(
                    title = "My Business Profiles",
                    description = "View, edit, and update your business details, operating hours, photos, and services.",
                    icon = Icons.Filled.Business,
                    onClick = { handleMarketplaceRoute(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                )

                AccountSettingsRow(
                    title = "Create New Business Profile",
                    description = "Register a new local business, trade, or service listing on the Community Marketplace.",
                    icon = Icons.Filled.AddBusiness,
                    onClick = { handleMarketplaceRoute(RtcRoute.MARKETPLACE_BUSINESS_NEW) },
                )

                AccountSettingsRow(
                    title = "Business Team Invitations",
                    description = "Manage pending invitations to co-manage community business profiles.",
                    icon = Icons.Filled.GroupAdd,
                    onClick = { handleMarketplaceRoute(RtcRoute.MARKETPLACE_INVITATIONS) },
                )

                AccountSettingsRow(
                    title = "Saved Businesses & Bookmarks",
                    description = "Access your bookmarked marketplace listings and saved local enterprises.",
                    icon = Icons.Filled.Bookmark,
                    onClick = { handleMarketplaceRoute(RtcRoute.MARKETPLACE_SAVED) },
                )

                AccountSettingsRow(
                    title = "My Reviews & Ratings",
                    description = "Manage your public customer ratings and reviews across the Marketplace.",
                    icon = Icons.Filled.RateReview,
                    onClick = { handleMarketplaceRoute(RtcRoute.MARKETPLACE_MY_REVIEWS) },
                )
            }
        }

        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            ThemePreference.entries.forEach { preference ->
                FilterChip(
                    selected = themePreference == preference,
                    onClick = { onSetTheme(preference) },
                    label = { Text(preference.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }

        Text("Account Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        AccountSettingsRow(
            title = "Notifications",
            description = "Review your Community and Support alerts and messages.",
            onClick = onOpenNotifications,
        )
        AccountSettingsRow(
            title = "Community updates",
            description = "Receive ordinary community alerts and local updates.",
            onClick = { onSetNotificationPreference("community", !communityNotifications) },
            trailing = { Switch(checked = communityNotifications, onCheckedChange = { onSetNotificationPreference("community", it) }) },
        )
        AccountSettingsRow(
            title = "Support updates",
            description = "Receive support-case and service-centre notifications.",
            onClick = { onSetNotificationPreference("support", !supportNotifications) },
            trailing = { Switch(checked = supportNotifications, onCheckedChange = { onSetNotificationPreference("support", it) }) },
        )
        AccountSettingsRow(
            title = "Security",
            description = "Password, sign-in and account protection.",
            onClick = onOpenSecurity,
        )
        AccountSettingsRow(
            title = "Privacy and data",
            description = "Manage your declared locality and privacy choices.",
            onClick = onOpenPrivacyAndData,
        )
        AccountSettingsRow(
            title = "Accessibility",
            description = if (readingMode) "Reading mode is on; adjust text for easier reading." else "Turn on reading mode for larger, clearer text.",
            onClick = onOpenAccessibility,
        )
        if (onSignOut != null) {
            AccountSettingsRow(
                title = "Log Out",
                description = "Sign out of your resident account on this device.",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onSignOut,
            )
        }
    }
}

@Composable
private fun AccountSettingsRow(
    title: String,
    description: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}
