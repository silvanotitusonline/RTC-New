package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun MarketHubScreen(
    onNavigate: (String) -> Unit,
) {
    MarketplaceHomeRoute(
        onNavigate = onNavigate,
        modifier = Modifier
            .fillMaxSize()
            .testTag("market_hub_screen"),
    )
}

