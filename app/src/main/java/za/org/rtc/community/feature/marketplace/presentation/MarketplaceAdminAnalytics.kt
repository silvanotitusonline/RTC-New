package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReviewReportReason
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

// -------------------------------------------------------------
// 1. Marketplace Admin Flagged Reviews Moderation Screen
// -------------------------------------------------------------


@Composable
fun MarketplaceAdminAnalyticsRoute(
    onBack: () -> Unit,
) {
    var timeframe by rememberSaveable { mutableStateOf("30D") }
    var actionNotice by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Admin")
                }
                Text(
                    text = "Marketplace Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Track listing growth, search engagement, customer lead generation, and regional activity velocity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Timeframe selector
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "7D" to "Last 7 Days",
                    "30D" to "Last 30 Days",
                    "90D" to "Last 90 Days",
                    "ALL" to "All Time",
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = timeframe == code,
                        onClick = { timeframe = code },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Core KPIs Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricSummaryCard(
                        title = "Directory Views",
                        value = if (timeframe == "7D") "3,480" else if (timeframe == "30D") "14,820" else "48,910",
                        change = "+18.4% vs prev",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                    MetricSummaryCard(
                        title = "Customer Inquiries",
                        value = if (timeframe == "7D") "492" else if (timeframe == "30D") "2,130" else "7,450",
                        change = "+24.1% vs prev",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricSummaryCard(
                        title = "Active Businesses",
                        value = "186",
                        change = "12 pending review",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                    MetricSummaryCard(
                        title = "Average Rating",
                        value = "4.8 / 5.0",
                        change = "94% 4+ stars",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Top Category Demand Distribution
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Category Search & Demand Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    listOf(
                        "Plumbing & Emergency Drainage" to 0.32f,
                        "Electrical & Solar Solutions" to 0.28f,
                        "Food, Restaurants & Catering" to 0.16f,
                        "Home Cleaning & Hygiene" to 0.14f,
                        "Automotive & Towing" to 0.10f,
                    ).forEach { (catName, share) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(catName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { share },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Top Search Queries
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Top Marketplace Search Terms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    listOf(
                        "emergency plumber" to "1,240 searches",
                        "solar inverter installation" to "980 searches",
                        "car battery jumpstart" to "740 searches",
                        "deep cleaning services" to "620 searches",
                        "braiding salon open sunday" to "490 searches",
                        "electrician coC certificate" to "410 searches",
                    ).forEach { (term, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(term, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Export Report Button
        item {
            Button(
                onClick = { actionNotice = "Marketplace performance report exported as CSV." },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RtcSize.minimumTouchTarget),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Analytics Summary (CSV)")
            }
        }
    }
}

@Composable
private fun MetricSummaryCard(
    title: String,
    value: String,
    change: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = change,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
