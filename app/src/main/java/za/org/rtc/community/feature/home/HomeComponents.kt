package za.org.rtc.community.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun HomeFeedHeader(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FeedTabItem("For You", "for_you", selectedTab, onTabSelected)
        FeedTabItem("Following", "following", selectedTab, onTabSelected)
    }
}

@Composable
fun FeedTabItem(
    label: String,
    route: String,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clickable { onTabSelected(route) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selectedTab == route) RtcDesignSystem.TextPrimary else RtcDesignSystem.TextSecondary,
        fontWeight = if (selectedTab == route) FontWeight.Bold else FontWeight.Normal,
    )
}
