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


data class FeaturedListingItem(
    val id: String,
    val businessId: String,
    val businessName: String,
    val categoryName: String,
    val rating: Double,
    val slot: String, // "HERO_BANNER", "CATEGORY_SPOTLIGHT", "TRENDING"
    val rank: Int,
    val impressionsCount: Int,
    val clickCount: Int,
    val expiresAt: String,
    val isActive: Boolean,
)

@Composable
fun MarketplaceAdminFeaturedRoute(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var showAddFeaturedDialog by rememberSaveable { mutableStateOf(false) }

    var featuredListings by remember {
        mutableStateOf(
            listOf(
                FeaturedListingItem(
                    id = "feat-01",
                    businessId = "biz-solar-01",
                    businessName = "BrightPower Solar & Inverter Solutions",
                    categoryName = "Electrical & Solar",
                    rating = 4.9,
                    slot = "HERO_BANNER",
                    rank = 1,
                    impressionsCount = 8420,
                    clickCount = 1240,
                    expiresAt = "In 18 days",
                    isActive = true,
                ),
                FeaturedListingItem(
                    id = "feat-02",
                    businessId = "biz-auto-01",
                    businessName = "Precision Auto Care & Diagnostics",
                    categoryName = "Automotive & Mechanical",
                    rating = 4.8,
                    slot = "CATEGORY_SPOTLIGHT",
                    rank = 2,
                    impressionsCount = 5120,
                    clickCount = 780,
                    expiresAt = "In 25 days",
                    isActive = true,
                ),
                FeaturedListingItem(
                    id = "feat-03",
                    businessId = "biz-plumb-02",
                    businessName = "Metro Plumbing & Emergency Drainage",
                    categoryName = "Plumbing & Drainage",
                    rating = 4.7,
                    slot = "TRENDING",
                    rank = 3,
                    impressionsCount = 3980,
                    clickCount = 610,
                    expiresAt = "In 12 days",
                    isActive = true,
                ),
                FeaturedListingItem(
                    id = "feat-04",
                    businessId = "biz-clean-01",
                    businessName = "SparklePro Commercial & Domestic Hygiene",
                    categoryName = "Home Cleaning & Hygiene",
                    rating = 4.9,
                    slot = "CATEGORY_SPOTLIGHT",
                    rank = 4,
                    impressionsCount = 4210,
                    clickCount = 590,
                    expiresAt = "In 30 days",
                    isActive = true,
                ),
            )
        )
    }

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
                    text = "Featured Listings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Promote and spotlight verified community businesses across the home banner, discovery rows, and top searches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Top Summary & CTA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Promoted Spots (${featuredListings.count { it.isActive }})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { showAddFeaturedDialog = true },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Spotlight Business")
                }
            }
        }

        // Promoted businesses
        items(featuredListings, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (item.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        text = "#${item.rank} ${item.slot.replace('_', ' ')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text("⭐ ${item.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.businessName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${item.categoryName} · Expires ${item.expiresAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { checked ->
                                featuredListings = featuredListings.map {
                                    if (it.id == item.id) it.copy(isActive = checked) else it
                                }
                                actionNotice = if (checked) "Featured promotion resumed for ${item.businessName}." else "Featured promotion paused."
                            },
                        )
                    }

                    // Impression & Click Metrics Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Impressions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${item.impressionsCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Clicks / Inquiries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${item.clickCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CTR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val ctr = if (item.impressionsCount > 0) (item.clickCount * 100f / item.impressionsCount) else 0f
                                Text(String.format("%.1f%%", ctr), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                featuredListings = featuredListings.filterNot { it.id == item.id }
                                actionNotice = "Removed ${item.businessName} from featured spots."
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove Spotlight")
                        }
                    }
                }
            }
        }
    }

    if (showAddFeaturedDialog) {
        var businessNameInput by rememberSaveable { mutableStateOf("") }
        var selectedSlot by rememberSaveable { mutableStateOf("HERO_BANNER") }
        var durationDays by rememberSaveable { mutableIntStateOf(30) }

        AlertDialog(
            onDismissRequest = { showAddFeaturedDialog = false },
            title = { Text("Spotlight Business Listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = businessNameInput,
                        onValueChange = { businessNameInput = it },
                        label = { Text("Business Name or ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Select Placement Slot", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "HERO_BANNER" to "Hero Banner",
                            "CATEGORY_SPOTLIGHT" to "Category Spotlight",
                            "TRENDING" to "Trending Row",
                        ).forEach { (slot, label) ->
                            FilterChip(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text("Duration: $durationDays days", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = durationDays.toFloat(),
                        onValueChange = { durationDays = it.toInt() },
                        valueRange = 7f..90f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (businessNameInput.isNotBlank()) {
                            val newItem = FeaturedListingItem(
                                id = "feat-${System.currentTimeMillis()}",
                                businessId = "biz-${System.currentTimeMillis()}",
                                businessName = businessNameInput.trim(),
                                categoryName = "General Service",
                                rating = 5.0,
                                slot = selectedSlot,
                                rank = featuredListings.size + 1,
                                impressionsCount = 0,
                                clickCount = 0,
                                expiresAt = "In $durationDays days",
                                isActive = true,
                            )
                            featuredListings = listOf(newItem) + featuredListings
                            actionNotice = "Business '${businessNameInput.trim()}' added to featured spotlight."
                            showAddFeaturedDialog = false
                        }
                    },
                    enabled = businessNameInput.isNotBlank(),
                ) { Text("Add to Spotlight") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFeaturedDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// -------------------------------------------------------------
// 4. Marketplace Admin Analytics & Growth Dashboard
// -------------------------------------------------------------
