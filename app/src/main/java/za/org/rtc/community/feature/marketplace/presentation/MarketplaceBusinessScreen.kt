package za.org.rtc.community.feature.marketplace.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursEvaluator
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursException
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursInterval
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOpeningStatus
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun MarketplaceDetailRoute(
    id: String,
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.detail.collectAsStateWithLifecycle()
    val reviewsState by viewModel.reviews.collectAsStateWithLifecycle()
    val mediaUrls by viewModel.mediaUrls.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val reviewViewModel: MarketplaceReviewViewModel = hiltViewModel()
    val reviewNotice by reviewViewModel.notice.collectAsStateWithLifecycle()

    LaunchedEffect(id) {
        viewModel.loadDetail(id)
        viewModel.loadReviews(id)
    }

    Column(Modifier.fillMaxSize()) {
        MarketplaceNotice(
            message = actionMessage ?: reviewNotice,
            onDismiss = {
                viewModel.dismissActionMessage()
                reviewViewModel.dismissNotice()
            },
        )
        Box(Modifier.weight(1f)) {
            MarketplaceLoadContainer(state, { viewModel.loadDetail(id) }) { detail ->
                MarketplaceBusinessDetailContent(
                    detail = detail,
                    reviewsState = reviewsState,
                    mediaUrls = mediaUrls,
                    onNavigate = onNavigate,
                    onSaved = viewModel::toggleSaved,
                    onAddReview = { rating, title, body, photos ->
                        viewModel.addLocalReview(detail.card.id, rating, title, body, photos)
                    },
                    onReportBusiness = { reason, details ->
                        reviewViewModel.reportBusiness(detail.card.id, reason, details)
                    },
                )
            }
        }
    }
}

internal fun launchDeviceMapDirections(
    context: android.content.Context,
    location: za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation?,
    businessName: String,
    localityFallback: String? = null,
) {
    val query = when {
        location?.latitude != null && location.longitude != null -> {
            "${location.latitude},${location.longitude}(${businessName})"
        }
        !location?.address.isNullOrBlank() -> {
            listOfNotNull(location.address, location.locality, location.municipality, location.province)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
        !localityFallback.isNullOrBlank() -> "$localityFallback, $businessName"
        else -> businessName
    }
    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {}
    }
}

enum class ReviewSortOption(val label: String) {
    MOST_RECENT("Most Recent"),
    HIGHEST_RATED("Highest Rated"),
    LOWEST_RATED("Lowest Rated"),
}

