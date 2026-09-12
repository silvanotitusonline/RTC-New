
package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun MarketplaceHome() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Explore Services", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        }
        
        // Horizontal Category Chips (Industry Standard)
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = false,
                        onClick = { /* Filter */ },
                        label = { Text(category) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        
        // Featured Businesses Grid (Optimized layout)
        items(featuredBusinesses) { business ->
            BusinessPremiumCard(business)
        }
    }
}
