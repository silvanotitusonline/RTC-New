
package za.org.rtc.community.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun HomeFeedHeader(
    selectedTab: String, 
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        FeedTabItem("For You", "for_you", selectedTab, onTabSelected)
        FeedTabItem("Following", "following", selectedTab, onTabSelected)
    }
}

@Composable
fun FeedTabItem(label: String, route: String, selectedTab: String, onTabSelected: (String) -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clickable { onTabSelected(route) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selectedTab == route) RtcDesignSystem.TextPrimary else RtcDesignSystem.TextSecondary,
        fontWeight = if (selectedTab == route) FontWeight.Bold else FontWeight.Normal
    )
}
