package za.org.rtc.community.feature.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Coder B-owned Services destination. Navigation wiring is intentionally deferred to the
 * resident-shell integration change so it does not conflict with Public Reports.
 */
@Composable
fun ServicesHubScreen(
    onOpenServiceCentre: () -> Unit,
    onOpenMarketplace: () -> Unit,
) {
    RtcScreenScaffold {
        item {
            RtcSectionHeader(
                title = "Services",
                subtitle = "Find local providers, request a booking, or explore the Marketplace.",
            )
        }
        item {
            ServiceHubCard(
                title = "Service Centre",
                description = "Find nearby providers, request work, and keep booking conversations together.",
                icon = { Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(RtcSize.actionIcon)) },
                onClick = onOpenServiceCentre,
            )
        }
        item {
            ServiceHubCard(
                title = "Marketplace",
                description = "Browse trusted community businesses, opportunities, and listings.",
                icon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.fillMaxWidth()) },
                onClick = onOpenMarketplace,
            )
        }
    }
}

@Composable
private fun ServiceHubCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
            horizontalAlignment = Alignment.Start,
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
