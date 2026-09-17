package za.org.rtc.community.feature.administration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedQueueFilter by rememberSaveable { mutableStateOf("All") }

    val visibleWorkItems = when (selectedQueueFilter) {
        "Urgent" -> workItems.filter { it.priority in setOf("URGENT", "HIGH") }
        "Mine" -> workItems.filter { it.assignedToMe }
        "Unassigned" -> workItems.filter { it.isUnassigned }
        else -> workItems
    }

    val urgentWorkItems = workItems.filter { it.priority in setOf("URGENT", "HIGH") }
    val needsLiveAdministratorMfa = session.role == UserRole.SYSTEM_ADMIN &&
            session.authority == SessionAuthority.SUPABASE_AUTH &&
            session.administratorMfaStatus != AdministratorMfaStatus.VERIFIED

    if (!session.role.isStaff) {
        PurposefulEmptyState("This workspace is available only to authorised staff.", "Return to Home", { onOpenTool(RtcRoute.HOME) })
        return
    }

    LaunchedEffect(session.id, session.role) {
        viewModel.refreshOperationsHub()
        if (session.role == UserRole.SYSTEM_ADMIN) viewModel.refreshAccessManagement()
    }

    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        // --- 1. HERO HEADER CARD WITH ROLE STATUS & MFA BADGE ---
        item {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (session.role == UserRole.SYSTEM_ADMIN) Icons.Filled.Shield else Icons.Filled.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (session.role == UserRole.SYSTEM_ADMIN) "PROTECTED ADMIN WORKSPACE" else "STAFF WORKSPACE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = when (session.role) {
                                        UserRole.SYSTEM_ADMIN -> "System Administrator"
                                        UserRole.CONTENT_EDITOR -> "Content Editor"
                                        UserRole.MODERATOR -> "Community Moderator"
                                        else -> "Staff Member"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                                )
                            }
                        }

                        IconButton(onClick = {
                            viewModel.refreshOperationsHub()
                            dashboardViewModel.refreshCounts()
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Workspace Data")
                        }
                    }

                    // Security & MFA Status pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (needsLiveAdministratorMfa) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (needsLiveAdministratorMfa) Icons.Filled.Lock else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (needsLiveAdministratorMfa) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (needsLiveAdministratorMfa) "MFA Verification Required" else "Authenticated Session",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (needsLiveAdministratorMfa) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (pendingApprovals.isNotEmpty() && session.role == UserRole.SYSTEM_ADMIN) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "${pendingApprovals.size} Pending Approvals",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. FRONTEND APP INTEGRATION BAR ("View Live App") ---
        item {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Frontend App Integration", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Text("Preview Public UI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { onOpenTool(RtcRoute.HOME) },
                            label = { Text("Home Feed", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.Dashboard, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                        AssistChip(
                            onClick = { onOpenTool(RtcRoute.PUBLIC_REPORTS) },
                            label = { Text("Public Reports", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                        AssistChip(
                            onClick = { onOpenTool(RtcRoute.MARKETPLACE_HOME) },
                            label = { Text("Marketplace", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                        AssistChip(
                            onClick = { onOpenTool(RtcRoute.DAILY_POST_STUDIO) },
                            label = { Text("Daily Post Studio", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        // --- 3. SEGMENTED TAB NAVIGATION ROW ---
        item {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Overview", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        BadgedBox(
                            badge = {
                                if (workItems.count { it.priority in setOf("URGENT", "HIGH") } > 0) {
                                    Badge { Text("${workItems.count { it.priority in setOf("URGENT", "HIGH") }}") }
                                }
                            }
                        ) {
                            Text("Work Queue", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Moderation", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("Notices & Events", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                )
                if (session.role == UserRole.SYSTEM_ADMIN) {
                    Tab(
                        selected = selectedTabIndex == 4,
                        onClick = { selectedTabIndex = 4 },
                        text = { Text("System & Security", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                    )
                }
            }
        }

        // Operational Feedback & Sync Statuses
        operationsUi.message?.let { message ->
            item {
                CommunityActionFeedback(
                    message = message,
                    isError = !operationsUi.isSuccess,
                    onDismiss = viewModel::dismissOperationsMessage
                )
            }
        }
        draft?.let { savedDraft ->
            item {
                ContinueDraftCard(
                    draft = savedDraft,
                    onResume = { onOpenTool(if (savedDraft.area == DraftArea.STAFF_MODERATION) RtcRoute.MODERATION else RtcRoute.CONTENT) },
                    onDiscard = { onDiscardDraft(savedDraft) }
                )
            }
        }
        if (pendingSyncCount > 0) {
            item {
                PendingSyncIndicator(pendingSyncCount, "Saved local staff work will stay on this device until it can be submitted through the protected workflow.")
            }
        }

        // --- 4. TAB 0: OVERVIEW ---
        if (selectedTabIndex == 0) {
            item {
                AdminPendingModerationSummary(
                    dashboardState = dashboardState,
                    onRefresh = dashboardViewModel::refreshCounts,
                    onOpenReports = { onOpenTool(RtcRoute.MODERATION) },
                    onOpenNotices = { onOpenTool(RtcRoute.CONTENT) },
                    onOpenEvents = { onOpenTool(RtcRoute.EVENTS) },
                    onOpenWorkQueue = { onOpenTool(RtcRoute.WORK_QUEUE) },
                )
            }

            if (session.role == UserRole.SYSTEM_ADMIN) {
                item {
                    Text("Protected Administration Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        AdminReferenceToolTile(
                            title = "Access Management",
                            description = "Verified accounts, roles and approvals",
                            icon = Icons.Filled.AdminPanelSettings,
                            modifier = Modifier.weight(1f)
                        ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ACCESS_MANAGEMENT) }

                        AdminReferenceToolTile(
                            title = "Operational Controls",
                            description = "Guarded production proposals",
                            icon = Icons.Filled.Settings,
                            modifier = Modifier.weight(1f)
                        ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.OPERATIONAL_CONTROLS) }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        AdminReferenceToolTile(
                            title = "Privacy Analytics",
                            description = "Aggregate metrics and audit trail",
                            icon = Icons.Filled.Visibility,
                            modifier = Modifier.weight(1f)
                        ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ANALYTICS_DASHBOARD) }

                        AdminReferenceToolTile(
                            title = "RTC AI",
                            description = "Reviewable administrative proposals",
                            icon = Icons.Filled.Psychology,
                            modifier = Modifier.weight(1f)
                        ) { onOpenAi() }
                    }
                }
            }

            if (urgentWorkItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("High Priority Queue (${urgentWorkItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { selectedTabIndex = 1; selectedQueueFilter = "Urgent" }) {
                            Text("View All")
                        }
                    }
                }
                items(urgentWorkItems.take(3), key = { "urgent_${it.id}" }) { workItem ->
                    OperationsWorkItemCard(
                        item = workItem,
                        canReassign = session.role == UserRole.SYSTEM_ADMIN,
                        onClaim = { viewModel.claimOperationsWorkItem(workItem.id) },
                        onRelease = { reason -> viewModel.releaseOperationsWorkItem(workItem.id, reason) },
                        onReadyForReview = { note -> viewModel.markOperationsWorkReadyForReview(workItem.id, note) },
                        onReassign = { ownerId, reason -> viewModel.reassignOperationsWorkItem(workItem.id, ownerId, reason) },
                        onOpen = {
                            onOpenTool(
                                when (workItem.sourceType) {
                                    "MODERATION_REPORT" -> RtcRoute.MODERATION
                                    "NOTICE_REVIEW" -> RtcRoute.CONTENT
                                    "SUPPORT_CASE" -> RtcRoute.MY_WORK
                                    "COMMUNITY_ALERT" -> RtcRoute.STAFF_ALERTS
                                    else -> RtcRoute.WORK_QUEUE
                                }
                            )
                        }
                    )
                }
            }
        }

        // --- 5. TAB 1: WORK QUEUE ---
        if (selectedTabIndex == 1) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    AdminWorkspaceMetricTile(workItems.count { it.assignedToMe }.toString(), "Assigned", Modifier.weight(1f)) { selectedQueueFilter = "Mine" }
                    AdminWorkspaceMetricTile(workItems.count { it.priority in setOf("URGENT", "HIGH") }.toString(), "High priority", Modifier.weight(1f)) { selectedQueueFilter = "Urgent" }
                    AdminWorkspaceMetricTile(workItems.count { it.isUnassigned }.toString(), "Unassigned", Modifier.weight(1f)) { selectedQueueFilter = "Unassigned" }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Work Queue Items", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(enabled = !operationsUi.isWorking, onClick = viewModel::refreshOperationsHub) {
                        Text("Refresh Queue")
                    }
                }
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    listOf("All", "Urgent", "Mine", "Unassigned").forEach { filter ->
                        FilterChip(
                            selected = selectedQueueFilter == filter,
                            onClick = { selectedQueueFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
            }

            if (operationsUi.isWorking && workItems.isEmpty()) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (!operationsUi.isWorking && visibleWorkItems.isEmpty()) {
                item { RtcEmptyState("No live work matches this filter.", "Change the filter or refresh the assigned-work queue.") }
            }

            items(visibleWorkItems, key = { it.id }) { workItem ->
                OperationsWorkItemCard(
                    item = workItem,
                    canReassign = session.role == UserRole.SYSTEM_ADMIN,
                    onClaim = { viewModel.claimOperationsWorkItem(workItem.id) },
                    onRelease = { reason -> viewModel.releaseOperationsWorkItem(workItem.id, reason) },
                    onReadyForReview = { note -> viewModel.markOperationsWorkReadyForReview(workItem.id, note) },
                    onReassign = { ownerId, reason -> viewModel.reassignOperationsWorkItem(workItem.id, ownerId, reason) },
                    onOpen = {
                        onOpenTool(
                            when (workItem.sourceType) {
                                "MODERATION_REPORT" -> RtcRoute.MODERATION
                                "NOTICE_REVIEW" -> RtcRoute.CONTENT
                                "SUPPORT_CASE" -> RtcRoute.MY_WORK
                                "COMMUNITY_ALERT" -> RtcRoute.STAFF_ALERTS
                                else -> RtcRoute.WORK_QUEUE
                            }
                        )
                    }
                )
            }
        }

        // --- 6. TAB 2: MODERATION & REPORTS ---
        if (selectedTabIndex == 2) {
            item {
                Text("Community Moderation Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (session.role in setOf(UserRole.MODERATOR, UserRole.SYSTEM_ADMIN)) {
                item {
                    AdminReferenceListRow(
                        title = "Public Reports & Timeline Moderation",
                        detail = "Review public reports, publish official incident timeline updates, and moderate public comments.",
                        icon = Icons.Filled.Timeline
                    ) { onOpenTool(RtcRoute.PUBLIC_REPORTS_ADMIN) }
                }

                item {
                    AdminReferenceListRow(
                        title = "Moderation Dashboard",
                        detail = "Review flagged community posts, appeals, and safeguarded moderator audit logs.",
                        icon = Icons.Filled.Shield
                    ) { onOpenTool(RtcRoute.MODERATION) }
                }

                item {
                    AdminReferenceListRow(
                        title = "Marketplace Business Submissions",
                        detail = "Inspect, verify, or decline local business registrations and featured listings.",
                        icon = Icons.Filled.Storefront
                    ) { onOpenTool(RtcRoute.ADMIN_MARKETPLACE) }
                }
            } else {
                item {
                    RtcEmptyState("Moderator Access Required", "Your account role does not include moderation permissions.")
                }
            }
        }

        // --- 7. TAB 3: NOTICES & DAILY POSTS ---
        if (selectedTabIndex == 3) {
            item {
                Text("Editorial & Daily Post Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (session.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) {
                item {
                    AdminReferenceListRow(
                        title = "Daily Post Studio (Canva & Blog Templates)",
                        detail = "Compose, preview with Canva and blog templates, and publish official administrator articles to the resident Daily Post feed.",
                        icon = Icons.Filled.Assignment
                    ) { onOpenTool(RtcRoute.DAILY_POST_STUDIO) }
                }

                item {
                    AdminReferenceListRow(
                        title = "Community Resident Alerts",
                        detail = "Broadcast priority resident notifications and inspect delivery confirmation stats.",
                        icon = Icons.Filled.Notifications
                    ) { onOpenTool(RtcRoute.STAFF_ALERTS) }
                }

                item {
                    AdminReferenceListRow(
                        title = "Official Notices Management",
                        detail = "Draft, review, publish, correct, or archive official municipal/community notices.",
                        icon = Icons.Filled.Campaign
                    ) { onOpenTool(RtcRoute.CONTENT) }
                }
            } else {
                item {
                    RtcEmptyState("Content Editor Access Required", "Your account role does not include content editor permissions.")
                }
            }
        }

        // --- 8. TAB 4: SYSTEM & SECURITY (SYSTEM_ADMIN ONLY) ---
        if (selectedTabIndex == 4 && session.role == UserRole.SYSTEM_ADMIN) {
            item {
                Text("System Administration & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            item {
                AdminReferenceListRow(
                    title = "Access Management",
                    detail = "Manage user accounts, system roles, verified badges, and pending privilege elevation requests.",
                    icon = Icons.Filled.AdminPanelSettings
                ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ACCESS_MANAGEMENT) }
            }

            item {
                AdminReferenceListRow(
                    title = "Operational Controls",
                    detail = "Review guarded production proposals, database migrations, and emergency overrides.",
                    icon = Icons.Filled.Settings
                ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.OPERATIONAL_CONTROLS) }
            }

            item {
                AdminReferenceListRow(
                    title = "System Health & Metrics",
                    detail = "Inspect active server connections, real-time database health, and queue latency.",
                    icon = Icons.Filled.Analytics
                ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.SYSTEM_HEALTH) }
            }

            item {
                AdminReferenceListRow(
                    title = "Administrative Audit Activity Log",
                    detail = "Review immutable audit trail logs of all system administrator actions.",
                    icon = Icons.Filled.Visibility
                ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ADMIN_ACTIVITY) }
            }

            item {
                AdminReferenceListRow(
                    title = "Brand & Experience Customization",
                    detail = "Configure community branding, theme colors, default assets, and app metadata.",
                    icon = Icons.Filled.Smartphone
                ) { onOpenTool(if (needsLiveAdministratorMfa) RtcRoute.ADMIN_MFA else RtcRoute.ADMIN_BRANDING) }
            }

            if (needsLiveAdministratorMfa) {
                item {
                    AdminReferenceListRow(
                        title = "Verify Administrator MFA Authenticator",
                        detail = "Open the protected authenticator verification screen before modifying protected settings.",
                        icon = Icons.Filled.Lock
                    ) { onOpenTool(RtcRoute.ADMIN_MFA) }
                }
            }
        }

        // Bottom Navigation Quick Links
        item {
            AdminWorkspaceNavigation(role = session.role, requiresMfa = needsLiveAdministratorMfa, onNavigate = onOpenTool)
        }
    }
}
