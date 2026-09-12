package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminQueue
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminSubmissionDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessLifecycleAction
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

private enum class AdminConfirmation { REQUEST_CHANGES, PUBLISH, REJECT, SUSPEND, REINSTATE }

@Composable
fun MarketplaceAdminRoute(
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceAdminViewModel = hiltViewModel(),
) {
    val state by viewModel.queue.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    MarketplaceLoadContainer(state, viewModel::load) { queue ->
        MarketplaceAdminDashboard(queue, notice, onNavigate, viewModel)
    }
}

@Composable
private fun MarketplaceAdminDashboard(
    queue: MarketplaceAdminQueue,
    notice: String?,
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceAdminViewModel,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                Text("Marketplace Operations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            MarketplaceNotice(notice, viewModel::dismissNotice)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                listOf(
                    "Pending" to queue.metrics.pendingListings,
                    "Changes" to queue.metrics.changesRequested,
                    "Published" to queue.metrics.publishedBusinesses,
                    "Locations" to queue.metrics.activeLocations,
                    "Flagged reviews" to queue.metrics.flaggedReviews,
                    "Suspended" to queue.metrics.suspendedListings,
                ).forEach { (label, count) -> AssistChip(onClick = {}, label = { Text("$label: $count") }) }
            }
        }
        item {
            Text("Admin Tools & Modules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(RtcSpacing.compact))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                OutlinedButton(
                    onClick = { onNavigate("admin/marketplace/reviews") },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(RtcSize.inlineIcon))
                    Spacer(Modifier.width(RtcSpacing.iconLabel))
                    Text("Review Triage (${queue.metrics.flaggedReviews})")
                }
                OutlinedButton(
                    onClick = { onNavigate("admin/marketplace/categories") },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(RtcSize.inlineIcon))
                    Spacer(Modifier.width(RtcSpacing.iconLabel))
                    Text("Categories Taxonomy")
                }
                OutlinedButton(
                    onClick = { onNavigate("admin/marketplace/featured") },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(RtcSize.inlineIcon))
                    Spacer(Modifier.width(RtcSpacing.iconLabel))
                    Text("Featured Spots")
                }
                OutlinedButton(
                    onClick = { onNavigate("admin/marketplace/analytics") },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(RtcSize.inlineIcon))
                    Spacer(Modifier.width(RtcSpacing.iconLabel))
                    Text("Analytics & Growth")
                }
            }
        }
        item {
            Text("Publication queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Queue contents and metrics come from the content-admin-protected Marketplace RPC.", style = MaterialTheme.typography.bodySmall)
        }
        if (queue.items.isEmpty()) item { Text("No Marketplace submissions currently require publication review.") }
        items(queue.items, key = { it.id }) { submission ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                    Text(submission.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(submission.state.replace('_', ' '))
                    submission.assignedTo?.let { Text("Assigned reviewer: $it", style = MaterialTheme.typography.bodySmall) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                        if (submission.assignedTo == null) {
                            OutlinedButton(onClick = { viewModel.assign(submission.id) }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Assign to me") }
                        }
                        Button(
                            onClick = { onNavigate("admin/marketplace/business/${submission.id}") },
                            modifier = Modifier.height(RtcSize.minimumTouchTarget),
                        ) { Text("Review submission") }
                    }
                }
            }
        }
    }
}

@Composable
fun MarketplaceAdminSubmissionRoute(
    submissionId: String,
    canModerateLifecycle: Boolean,
    onBack: () -> Unit,
    viewModel: MarketplaceAdminViewModel = hiltViewModel(),
) {
    val state by viewModel.detail.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(submissionId) { viewModel.loadDetail(submissionId) }
    MarketplaceLoadContainer(state, { viewModel.loadDetail(submissionId) }) { detail ->
        MarketplaceAdminSubmissionContent(detail, canModerateLifecycle, notice, onBack, viewModel)
    }
}

