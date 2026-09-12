package za.org.rtc.community.feature.marketplace.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation
import za.org.rtc.community.ui.theme.RtcSpacing

internal const val MarketplaceRequestBookingLabel = "Request Booking"

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
            message = actionMessage,
            onDismiss = viewModel::dismissActionMessage,
        )
        MarketplaceNotice(
            message = reviewNotice,
            onDismiss = reviewViewModel::dismissNotice,
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
    location: MarketplaceLocation?,
    businessName: String,
    localityFallback: String? = null,
) {
    val query = when {
        location?.latitude != null && location.longitude != null ->
            "${location.latitude},${location.longitude}($businessName)"
        !location?.address.isNullOrBlank() ->
            listOfNotNull(location.address, location.locality, location.municipality, location.province)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        !localityFallback.isNullOrBlank() -> "$localityFallback, $businessName"
        else -> businessName
    }
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
    runCatching { context.startActivity(mapIntent) }.onFailure {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
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
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(RtcSpacing.pageGutter),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            item {
                Text("Business status", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(status.lifecycleState.replace('_', ' '))
            }
            items(status.revisions, key = { it.id }) { revision ->
                Card(Modifier.fillParentMaxWidth()) {
                    Column(
                        Modifier.padding(RtcSpacing.cardPadding),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(RtcSpacing.relatedText),
                    ) {
                        Text(
                            "Revision ${revision.number} · ${revision.state.replace('_', ' ')}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        revision.feedback?.let { Text(it) }
                        revision.submittedAt?.let { Text("Submitted $it", style = MaterialTheme.typography.bodySmall) }
                        revision.reviewedAt?.let { Text("Reviewed $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
