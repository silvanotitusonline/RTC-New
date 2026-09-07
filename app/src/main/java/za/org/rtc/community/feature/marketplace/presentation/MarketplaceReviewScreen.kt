package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReviewReportReason
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun MarketplaceReviewsRoute(
    businessId: String,
    onBack: () -> Unit,
    discoveryViewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
    reviewViewModel: MarketplaceReviewViewModel = hiltViewModel(),
) {
    val state by discoveryViewModel.reviews.collectAsStateWithLifecycle()
    val notice by reviewViewModel.notice.collectAsStateWithLifecycle()
    val ownedBusinesses by reviewViewModel.ownedBusinessIds.collectAsStateWithLifecycle()
    val helpfulVotes by reviewViewModel.helpfulVotes.collectAsStateWithLifecycle()
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var deleteReview by remember { mutableStateOf<MarketplaceReview?>(null) }
    var reportReview by remember { mutableStateOf<MarketplaceReview?>(null) }
    var respondReview by remember { mutableStateOf<MarketplaceReview?>(null) }

    LaunchedEffect(businessId) { discoveryViewModel.loadReviews(businessId) }
    LazyColumn(
        Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        item {
            Text("Business reviews", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            MarketplaceNotice(notice, reviewViewModel::dismissNotice)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text("Write or update your review", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                    (1..5).forEach { value ->
                        FilterChip(selected = rating == value, onClick = { rating = value }, label = { Text("$value stars") })
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Review title (optional)") })
                OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Review (optional)") }, minLines = 3)
                Button(
                    onClick = { reviewViewModel.save(businessId, rating, title, body) { discoveryViewModel.loadReviews(businessId) } },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) { Text("Save review") }
            }
        }
        item {
            MarketplaceLoadContainer(state, { discoveryViewModel.loadReviews(businessId) }) { pair ->
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                    AppStoreRatingSummary(
                        average = pair.second.average,
                        count = pair.second.count,
                        distribution = pair.second.distribution,
                    )
                    pair.first.forEach { review ->
                        ReviewCard(
                            review = review,
                            helpfulSelected = helpfulVotes[review.id] == true,
                            canRespond = businessId in ownedBusinesses && !review.isMine,
                            onHelpful = { selected -> reviewViewModel.voteHelpful(review.id, selected) { discoveryViewModel.loadReviews(businessId) } },
                            onReport = { reportReview = review },
                            onRespond = { respondReview = review },
                            onDelete = { deleteReview = review },
                        )
                    }
                }
            }
        }
    }

    deleteReview?.let { review ->
        ConfirmMarketplaceActionDialog(
            title = "Delete your review?",
            message = "This permanently removes your Marketplace review. This action is sent through the authenticated review RPC.",
            confirmLabel = "Delete review",
            destructive = true,
            onDismiss = { deleteReview = null },
            onConfirm = {
                deleteReview = null
                reviewViewModel.delete(review.id) { discoveryViewModel.loadReviews(businessId) }
            },
        )
    }
    reportReview?.let { review -> ReportReviewDialog(review, onDismiss = { reportReview = null }) { reason, details ->
        reportReview = null
        reviewViewModel.report(review.id, reason.code, details) { discoveryViewModel.loadReviews(businessId) }
    } }
    respondReview?.let { review -> RespondToReviewDialog(review, onDismiss = { respondReview = null }) { response ->
        respondReview = null
        reviewViewModel.respond(review.id, response) { discoveryViewModel.loadReviews(businessId) }
    } }
}

@Composable
private fun ReviewCard(
    review: MarketplaceReview,
    helpfulSelected: Boolean,
    canRespond: Boolean,
    onHelpful: (Boolean) -> Unit,
    onReport: () -> Unit,
    onRespond: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Text("${review.rating}/5", fontWeight = FontWeight.Bold)
            review.title.takeIf(String::isNotBlank)?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            review.body.takeIf(String::isNotBlank)?.let { Text(it) }
            review.response?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(RtcSpacing.contentGroup)) {
                        Text("Business response", fontWeight = FontWeight.SemiBold)
                        Text(it.body)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                FilterChip(
                    selected = helpfulSelected,
                    onClick = { onHelpful(!helpfulSelected) },
                    label = { Text("Helpful · ${review.helpfulCount}") },
                    leadingIcon = { Icon(Icons.Filled.ThumbUp, contentDescription = null) },
                )
                if (review.isMine) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Delete") }
                } else {
                    OutlinedButton(onClick = onReport, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Report") }
                    if (canRespond && review.response == null) {
                        Button(onClick = onRespond, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Respond as business") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportReviewDialog(
    review: MarketplaceReview,
    onDismiss: () -> Unit,
    onSubmit: (MarketplaceReviewReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf(MarketplaceReviewReportReason.SPAM) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text(review.title.ifBlank { "Review ${review.id.take(8)}" })
                Text("Choose the reason that best matches the issue. Reasons map exactly to backend-supported moderation codes.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                    MarketplaceReviewReportReason.entries.forEach { option ->
                        FilterChip(selected = reason == option, onClick = { reason = option }, label = { Text(option.label) })
                    }
                }
                OutlinedTextField(value = details, onValueChange = { details = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Additional details") }, minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(reason, details) }) { Text("Submit report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RespondToReviewDialog(review: MarketplaceReview, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var response by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Business response") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text(review.body.ifBlank { review.title.ifBlank { "Customer review" } })
                OutlinedTextField(value = response, onValueChange = { response = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Public response") }, minLines = 4)
                Text("The backend verifies that you are an authorised editor for this review's business before saving the response.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(response) }, enabled = response.isNotBlank()) { Text("Post response") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MarketplaceMyReviewsRoute(viewModel: MarketplaceReviewViewModel = hiltViewModel()) {
    val state by viewModel.mine.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var deleteReview by remember { mutableStateOf<MarketplaceReview?>(null) }
    LaunchedEffect(Unit) { viewModel.loadMine() }
    MarketplaceLoadContainer(state, viewModel::loadMine) { reviews ->
        LazyColumn(
            Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            item {
                Text("My Marketplace reviews", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                MarketplaceNotice(notice, viewModel::dismissNotice)
            }
            if (reviews.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(RtcSpacing.pageGutter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                        ) {
                            Icon(
                                Icons.Filled.RateReview,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(RtcSize.minimumTouchTarget),
                            )
                            Text(
                                "No Reviews Written Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Help local residents discover great service providers by leaving ratings and feedback on businesses you have used.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
            items(reviews, key = { it.id }) { review ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("${review.rating}/5", fontWeight = FontWeight.SemiBold)
                        review.title.takeIf(String::isNotBlank)?.let { Text(it) }
                        review.body.takeIf(String::isNotBlank)?.let { Text(it) }
                        OutlinedButton(onClick = { deleteReview = review }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Delete review") }
                    }
                }
            }
        }
    }
    deleteReview?.let { review ->
        ConfirmMarketplaceActionDialog(
            title = "Delete review?",
            message = "This permanently deletes your review.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteReview = null },
            onConfirm = { deleteReview = null; viewModel.delete(review.id) { viewModel.loadMine() } },
        )
    }
}