@Composable
private fun MarketplaceAdminSubmissionContent(
    detail: MarketplaceAdminSubmissionDetail,
    canModerateLifecycle: Boolean,
    notice: String?,
    onBack: () -> Unit,
    viewModel: MarketplaceAdminViewModel,
) {
    var confirmation by remember { mutableStateOf<AdminConfirmation?>(null) }
    var feedback by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back to queue") }
            Text(detail.displayName.ifBlank { "Marketplace submission" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Submission ${detail.submissionState.replace('_', ' ')} · revision ${detail.revisionState.replace('_', ' ')}")
            Text("Business lifecycle: ${detail.lifecycleState.replace('_', ' ')}")
            MarketplaceNotice(notice, viewModel::dismissNotice)
        }
        item {
            MarketplaceDetailSection("Submitted revision") {
                val revision = detail.submittedRevision
                Text(revision.marketplaceString("tagline"))
                Text(revision.marketplaceString("description"))
                val phone = revision.marketplaceString("public_phone")
                val email = revision.marketplaceString("public_email")
                val website = revision.marketplaceString("website_url")
                if (phone.isNotBlank()) Text("Phone: $phone")
                if (email.isNotBlank()) Text("Email: $email")
                if (website.isNotBlank()) Text("Website: $website")
            }
        }
        item { AdminJsonCollection("Locations", detail.locations) { item -> listOf(item.marketplaceString("label"), item.marketplaceString("locality"), item.marketplaceString("address_visibility")).filter(String::isNotBlank).joinToString(" · ") } }
        item { AdminJsonCollection("Offerings", detail.offerings) { item -> listOf(item.marketplaceString("title"), item.marketplaceString("offering_type"), item.marketplaceString("price_type")).filter(String::isNotBlank).joinToString(" · ") } }
        item { AdminJsonCollection("Media", detail.media) { item -> listOf(item.marketplaceString("asset_type"), item.marketplaceString("alt_text")).filter(String::isNotBlank).joinToString(" · ") } }
        item { AdminJsonCollection("Verification", detail.verification) { item -> listOf(item.marketplaceString("verification_type"), item.marketplaceString("state")).filter(String::isNotBlank).joinToString(" · ") } }
        item { AdminJsonCollection("Audit history", detail.history) { item -> listOf(item.marketplaceString("event_type"), item.marketplaceString("created_at")).filter(String::isNotBlank).joinToString(" · ") } }
        item {
            MarketplaceDetailSection("Publication decision") {
                Text("Every decision requires explicit confirmation. PostgreSQL RPC authorization remains authoritative.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                    OutlinedButton(onClick = { confirmation = AdminConfirmation.REQUEST_CHANGES }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Request changes") }
                    Button(onClick = { confirmation = AdminConfirmation.PUBLISH }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Approve & publish") }
                    OutlinedButton(onClick = { confirmation = AdminConfirmation.REJECT }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Reject") }
                }
            }
        }
        if (canModerateLifecycle && detail.lifecycleActions.isNotEmpty()) {
            item {
                MarketplaceDetailSection("Lifecycle moderation") {
                    if (MarketplaceBusinessLifecycleAction.SUSPEND in detail.lifecycleActions) {
                        OutlinedButton(onClick = { confirmation = AdminConfirmation.SUSPEND }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Suspend published business") }
                    }
                    if (MarketplaceBusinessLifecycleAction.REINSTATE in detail.lifecycleActions) {
                        Button(onClick = { confirmation = AdminConfirmation.REINSTATE }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Reinstate business") }
                    }
                }
            }
        }
    }

    confirmation?.let { action ->
        val needsFeedback = action in setOf(AdminConfirmation.REQUEST_CHANGES, AdminConfirmation.REJECT, AdminConfirmation.SUSPEND)
        ConfirmMarketplaceActionDialog(
            title = when (action) {
                AdminConfirmation.REQUEST_CHANGES -> "Request changes?"
                AdminConfirmation.PUBLISH -> "Publish this business?"
                AdminConfirmation.REJECT -> "Reject this submission?"
                AdminConfirmation.SUSPEND -> "Suspend this business?"
                AdminConfirmation.REINSTATE -> "Reinstate this business?"
            },
            message = when (action) {
                AdminConfirmation.PUBLISH -> "The submitted revision becomes the public Marketplace revision after the backend validates this decision."
                AdminConfirmation.REINSTATE -> "The backend will restore the suspended business only if its lifecycle state still permits reinstatement."
                else -> "Provide a clear reason. The backend will validate your role and the current authoritative state before applying this action."
            },
            confirmLabel = when (action) {
                AdminConfirmation.REQUEST_CHANGES -> "Request changes"
                AdminConfirmation.PUBLISH -> "Publish"
                AdminConfirmation.REJECT -> "Reject"
                AdminConfirmation.SUSPEND -> "Suspend"
                AdminConfirmation.REINSTATE -> "Reinstate"
            },
            destructive = action in setOf(AdminConfirmation.REJECT, AdminConfirmation.SUSPEND),
            feedbackLabel = if (needsFeedback) "Reason / feedback" else null,
            feedback = feedback,
            onFeedbackChange = { feedback = it },
            onDismiss = { confirmation = null; feedback = "" },
            onConfirm = {
                confirmation = null
                when (action) {
                    AdminConfirmation.REQUEST_CHANGES -> viewModel.requestChanges(detail.submissionId, feedback)
                    AdminConfirmation.PUBLISH -> viewModel.publish(detail.submissionId)
                    AdminConfirmation.REJECT -> viewModel.reject(detail.submissionId, feedback)
                    AdminConfirmation.SUSPEND -> viewModel.suspendBusiness(detail.businessId, feedback)
                    AdminConfirmation.REINSTATE -> viewModel.reinstateBusiness(detail.businessId)
                }
                feedback = ""
            },
        )
    }
}

@Composable
private fun AdminJsonCollection(title: String, values: List<kotlinx.serialization.json.JsonObject>, label: (kotlinx.serialization.json.JsonObject) -> String) {
    MarketplaceDetailSection(title) {
        if (values.isEmpty()) Text("None supplied.")
        values.forEach { value ->
            Card(Modifier.fillMaxWidth()) {
                Text(label(value).ifBlank { "Recorded item" }, modifier = Modifier.padding(RtcSpacing.cardPadding))
            }
        }
    }
}

@Composable
fun MarketplaceAdminBoundaryPlaceholder(title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
    ) {
        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(message)
        Text("This route is still protected by the centralized RouteAccessPolicy and the backend remains authoritative for all data access and mutations.", style = MaterialTheme.typography.bodySmall)
    }
}
