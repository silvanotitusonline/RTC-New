package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.runtime.Composable

@Composable
fun MarketHubScreen(
    onNavigate: (String) -> Unit,
) {
    MarketplaceHomeRoute(onNavigate = onNavigate)
}
