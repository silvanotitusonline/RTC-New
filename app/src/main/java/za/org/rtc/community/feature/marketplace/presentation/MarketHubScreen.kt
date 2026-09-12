package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.runtime.Composable
import za.org.rtc.community.navigation.RtcRoute

@Composable
fun MarketHubScreen(
    onNavigate: (String) -> Unit,
) {
    MarketplaceHomeRoute(
        onNavigate = onNavigate,
        onSwitchToServices = { onNavigate(RtcRoute.SERVICE_CENTRE_HOME) },
    )
}
