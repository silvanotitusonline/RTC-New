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
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun AccountSettingsMenu(
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenPrivacyAndData: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenMarketplaceBusiness: (() -> Unit)? = null,
    onOpenMarketplaceRoute: ((String) -> Unit)? = null,
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

        Text("Account Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        AccountSettingsRow(
            title = "Notifications",
            description = "Choose which Community and Support updates you receive.",
            onClick = onOpenNotifications,
        )
        AccountSettingsRow(
            title = "Security",
            description = "Password, sign-in and account protection.",
            onClick = onOpenSecurity,
        )
        AccountSettingsRow(
            title = "Privacy and data",
            description = "Declared locality, your data export, and deletion requests.",
            onClick = onOpenPrivacyAndData,
        )
        AccountSettingsRow(
            title = "Accessibility",
            description = "Reading mode and display preferences.",
            onClick = onOpenAccessibility,
        )
    }
}

@Composable
private fun AccountSettingsRow(
    title: String,
    description: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
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
        }
    }
}

