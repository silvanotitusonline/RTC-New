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


data class FlaggedReviewItem(
    val id: String,
    val businessId: String,
    val businessName: String,
    val authorName: String,
    val rating: Int,
    val title: String,
    val body: String,
    val reportReason: String,
    val reportDetails: String,
    val reportCount: Int,
    val reportedAt: String,
    val status: String, // "PENDING", "APPROVED", "REMOVED"
)

@Composable
fun MarketplaceAdminReviewsRoute(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var selectedReviewForDetail by remember { mutableStateOf<FlaggedReviewItem?>(null) }
    var reviewToDismiss by remember { mutableStateOf<FlaggedReviewItem?>(null) }
    var reviewToRemove by remember { mutableStateOf<FlaggedReviewItem?>(null) }

    var flaggedReviews by remember {
        mutableStateOf(
            listOf(
                FlaggedReviewItem(
                    id = "rev-fl-101",
                    businessId = "biz-plumb-01",
                    businessName = "Khayelitsha 24/7 Plumbing Pro",
                    authorName = "Vuyo M.",
                    rating = 1,
                    title = "Suspected fake review / competitor attack",
                    body = "This business charged exorbitant rates and did not complete the job properly on Site B.",
                    reportReason = "Competitor / Fraudulent Claim",
                    reportDetails = "Customer account created 1 hour ago; no booking record found in our CRM system.",
                    reportCount = 3,
                    reportedAt = "2 hours ago",
                    status = "PENDING",
                ),
                FlaggedReviewItem(
                    id = "rev-fl-102",
                    businessId = "biz-auto-04",
                    businessName = "Apex Auto & Body Repairs",
                    authorName = "Anonymous",
                    rating = 1,
                    title = "Abusive and offensive language",
                    body = "Completely rude staff and total scam artists who don't know what they are doing.",
                    reportReason = "Harassment / Inappropriate Content",
                    reportDetails = "Contains defamatory accusations and personal insults targeted at service technician.",
                    reportCount = 5,
                    reportedAt = "Yesterday",
                    status = "PENDING",
                ),
                FlaggedReviewItem(
                    id = "rev-fl-103",
                    businessId = "biz-solar-02",
                    businessName = "GreenCape Solar & Inverters",
                    authorName = "David K.",
                    rating = 5,
                    title = "Cryptocurrency solicitation spam in review",
                    body = "For high yield investment contact WhatsApp +27 82 000 1234 guaranteed 500% returns.",
                    reportReason = "Spam / Promotion",
                    reportDetails = "External WhatsApp scam link posted in public review section.",
                    reportCount = 8,
                    reportedAt = "3 days ago",
                    status = "PENDING",
                ),
            )
        )
    }

    val filteredList = remember(flaggedReviews, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> flaggedReviews.filter { it.status == "PENDING" }
            "SPAM" -> flaggedReviews.filter { it.reportReason.contains("Spam", ignoreCase = true) }
            "HARASSMENT" -> flaggedReviews.filter { it.reportReason.contains("Harassment", ignoreCase = true) }
            "COMPETITOR" -> flaggedReviews.filter { it.reportReason.contains("Competitor", ignoreCase = true) }
            else -> flaggedReviews
        }
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
                    text = "Review Moderation Triage",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Review and resolve user-flagged reviews to maintain trust and safety across the Marketplace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Filter chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                listOf(
                    "ALL" to "All Flagged (${flaggedReviews.count { it.status == "PENDING" }})",
                    "SPAM" to "Spam & Links",
                    "HARASSMENT" to "Harassment & Abuse",
                    "COMPETITOR" to "Fraud / Competitor",
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = selectedFilter == code,
                        onClick = { selectedFilter = code },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Review list
        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = "No Flagged Reviews Pending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "All reported reviews in this category have been moderated and resolved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { review ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (review.status == "PENDING") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(RtcSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Header row: business name & report badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = review.businessName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "By ${review.authorName} · ${review.reportedAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Flag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "${review.reportCount} reports",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        // Rating & Review snippet
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "⭐ ${review.rating}/5",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (review.title.isNotBlank()) {
                                Text(
                                    text = "· ${review.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Text(
                            text = "\"${review.body}\"",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // Flag details
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Reported for: ${review.reportReason}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = review.reportDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Moderator action buttons
                        if (review.status == "PENDING") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                            ) {
                                Button(
                                    onClick = { reviewToDismiss = review },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Dismiss Flag (Approve)")
                                }
                                OutlinedButton(
                                    onClick = { reviewToRemove = review },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove Review")
                                }
                            }
                        } else {
                            Text(
                                text = "Status: ${review.status}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (review.status == "APPROVED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    // Dismiss flag dialog
    reviewToDismiss?.let { review ->
        AlertDialog(
            onDismissRequest = { reviewToDismiss = null },
            title = { Text("Approve Review & Dismiss Flag?") },
            text = {
                Text("This will mark the review as compliant with Community guidelines, dismiss the report flags, and keep it visible on ${review.businessName}.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        flaggedReviews = flaggedReviews.map {
                            if (it.id == review.id) it.copy(status = "APPROVED") else it
                        }
                        actionNotice = "Review approved and report flag dismissed."
                        reviewToDismiss = null
                    },
                ) { Text("Approve & Keep") }
            },
            dismissButton = {
                TextButton(onClick = { reviewToDismiss = null }) { Text("Cancel") }
            },
        )
    }

    // Remove review dialog
    reviewToRemove?.let { review ->
        AlertDialog(
            onDismissRequest = { reviewToRemove = null },
            title = { Text("Remove Review for Policy Violation?") },
            text = {
                Text("This permanently removes the review from public listing and notifies the reviewer regarding policy enforcement.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        flaggedReviews = flaggedReviews.map {
                            if (it.id == review.id) it.copy(status = "REMOVED") else it
                        }
                        actionNotice = "Review removed due to policy violation."
                        reviewToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Confirm Removal") }
            },
            dismissButton = {
                TextButton(onClick = { reviewToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

// -------------------------------------------------------------
// 2. Marketplace Admin Categories Management Screen
// -------------------------------------------------------------
