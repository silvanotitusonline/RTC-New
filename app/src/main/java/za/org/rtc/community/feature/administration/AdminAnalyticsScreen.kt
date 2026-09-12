package za.org.rtc.community.feature.administration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun AdminAnalyticsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text(
                "Civic Intelligence Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = RtcDesignSystem.TextPrimary,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            AnalyticsCard(
                title = "Community Sentiment",
                value = "Positive (0.62)",
                trend = "+5% from last week",
                color = Color.Green,
            )
        }
        item {
            AnalyticsCard(
                title = "Hotspot Clusters",
                value = "12 Active Clusters",
                trend = "3 New in Sector 4",
                color = Color.Red,
            )
        }
        item {
            AnalyticsCard(
                title = "Avg. Resolution Time",
                value = "4.2 Days",
                trend = "-12% improvement",
                color = Color.Blue,
            )
        }
    }
}

@Composable
fun AnalyticsCard(title: String, value: String, trend: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = RtcDesignSystem.SurfaceDark),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = RtcDesignSystem.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(value, color = RtcDesignSystem.TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Text(trend, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}
