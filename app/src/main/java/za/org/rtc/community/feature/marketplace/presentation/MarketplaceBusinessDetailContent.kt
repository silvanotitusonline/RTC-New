package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceRating
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun MarketplaceBusinessDetailContent(
    detail: MarketplaceBusinessDetail,
    reviewsState: MarketplaceLoadState<Pair<List<MarketplaceReview>, MarketplaceRating>>,
    mediaUrls: Map<String, String>,
    onNavigate: (String) -> Unit,
    onSaved: (String, Boolean) -> Unit,
    onAddReview: (Int, String, String, List<String>) -> Unit,
    onReportBusiness: (reason: String, details: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var showReviewDialog by rememberSaveable { mutableStateOf(false) }
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var isBookmarked by rememberSaveable(detail.saved) { mutableStateOf(detail.saved) }
    val activeRating = remember(reviewsState, detail.rating) {
        (reviewsState as? MarketplaceLoadState.Data)?.value?.second ?: detail.rating
    }
    val activeReviews = remember(reviewsState) {
        (reviewsState as? MarketplaceLoadState.Data)?.value?.first.orEmpty()
    }
    val gallery = remember(detail, mediaUrls) {
        detail.resolvedGalleryPhotos.map { mediaUrls[it] ?: it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = RtcSpacing.pageGutter,
            vertical = RtcSpacing.contentGroup,
        ),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            detail.card.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            detail.card.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (detail.card.verified) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Verified") },
                            leadingIcon = { Icon(Icons.Filled.Verified, contentDescription = null) },
                        )
                    }
                }
                Text(
                    "${detail.card.category} · ${detail.card.locality} · ${"%.1f".format(activeRating.average)}/5",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                ) {
                    Button(
                        onClick = {
                            val next = !isBookmarked
                            isBookmarked = next
                            onSaved(detail.card.id, next)
                        },
                        modifier = Modifier.height(RtcSize.minimumTouchTarget),
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Text(if (isBookmarked) "Saved" else "Save")
                    }
                    OutlinedButton(
                        onClick = {
                            launchDeviceMapDirections(
                                context = context,
                                location = detail.locations.firstOrNull(),
                                businessName = detail.card.displayName,
                                localityFallback = detail.card.locality,
                            )
                        },
                        modifier = Modifier.height(RtcSize.minimumTouchTarget),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Text("Directions")
                    }
                    OutlinedButton(
                        onClick = { showReportDialog = true },
                        modifier = Modifier.height(RtcSize.minimumTouchTarget),
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = null)
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Text("Report")
                    }
                }
            }
        }

        if (gallery.isNotEmpty()) {
            item {
                BusinessGallerySection(
                    photos = gallery,
                    businessName = detail.card.displayName,
                )
            }
        }

        item {
            MarketplaceDetailSection("About") {
                Text(detail.description.ifBlank { "No business description has been published yet." })
                detail.phone?.takeIf(String::isNotBlank)?.let { phone ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null)
                        Text(phone)
                    }
                }
                detail.email?.takeIf(String::isNotBlank)?.let { Text("Email: $it") }
                detail.websiteUrl?.takeIf(String::isNotBlank)?.let { Text("Website: $it") }
            }
        }

        if (detail.offerings.isNotEmpty()) {
            item {
                Text("Services & products", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(detail.offerings, key = { it.id }) { offering ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(RtcSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
                    ) {
                        Text(offering.title, fontWeight = FontWeight.Bold)
                        offering.description.takeIf(String::isNotBlank)?.let { Text(it) }
                        Text(offering.priceLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (detail.locations.isNotEmpty()) {
            item { Text("Locations & hours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(detail.locations, key = { it.id }) { location ->
                MarketplaceLocationCard(
                    location = location,
                    businessName = detail.card.displayName,
                    onGetDirections = {
                        launchDeviceMapDirections(
                            context = context,
                            location = location,
                            businessName = detail.card.displayName,
                            localityFallback = detail.card.locality,
                        )
                    },
                )
            }
        }

        item { BusinessActivitySection(detail = detail) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text("Ratings & reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppStoreRatingSummary(
                    average = activeRating.average,
                    count = activeRating.count,
                    distribution = activeRating.distribution,
                )
                Button(
                    onClick = { showReviewDialog = true },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) { Text("Write a Review") }
            }
        }

        if (activeReviews.isEmpty()) {
            item { Text("No resident reviews have been published yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(activeReviews.take(8), key = { it.id }) { review ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(RtcSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
                    ) {
                        Text("${review.rating}/5", fontWeight = FontWeight.Bold)
                        review.title.takeIf(String::isNotBlank)?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                        review.body.takeIf(String::isNotBlank)?.let { Text(it) }
                        Text(review.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showReviewDialog) {
        WriteReviewDialog(
            businessName = detail.card.displayName,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, title, body, photos ->
                showReviewDialog = false
                onAddReview(rating, title, body, photos)
            },
        )
    }

    if (showReportDialog) {
        ReportBusinessDialog(
            businessName = detail.card.displayName,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                showReportDialog = false
                onReportBusiness(reason, details)
            },
        )
    }
}
