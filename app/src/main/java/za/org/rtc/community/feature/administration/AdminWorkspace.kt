package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    val visibleWorkItems = when (selectedQueueFilter) {
        "Urgent" -> workItems.filter { it.priority in setOf("URGENT", "HIGH") }
        "Mine" -> workItems.filter { it.assignedToMe }
        "Unassigned" -> workItems.filter { it.isUnassigned }
        else -> workItems
    }
    val needsLiveAdministratorMfa = session.role == UserRole.SYSTEM_ADMIN && session.authority == SessionAuthority.SUPABASE_AUTH && session.administratorMfaStatus != AdministratorMfaStatus.VERIFIED
    if (!session.role.isStaff) {
        PurposefulEmptyState("This workspace is available only to authorised staff.", "Return to Home", {})
        return
    }
    LaunchedEffect(session.id, session.role) {
        viewModel.refreshOperationsHub()
        if (session.role == UserRole.SYSTEM_ADMIN) viewModel.refreshAccessManagement()
    }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(if (session.role == UserRole.SYSTEM_ADMIN) "PROTECTED WORKSPACE" else "STAFF WORKSPACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(if (session.role == UserRole.SYSTEM_ADMIN) "Administrator" else "Work Queue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(if (session.role == UserRole.SYSTEM_ADMIN) "Protected administration is deliberate: confirmed actions, verified MFA where configured, and immutable audit records." else "Resume assigned work, use role-specific tools, or manage your own work preferences.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AdminPendingModerationSummary(
                dashboardState = dashboardState,
                onRefresh = dashboardViewModel::refreshCounts,
                onOpenReports = { onOpenTool(RtcRoute.MODERATION) },
                onOpenBusiness = { onOpenTool(RtcRoute.ADMIN_MARKETPLACE) },
                onOpenSupport = { onOpenTool(RtcRoute.MY_WORK) },
            )
        }
        if (session.role == UserRole.SYSTEM_ADMIN) {
            item { RtcCard(protected = true) { Text("Protected administration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (needsLiveAdministratorMfa) "Verify your authenticator before opening protected administration tools." else "Protected tools require explicit confirmation, server authorization, and immutable audit records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { AdminReferenceToolTile("Access Management", "Verified accounts, roles and approvals", Icons.Filled.AdminPanelSettings, Modifier.weight(1f)) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ACCESS_MANAGEMENT) }; AdminReferenceToolTile("Operational Controls", "Guarded production proposals", Icons.Filled.Settings, Modifier.weight(1f)) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.OPERATIONAL_CONTROLS) } } }
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { AdminReferenceToolTile("Privacy Analytics", "Aggregate metrics and audit trail", Icons.Filled.Visibility, Modifier.weight(1f)) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ANALYTICS_DASHBOARD) }; AdminReferenceToolTile("RTC AI", "Reviewable administrative proposals", Icons.Filled.Psychology, Modifier.weight(1f)) { onOpenAi() } } }
            item { Text("Pending Administrator approvals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { if (pendingApprovals.isEmpty()) RtcEmptyState("No pending approvals", "Administrator changes requiring a second decision will appear here.") else AdminReferenceListRow(title = "${pendingApprovals.size} Administrator approval${if (pendingApprovals.size == 1) "" else "s"} pending", detail = "Open Access Management to review the server-recorded requests.", icon = Icons.Filled.Shield) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ACCESS_MANAGEMENT) } }
        }
        operationsUi.message?.let { message -> item { CommunityActionFeedback(message = message, isError = !operationsUi.isSuccess, onDismiss = viewModel::dismissOperationsMessage) } }
        draft?.let { savedDraft -> item { ContinueDraftCard(draft = savedDraft, onResume = { onOpenTool(if (savedDraft.area == DraftArea.STAFF_MODERATION) RtcRoute.MODERATION else RtcRoute.CONTENT) }, onDiscard = { onDiscardDraft(savedDraft) }) } }
        if (pendingSyncCount > 0) item { PendingSyncIndicator(pendingSyncCount, "Saved local staff work will stay on this device until it can be submitted through the protected workflow.") }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { AdminWorkspaceMetricTile(workItems.count { it.assignedToMe }.toString(), "Assigned", Modifier.weight(1f)) { selectedQueueFilter = "Mine" }; AdminWorkspaceMetricTile(workItems.count { it.priority in setOf("URGENT", "HIGH") }.toString(), "High priority", Modifier.weight(1f)) { selectedQueueFilter = "Urgent" }; AdminWorkspaceMetricTile(workItems.count { it.isUnassigned }.toString(), "Unassigned", Modifier.weight(1f)) { selectedQueueFilter = "Unassigned" } } }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Assigned work", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); TextButton(enabled = !operationsUi.isWorking, onClick = viewModel::refreshOperationsHub) { Text("Refresh") } } }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf("All", "Urgent", "Mine", "Unassigned").forEach { filter -> FilterChip(selected = selectedQueueFilter == filter, onClick = { selectedQueueFilter = filter }, label = { Text(filter) }) } } }
        if (operationsUi.isWorking && workItems.isEmpty()) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (!operationsUi.isWorking && visibleWorkItems.isEmpty()) item { RtcEmptyState("No live work matches this filter.", "Change the filter or refresh the assigned-work queue.") }
        items(visibleWorkItems, key = { it.id }) { workItem -> OperationsWorkItemCard(item = workItem, canReassign = session.role == UserRole.SYSTEM_ADMIN, onClaim = { viewModel.claimOperationsWorkItem(workItem.id) }, onRelease = { reason -> viewModel.releaseOperationsWorkItem(workItem.id, reason) }, onReadyForReview = { note -> viewModel.markOperationsWorkReadyForReview(workItem.id, note) }, onReassign = { ownerId, reason -> viewModel.reassignOperationsWorkItem(workItem.id, ownerId, reason) }, onOpen = { onOpenTool(when (workItem.sourceType) { "MODERATION_REPORT" -> RtcRoute.MODERATION; "NOTICE_REVIEW" -> RtcRoute.CONTENT; "SUPPORT_CASE" -> RtcRoute.MY_WORK; "COMMUNITY_ALERT" -> RtcRoute.STAFF_ALERTS; else -> RtcRoute.WORK_QUEUE }) }) }
        item { Text("Role tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (session.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) { item { AdminReferenceListRow("Community Alerts", "Create resident alerts and inspect server-confirmed delivery follow-up.", Icons.Filled.Notifications) { onOpenTool(RtcRoute.STAFF_ALERTS) } }; item { AdminReferenceListRow("Content Management", "Draft, submit, review, publish, correct, or retire official notices.", Icons.Filled.Campaign) { onOpenTool(RtcRoute.CONTENT) } }; item { AdminReferenceListRow("Event Moderation", "Create, edit, publish, cancel or delete community calendar events.", Icons.Filled.Event) { onOpenTool(RtcRoute.ADMIN_EVENTS) } } }
        if (session.role in setOf(UserRole.MODERATOR, UserRole.SYSTEM_ADMIN)) {
            item { AdminReferenceListRow("Public Reports & Timeline Moderation", "Review community reports, publish official timeline updates, verify issues, and moderate comments.", Icons.Filled.Timeline) { onOpenTool(RtcRoute.PUBLIC_REPORTS_ADMIN) } }
            item { AdminReferenceListRow("Moderation dashboard", "Review reports, appeals, and safeguarded moderator decisions.", Icons.Filled.Shield) { onOpenTool(RtcRoute.MODERATION) } }
        }
        item { AdminReferenceListRow("My Work Profile", "Manage availability, work notifications, profile settings, and session sign-out.", Icons.Filled.Person) { onOpenTool(RtcRoute.MY_WORK) } }
        if (session.role == UserRole.SYSTEM_ADMIN && needsLiveAdministratorMfa) item { AdminReferenceListRow("Verify administrator MFA", "Open the protected authenticator verification screen before using System Administrator tools.", Icons.Filled.Shield) { onOpenTool(RtcRoute.ADMIN_MFA) } }
        item { AdminWorkspaceNavigation(role = session.role, requiresMfa = needsLiveAdministratorMfa, onNavigate = onOpenTool) }
    }
}
