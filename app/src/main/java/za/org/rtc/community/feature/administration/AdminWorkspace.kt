package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.DraftArea
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.feature.home.ContinueDraftCard
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.PendingSyncIndicator
import za.org.rtc.community.ui.components.PurposefulEmptyState
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSpacing
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AdminWorkspace(
    viewModel: RtcViewModel,
    onOpenAi: () -> Unit,
    onOpenTool: (String) -> Unit,
    draft: LocalDraft?,
    onDiscardDraft: (LocalDraft) -> Unit,
    pendingSyncCount: Int,
    dashboardViewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val workItems by viewModel.operationsWorkQueue.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.accessRoleChangeRequests.collectAsStateWithLifecycle()
    val operationsUi by viewModel.operationsUi.collectAsStateWithLifecycle()

    var selectedQueueFilter by rememberSaveable { mutableStateOf("All") }
    var toolQuery by rememberSaveable { mutableStateOf("") }

    if (!session.role.isStaff) {
        PurposefulEmptyState(
            "This workspace is available only to authorised staff.",
            "Return to Home",
            {},
        )
        return
    }

    val needsLiveAdministratorMfa = session.role == UserRole.SYSTEM_ADMIN &&
        session.authority == SessionAuthority.SUPABASE_AUTH &&
        session.administratorMfaStatus != AdministratorMfaStatus.VERIFIED

    val roleDestinations = adminWorkspaceDestinations(session.role)
    val visibleDestinations = filterAdminWorkspaceDestinations(roleDestinations, toolQuery)
    val visibleWorkItems = workItems.filter { item ->
        when (selectedQueueFilter) {
            "Urgent" -> item.priority in setOf("URGENT", "HIGH")
            "Mine" -> item.assignedToMe
            "Unassigned" -> item.isUnassigned
            "Review" -> item.state == "READY_FOR_REVIEW"
            "Overdue" -> item.dueAt?.let { dueAt ->
                runCatching { Instant.parse(dueAt).isBefore(Instant.now()) }.getOrDefault(false)
            } ?: false
            else -> true
        }
    }

    LaunchedEffect(session.id, session.role) {
        viewModel.refreshOperationsHub()
        dashboardViewModel.refreshCounts()
        if (session.role == UserRole.SYSTEM_ADMIN) viewModel.refreshAccessManagement()
    }

    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(
                    "OPERATIONS HUB",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (session.role) {
                        UserRole.SYSTEM_ADMIN -> "Administrator workspace"
                        UserRole.MODERATOR, UserRole.EVIDENCE_REVIEWER -> "Safety & moderation workspace"
                        UserRole.CONTENT_EDITOR -> "Content & alerts workspace"
                        UserRole.CASE_STAFF -> "Case work workspace"
                        else -> "Staff workspace"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Handle what needs attention first, then move directly to the tool you need. Destinations are limited to your authorised role.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (session.role == UserRole.SYSTEM_ADMIN) {
            item {
                RtcCard(protected = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        androidx.compose.material3.Icon(
                            if (needsLiveAdministratorMfa) Icons.Filled.Lock else Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (needsLiveAdministratorMfa) "Protected tools locked" else "Protected tools ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (needsLiveAdministratorMfa)
                                    "Verify your authenticator once before opening System Administration tools."
                                else
                                    "MFA is verified for this session. Server authorization and audit logging still apply.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (needsLiveAdministratorMfa) {
                            TextButton(onClick = { onOpenTool(RtcRoute.ADMIN_MFA) }) { Text("Verify") }
                        }
                    }
                }
            }
        }

        item {
            AdminNeedsAttentionCard(
                state = dashboardState,
                pendingApprovals = if (session.role == UserRole.SYSTEM_ADMIN) pendingApprovals.size else 0,
                onRefresh = dashboardViewModel::refreshCounts,
                onOpenHighPriority = { selectedQueueFilter = "Urgent" },
                onOpenOverdue = { selectedQueueFilter = "Overdue" },
                onOpenReview = { selectedQueueFilter = "Review" },
                onOpenApprovals = {
                    onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ACCESS_MANAGEMENT)
                },
            )
        }

        operationsUi.message?.let { message ->
            item {
                CommunityActionFeedback(
                    message = message,
                    isError = !operationsUi.isSuccess,
                    onDismiss = viewModel::dismissOperationsMessage,
                )
            }
        }

        draft?.let { savedDraft ->
            item {
                ContinueDraftCard(
                    draft = savedDraft,
                    onResume = {
                        onOpenTool(
                            if (savedDraft.area == DraftArea.STAFF_MODERATION) RtcRoute.MODERATION else RtcRoute.CONTENT,
                        )
                    },
                    onDiscard = { onDiscardDraft(savedDraft) },
                )
            }
        }

        if (pendingSyncCount > 0) {
            item {
                PendingSyncIndicator(
                    pendingSyncCount,
                    "Saved staff work remains on this device until the protected workflow can submit it.",
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Work queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Role-scoped operational work assigned or available to you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(enabled = !operationsUi.isWorking, onClick = viewModel::refreshOperationsHub) {
                    Text("Refresh")
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                listOf("All", "Urgent", "Mine", "Unassigned", "Review", "Overdue").forEach { filter ->
                    FilterChip(
                        selected = selectedQueueFilter == filter,
                        onClick = { selectedQueueFilter = filter },
                        label = { Text(filter) },
                    )
                }
            }
        }

        if (operationsUi.isWorking && workItems.isEmpty()) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        if (!operationsUi.isWorking && visibleWorkItems.isEmpty()) {
            item { RtcEmptyState("No work matches this view", "Choose another filter or refresh the queue.") }
        }
        items(visibleWorkItems, key = { it.id }) { workItem ->
            val eligibleAssignees = dashboardState.eligibleAssigneesByWorkItem[workItem.id].orEmpty()
            val assigneesLoading = dashboardState.assigneeLoadingWorkItemId == workItem.id
            val assigneesError = dashboardState.assigneeErrorByWorkItem[workItem.id]
            OperationsWorkItemCard(
                item = workItem,
                canReassign = session.role == UserRole.SYSTEM_ADMIN,
                onClaim = { viewModel.claimOperationsWorkItem(workItem.id) },
                onRelease = { reason -> viewModel.releaseOperationsWorkItem(workItem.id, reason) },
                onReadyForReview = { note -> viewModel.markOperationsWorkReadyForReview(workItem.id, note) },
                eligibleAssignees = eligibleAssignees,
                assigneesLoading = assigneesLoading,
                assigneesError = assigneesError,
                onLoadEligibleAssignees = { dashboardViewModel.loadEligibleAssignees(workItem.id) },
                onRetryEligibleAssignees = { dashboardViewModel.retryEligibleAssignees(workItem.id) },
                onClearEligibleAssignees = { dashboardViewModel.invalidateEligibleAssignees(workItem.id) },
                onReassign = { ownerId, reason -> viewModel.reassignOperationsWorkItem(workItem.id, ownerId, reason) },
                onOpen = {
                    onOpenTool(
                        when (workItem.sourceType) {
                            "MODERATION_REPORT" -> RtcRoute.MODERATION
                            "NOTICE_REVIEW" -> RtcRoute.CONTENT
                            "SUPPORT_CASE" -> RtcRoute.MY_WORK
                            "COMMUNITY_ALERT", "ALERT_DELIVERY_FAILURE" -> RtcRoute.STAFF_ALERTS
                            else -> RtcRoute.WORK_QUEUE
                        },
                    )
                },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text("Tools & work areas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Search by task instead of hunting through menus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = toolQuery,
                    onValueChange = { toolQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Find an admin tool") },
                    placeholder = { Text("Try ‘alerts’, ‘audit’, ‘reports’…") },
                )
            }
        }

        AdminWorkspaceArea.entries
            .filter { it != AdminWorkspaceArea.NEEDS_ATTENTION }
            .forEach { area ->
                val destinations = visibleDestinations.filter { it.area == area }
                if (destinations.isNotEmpty()) {
                    item {
                        Text(area.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(destinations, key = { it.id }) { destination ->
                        AdminDestinationRow(
                            destination = destination,
                            locked = destination.requiresMfa && needsLiveAdministratorMfa,
                            onClick = {
                                onOpenTool(resolveAdminDestinationRoute(destination, needsLiveAdministratorMfa))
                            },
                        )
                    }
                }
            }

        if (toolQuery.isNotBlank() && visibleDestinations.isEmpty()) {
            item { RtcEmptyState("No matching admin tool", "Try a broader task name or clear the search.") }
        }

        if (session.role.canUseAi) {
            item {
                AdminReferenceListRow(
                    "RTC AI assistant",
                    "Draft reviewable administrative proposals; protected actions still require human confirmation.",
                    Icons.Filled.Psychology,
                    onOpenAi,
                )
            }
        }
    }
}