@Composable
private fun MarketplaceBusinessDetailContent(
    detail: MarketplaceBusinessDetail,
    reviewsState: MarketplaceLoadState<Pair<List<za.org.rtc.community.feature.marketplace.domain.MarketplaceReview>, za.org.rtc.community.feature.marketplace.domain.MarketplaceRating>>,
    mediaUrls: Map<String, String>,
    onNavigate: (String) -> Unit,
    onSaved: (String, Boolean) -> Unit,
    onAddReview: (Int, String, String, List<String>) -> Unit,
    onReportBusiness: (reason: String, details: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var showReviewDialog by rememberSaveable { mutableStateOf(false) }
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var selectedReviewPhotoUrl by remember { mutableStateOf<String?>(null) }
    var reviewSortOption by rememberSaveable { mutableStateOf(ReviewSortOption.MOST_RECENT) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var isBookmarked by rememberSaveable(detail.saved) { mutableStateOf(detail.saved) }

    val activeRating = remember(reviewsState, detail.rating) {
        (reviewsState as? MarketplaceLoadState.Data)?.value?.second ?: detail.rating
    }
    val activeReviews = remember(reviewsState) {
        (reviewsState as? MarketplaceLoadState.Data)?.value?.first.orEmpty()
    }
    val sortedReviews = remember(activeReviews, reviewSortOption) {
        when (reviewSortOption) {
            ReviewSortOption.MOST_RECENT -> activeReviews
            ReviewSortOption.HIGHEST_RATED -> activeReviews.sortedByDescending { it.rating }
            ReviewSortOption.LOWEST_RATED -> activeReviews.sortedBy { it.rating }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // App Store Top Hero / Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Top App Store Style Product Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppStoreSquircleLogo(
                        name = detail.card.displayName,
                        category = detail.card.category,
                        logoUrl = detail.card.logoPath?.let { mediaUrls[it] },
                        size = 80.dp,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = detail.card.displayName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp,
                                ),
                            )
                            if (detail.card.verified) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified by RTC Community Admins",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = detail.card.category,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (detail.card.verified) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "VERIFIED PROVIDER",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        detail.card.tagline.takeIf(String::isNotBlank)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }

                        // App Store GET Pill Button & Bookmark
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppStoreGetButton(
                                text = "REQUEST",
                                onClick = {
                                    val firstOffering = detail.offerings.firstOrNull()
                                    onNavigate(
                                        RtcRoute.serviceCentreRequest(
                                            providerId = detail.card.id,
                                            businessId = detail.card.id,
                                            offeringId = firstOffering?.id,
                                        )
                                    )
                                },
                            )

                            IconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_SUBJECT, "Check out ${detail.card.displayName}")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out ${detail.card.displayName} on RTC Marketplace!\n" +
                                                "Category: ${detail.card.category}\n" +
                                                "Location: ${detail.card.locality}\n" +
                                                (detail.card.tagline.takeIf { it.isNotBlank() }?.let { "• $it\n" } ?: "")
                                        )
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share ${detail.card.displayName}")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share Business Profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    val nextState = !isBookmarked
                                    isBookmarked = nextState
                                    onSaved(detail.card.id, nextState)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isBookmarked) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = if (isBookmarked) "Saved to bookmarks" else "Save bookmark",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = { showReportDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Flag,
                                    contentDescription = "Report Business",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // App Store Highlights Strip (Rating, Locality, Status, Verified)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${activeRating.count} RATINGS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "%.1f".format(activeRating.average),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp).padding(start = 2.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DISTANCE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${((detail.card.distanceMetres ?: 850) / 1000.0).let { "%.1f".format(it) }} km",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    val openingStatus = detail.primaryOpeningStatus
                    val statusColor = when (openingStatus) {
                        MarketplaceOpeningStatus.OpenNow, MarketplaceOpeningStatus.Open24Hours -> Color(0xFF2E7D32)
                        MarketplaceOpeningStatus.Closed -> Color(0xFFC62828)
                        MarketplaceOpeningStatus.ByAppointment -> Color(0xFF1565C0)
                        MarketplaceOpeningStatus.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = openingStatus.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = statusColor,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Action Shortcuts Grid (Directions, Call, Map, Web)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val primaryLocation = detail.locations.firstOrNull()
                    Button(
                        onClick = {
                            launchDeviceMapDirections(
                                context = context,
                                location = primaryLocation,
                                businessName = detail.card.displayName,
                                localityFallback = detail.card.locality,
                            )
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Get Directions", maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    detail.phone?.let { phone ->
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
                            modifier = Modifier.weight(0.85f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Call", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    detail.locations.firstOrNull()?.let { loc ->
                        OutlinedButton(
                            onClick = { onNavigate("community/marketplace/business/${detail.card.id}/directions/${loc.id}") },
                            modifier = Modifier.weight(0.85f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("In-App", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    detail.websiteUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            modifier = Modifier.weight(0.85f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Web", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Photo Gallery Carousel Section
        item {
            BusinessGallerySection(
                photos = detail.resolvedGalleryPhotos,
                businessName = detail.card.displayName,
            )
        }

        // About Description Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "About this Business",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
        }

        // Recent Owner Activity & Announcements Section
        item {
            BusinessActivitySection(
                detail = detail,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Services & Products Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Services & Offerings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )

                if (detail.offerings.isEmpty()) {
                    Text(
                        text = "No public offerings have been listed yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                detail.offerings.forEach { offering ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = offering.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = offering.priceLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            offering.description.takeIf(String::isNotBlank)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                offering.availabilityNote?.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )
                                } ?: Spacer(Modifier.width(1.dp))

                                Button(
                                    onClick = {
                                        onNavigate(
                                            RtcRoute.serviceCentreRequest(
                                                providerId = detail.card.id,
                                                businessId = detail.card.id,
                                                offeringId = offering.id,
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("Book", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Locations & Operating Hours
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Locations & Hours",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )

                if (detail.locations.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(detail.card.locality.ifBlank { "Service Area" }, fontWeight = FontWeight.Bold)
                            }
                            Text("Operating hours available on booking request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                detail.locations.forEach { location ->
                    MarketplaceLocationCard(
                        location = location,
                        businessName = detail.card.displayName,
                        onGetDirections = {
                            launchDeviceMapDirections(
                                context = context,
                                location = location,
                                businessName = detail.card.displayName,
                                localityFallback = location.locality,
                            )
                        },
                    )
                }
            }
        }

        // App Store Ratings & Reviews Hub
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ratings & Reviews",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "See All (${activeRating.count})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigate("community/marketplace/business/${detail.card.id}/reviews") },
                    )
                }

                // App Store Rating Summary Card
                AppStoreRatingSummary(
                    average = activeRating.average,
                    count = activeRating.count,
                    distribution = activeRating.distribution,
                )

                // "Write a Review" interactive Button
                OutlinedButton(
                    onClick = { showReviewDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Write a Review", fontWeight = FontWeight.Bold)
                }

                // Reviews Section Header with Sorting Dropdown
                if (sortedReviews.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Reviews (${sortedReviews.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )

                        // Sorting Dropdown Trigger
                        Box {
                            Surface(
                                onClick = { sortDropdownExpanded = true },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Sort,
                                        contentDescription = "Sort Reviews",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = reviewSortOption.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = sortDropdownExpanded,
                                onDismissRequest = { sortDropdownExpanded = false },
                            ) {
                                ReviewSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (reviewSortOption == option) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                } else {
                                                    Spacer(Modifier.width(16.dp))
                                                }
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (reviewSortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (reviewSortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        },
                                        onClick = {
                                            reviewSortOption = option
                                            sortDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Resident Reviews List (Sorted)
                    sortedReviews.forEach { review ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        repeat(5) { index ->
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = if (index < review.rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = review.createdAt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                review.title.takeIf(String::isNotBlank)?.let {
                                    Text(it, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(review.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                // Attached Review Photos Thumbnail Row
                                if (review.photos.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        itemsIndexed(review.photos) { idx, photoUrl ->
                                            Card(
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .clickable { selectedReviewPhotoUrl = photoUrl },
                                            ) {
                                                Box(Modifier.fillMaxSize()) {
                                                    MarketplaceProgressiveImage(
                                                        url = photoUrl,
                                                        contentDescription = "Review attached photo ${idx + 1}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(3.dp)
                                                            .size(20.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.6f)),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.ZoomIn,
                                                            contentDescription = "Zoom photo",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                review.response?.let { resp ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text("Response from owner", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            Text(resp.body, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
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

    // Review Photo Full-Screen Viewer Dialog
    selectedReviewPhotoUrl?.let { photoUrl ->
        AlertDialog(
            onDismissRequest = { selectedReviewPhotoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize(),
            confirmButton = {},
            dismissButton = {},
            title = null,
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    IconButton(
                        onClick = { selectedReviewPhotoUrl = null },
                        modifier = Modifier
                            .padding(top = 28.dp, start = 16.dp)
                            .align(Alignment.TopStart)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close photo",
                            tint = Color.White,
                        )
                    }

                    MarketplaceProgressiveImage(
                        url = photoUrl,
                        contentDescription = "Review attached photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    )
                }
            },
        )
    }
}

@Composable
fun MarketplaceStatusRoute(
    businessId: String,
    viewModel: MarketplaceOwnerViewModel = hiltViewModel(),
) {
    val state by viewModel.status.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.loadStatus(businessId) }
    MarketplaceLoadContainer(state, { viewModel.loadStatus(businessId) }) { status ->
        LazyColumn(
            Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            item {
                Text("Business status", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(status.lifecycleState.replace('_', ' '))
            }
            items(status.revisions, key = { it.id }) { revision ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("Revision ${revision.number} · ${revision.state.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                        revision.feedback?.let { Text(it) }
                        revision.submittedAt?.let { Text("Submitted $it", style = MaterialTheme.typography.bodySmall) }
                        revision.reviewedAt?.let { Text("Reviewed $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun MarketplaceLocationCard(
    location: MarketplaceLocation,
    businessName: String,
    onGetDirections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedSchedule by rememberSaveable { mutableStateOf(false) }
    val evaluator = remember { MarketplaceHoursEvaluator() }
    val status = remember(location) { evaluator.evaluate(location) }
    val todaySummary = remember(location) { evaluator.todaySummary(location) }
    val weeklySchedule = remember(location) { evaluator.weeklySchedule(location) }

    val statusContainerColor = when (status) {
        MarketplaceOpeningStatus.OpenNow, MarketplaceOpeningStatus.Open24Hours -> Color(0xFFE8F5E9)
        MarketplaceOpeningStatus.Closed -> Color(0xFFFFEBEE)
        MarketplaceOpeningStatus.ByAppointment -> Color(0xFFE3F2FD)
        MarketplaceOpeningStatus.Unavailable -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusContentColor = when (status) {
        MarketplaceOpeningStatus.OpenNow, MarketplaceOpeningStatus.Open24Hours -> Color(0xFF2E7D32)
        MarketplaceOpeningStatus.Closed -> Color(0xFFC62828)
        MarketplaceOpeningStatus.ByAppointment -> Color(0xFF1565C0)
        MarketplaceOpeningStatus.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Location Header & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = location.label.ifBlank { "Primary Location" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusContainerColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusContentColor)
                        )
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusContentColor,
                        )
                    }
                }
            }

            // Address Details
            val fullAddress = listOfNotNull(location.address, location.locality, location.municipality, location.province)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (fullAddress.isNotBlank()) {
                Text(
                    text = fullAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            location.parkingNote?.takeIf(String::isNotBlank)?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalParking,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Today's Hours Highlight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Today's Hours",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Text(
                    text = todaySummary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (status.isOpen) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Expandable Weekly Schedule Accordion
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expandedSchedule = !expandedSchedule }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expandedSchedule) "Hide weekly schedule" else "View full weekly hours",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (expandedSchedule) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (expandedSchedule) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        weeklySchedule.forEach { dayInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (dayInfo.isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = dayInfo.dayName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = if (dayInfo.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (dayInfo.isToday) {
                                        Text(
                                            text = "(Today)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    text = dayInfo.summary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (dayInfo.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Holiday / Single-date exceptions if present
                        if (location.hourExceptions.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Special Holiday Hours:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            location.hourExceptions.forEach { exception ->
                                Text(
                                    text = exception.marketplaceDisplayLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Get Directions Button
            Button(
                onClick = onGetDirections,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Directions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun MarketplaceHoursInterval.marketplaceDisplayLabel(): String {
    val day = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").getOrElse(dayOfWeek) { "Day $dayOfWeek" }
    val stateLabel = when (state) {
        "OPEN" -> listOfNotNull(opensAt, closesAt).joinToString(" – ")
        "OPEN_24_HOURS" -> "Open 24 hours"
        "APPOINTMENT_ONLY" -> "By appointment"
        "CLOSED" -> "Closed"
        else -> "Schedule unavailable"
    }
    return "$day · $stateLabel"
}

private fun MarketplaceHoursException.marketplaceDisplayLabel(): String {
    val stateLabel = when (state) {
        "OPEN" -> listOfNotNull(opensAt, closesAt).joinToString(" – ")
        "OPEN_24_HOURS" -> "Open 24 hours"
        "APPOINTMENT_ONLY" -> "By appointment"
        "CLOSED" -> "Closed"
        else -> "Schedule unavailable"
    }
    return listOfNotNull(date, stateLabel, note?.takeIf(String::isNotBlank)).joinToString(" · ")
}

@Composable
private fun ReportBusinessDialog(
    businessName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit,
) {
    val reasons = listOf(
        "Incorrect information or address",
        "Inappropriate or offensive content",
        "Fake listing or scam",
        "Permanently closed",
        "Spam or misleading information",
        "Other",
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var detailsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $businessName", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select a reason to flag this business listing for moderation:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it },
                    label = { Text("Additional details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, detailsText) },
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

fun dispatchBusinessAnnouncementNotification(
    context: android.content.Context,
    businessName: String,
    title: String,
    body: String,
) {
    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
    val intent = Intent(context, za.org.rtc.community.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        context,
        (System.currentTimeMillis() % 100000).toInt(),
        intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = androidx.core.app.NotificationCompat.Builder(
        context,
        za.org.rtc.community.notifications.RTC_COMMUNITY_UPDATES_CHANNEL,
    )
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("📢 $businessName Announcement")
        .setContentText("$title: $body")
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    } catch (_: Exception) {}
}

private data class ActivityPostData(
    val id: String,
    val title: String,
    val body: String,
    val dateLabel: String,
    val categoryBadge: String,
    val initialLikes: Int,
)

@Composable
fun BusinessActivitySection(
    detail: MarketplaceBusinessDetail,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var posts by remember(detail) {
        val list = mutableListOf<ActivityPostData>()
        list.add(
            ActivityPostData(
                id = "act-1",
                title = "Welcome to ${detail.card.displayName}!",
                body = "We're active on RTC Community Marketplace! Explore our offerings, view location directions, or submit a booking request directly from our profile.",
                dateLabel = "Recently",
                categoryBadge = "ANNOUNCEMENT",
                initialLikes = 14,
            )
        )
        if (detail.card.verified) {
            list.add(
                ActivityPostData(
                    id = "act-2",
                    title = "Verified Community Provider",
                    body = "Our business listing has been confirmed and verified by RTC community admins. We are dedicated to providing trusted and high quality local service.",
                    dateLabel = "Verified Status",
                    categoryBadge = "VERIFIED",
                    initialLikes = 32,
                )
            )
        }
        if (detail.offerings.isNotEmpty()) {
            val firstOffering = detail.offerings.first()
            list.add(
                ActivityPostData(
                    id = "act-3",
                    title = "Featured Service: ${firstOffering.title}",
                    body = "${firstOffering.description.ifBlank { "Now accepting direct community requests for " + firstOffering.title }}. Pricing: ${firstOffering.priceLabel}.",
                    dateLabel = "2 days ago",
                    categoryBadge = "SERVICE UPDATE",
                    initialLikes = 9,
                )
            )
        }
        mutableStateOf(list.toList())
    }

    var showPostDialog by remember { mutableStateOf(false) }
    var notificationNoticeMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Activity & Announcements",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            OutlinedButton(
                onClick = { showPostDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Post Update", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Bookmarked Push Notification Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (detail.saved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (detail.saved) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    tint = if (detail.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (detail.saved) "🔔 Push Notification Alerts Active" else "Bookmark for Push Alerts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (detail.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (detail.saved)
                            "You are bookmarked to receive instant device notifications whenever ${detail.card.displayName} posts new announcements or updates."
                        else
                            "Save/Bookmark this business (❤️ Save) to receive instant push notification alerts when new announcements or offers are posted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Action Notice Banner when a Push Alert was dispatched
        notificationNoticeMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { notificationNoticeMessage = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        posts.forEach { post ->
            ActivityPostCard(
                post = post,
                businessName = detail.card.displayName,
                isVerified = detail.card.verified,
                categoryName = detail.card.category,
            )
        }
    }

    if (showPostDialog) {
        NewAnnouncementDialog(
            businessName = detail.card.displayName,
            onDismiss = { showPostDialog = false },
            onSubmit = { title, body, badge ->
                showPostDialog = false
                val newPost = ActivityPostData(
                    id = "act_${System.currentTimeMillis()}",
                    title = title,
                    body = body,
                    dateLabel = "Just now",
                    categoryBadge = badge,
                    initialLikes = 1,
                )
                posts = listOf(newPost) + posts

                // Dispatch actual Android device push notification for bookmarked users
                dispatchBusinessAnnouncementNotification(
                    context = context,
                    businessName = detail.card.displayName,
                    title = title,
                    body = body,
                )

                notificationNoticeMessage = "📢 Announcement published! Instant push notification alert sent to residents who bookmarked ${detail.card.displayName}."
            },
        )
    }
}

@Composable
private fun NewAnnouncementDialog(
    businessName: String,
    onDismiss: () -> Unit,
    onSubmit: (title: String, body: String, badge: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedBadge by remember { mutableStateOf("ANNOUNCEMENT") }

    val badges = listOf("ANNOUNCEMENT", "SPECIAL OFFER", "EVENT", "SERVICE UPDATE")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Post Business Update", fontWeight = FontWeight.Bold)
                Text(
                    "Publish an announcement & alert bookmarked residents of $businessName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Announcement Title") },
                    placeholder = { Text("e.g. 20% Off Weekend Special / New Opening Hours") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Announcement Details") },
                    placeholder = { Text("Write the update details for community residents...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )

                Text(
                    text = "Category Badge:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    badges.forEach { badge ->
                        FilterChip(
                            selected = selectedBadge == badge,
                            onClick = { selectedBadge = badge },
                            label = { Text(badge, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) {
                        onSubmit(title, body, selectedBadge)
                    }
                },
                enabled = title.isNotBlank() && body.isNotBlank(),
                shape = CircleShape,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Publish & Alert Bookmarked")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ActivityPostCard(
    post: ActivityPostData,
    businessName: String,
    isVerified: Boolean,
    categoryName: String,
) {
    var liked by rememberSaveable { mutableStateOf(false) }
    var likesCount by rememberSaveable { mutableStateOf(post.initialLikes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppStoreSquircleLogo(
                        name = businessName,
                        category = categoryName,
                        size = 40.dp,
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = businessName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            if (isVerified) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified by RTC Community",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = post.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (post.categoryBadge == "VERIFIED") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = post.categoryBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (post.categoryBadge == "VERIFIED") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )

            Text(
                text = post.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            liked = !liked
                            if (liked) likesCount++ else likesCount--
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (liked) "Unlike post" else "Like post",
                            tint = if (liked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = "$likesCount likes",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Owner Post",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BusinessGallerySection(
    photos: List<String>,
    businessName: String,
) {
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Photo Gallery",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "${photos.size} Photos",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Horizontal Image Carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(photos) { index, photoUrl ->
                Card(
                    modifier = Modifier
                        .width(260.dp)
                        .height(170.dp)
                        .clickable { selectedPhotoIndex = index },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MarketplaceProgressiveImage(
                            url = photoUrl,
                            contentDescription = "Gallery photo ${index + 1} for $businessName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Dark Gradient Overlay at Bottom
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f),
                                        ),
                                    ),
                                ),
                        )

                        // Bottom Caption Pill
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = "Photo ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        // Top-right Fullscreen Zoom Affordance
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ZoomIn,
                                contentDescription = "View photo fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Viewer Modal Dialog
    selectedPhotoIndex?.let { currentIndex ->
        FullScreenGalleryViewerDialog(
            photos = photos,
            initialIndex = currentIndex,
            businessName = businessName,
            onDismiss = { selectedPhotoIndex = null },
        )
    }
}

@Composable
private fun FullScreenGalleryViewerDialog(
    photos: List<String>,
    initialIndex: Int,
    businessName: String,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize(),
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }

                    Text(
                        text = "${currentIndex + 1} of ${photos.size}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.width(40.dp))
                }

                // Centered Main Photo
                MarketplaceProgressiveImage(
                    url = photos.getOrNull(currentIndex),
                    contentDescription = "Full photo ${currentIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 80.dp),
                )

                // Navigation Prev / Next Arrows
                if (currentIndex > 0) {
                    IconButton(
                        onClick = { currentIndex-- },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Photo",
                            tint = Color.White,
                        )
                    }
                }

                if (currentIndex < photos.size - 1) {
                    IconButton(
                        onClick = { currentIndex++ },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Photo",
                            tint = Color.White,
                        )
                    }
                }

                // Bottom Business Title & Caption
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = businessName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Photo ${currentIndex + 1} of ${photos.size} · Community Showcase",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        },
    )
}

