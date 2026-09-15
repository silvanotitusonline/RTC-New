package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun PublicReportsScreen(
    initialScope: PublicReportScope = PublicReportScope.VERIFIED,
    isAdmin: Boolean = false,
    onOpenReport: (String) -> Unit = {},
    onCompose: () -> Unit = {},
    onOpenAdminWorkspace: (() -> Unit)? = null,
    viewModel: PublicReportViewModel = hiltViewModel(),
) {
    val state = viewModel.feed.collectAsStateWithLifecycle().value
    val detailState = viewModel.detail.collectAsStateWithLifecycle().value
    var selectedReportIdForSheet by rememberSaveable { mutableStateOf<String?>(null) }

    // Admin Verification Queue Mode: 0 = Verified Public Feed, 1 = Pending Verification Queue
    var adminViewMode by rememberSaveable { mutableIntStateOf(0) }

    // Reject Dialog State
    var reportToRejectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRejectReason by rememberSaveable { mutableStateOf("Insufficient evidence or inaccurate location") }
    var customRejectReason by rememberSaveable { mutableStateOf("") }

    val unverifiedCount = state.reports.count { !it.verified }

    LaunchedEffect(initialScope, isAdmin) {
        viewModel.loadInitial(initialScope)
    }

    // Handle Admin Mode Switch
    fun handleAdminModeSwitch(mode: Int) {
        adminViewMode = mode
        if (mode == 0) {
            viewModel.setScope(PublicReportScope.VERIFIED)
        } else {
            viewModel.setScope(PublicReportScope.UNRESOLVED)
        }
    }

    if (selectedReportIdForSheet != null) {
        InteractiveReportDetailSheet(
            state = detailState,
            onDismiss = { selectedReportIdForSheet = null },
            onVote = viewModel::vote,
            onCommentDraftChange = viewModel::updateCommentDraft,
            onSubmitComment = viewModel::submitComment,
        )
    }

    // Rejection Dialog for Administrators
    if (reportToRejectId != null) {
        val rejectReasons = listOf(
            "Insufficient evidence or inaccurate location",
            "Duplicate report already under municipal review",
            "Non-civic / spam content",
            "Private property dispute / out of civic scope",
            "Other reason"
        )
        AlertDialog(
            onDismissRequest = { reportToRejectId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Reject Civic Report", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select the reason for rejecting this report. It will be removed from pending review and excluded from the public feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    rejectReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedRejectReason == reason) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else Color.Transparent)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedRejectReason == reason,
                                onClick = { selectedRejectReason = reason }
                            )
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (selectedRejectReason == "Other reason") {
                        OutlinedTextField(
                            value = customRejectReason,
                            onValueChange = { customRejectReason = it },
                            placeholder = { Text("Specify rejection reason...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reportId = reportToRejectId ?: return@Button
                        val finalReason = if (selectedRejectReason == "Other reason" && customRejectReason.isNotBlank()) {
                            customRejectReason.trim()
                        } else {
                            selectedRejectReason
                        }
                        viewModel.adminRejectReport(reportId, finalReason)
                        reportToRejectId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Rejection")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToRejectId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ResidentPullToRefresh(
        isRefreshing = state.refreshing || state.loading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.testTag("public_reports_pull_refresh"),
    ) {
        RtcScreenScaffold {
            item {
                RtcSectionHeader(
                    title = "Public Reports",
                    subtitle = if (isAdmin) "Review, verify, and moderate resident reports." else "Verified civic issues reported by residents.",
                )
            }

            // ADMIN VERIFICATION HUB HEADER
            if (isAdmin) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        "ADMIN VERIFICATION CONTROLS",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (onOpenAdminWorkspace != null) {
                                    TextButton(onClick = onOpenAdminWorkspace) {
                                        Text("Full Admin Console", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(
                                "As an authorized reviewer, you can verify reports to make them public or reject non-compliant submissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Feed Selector Tabs
                            TabRow(
                                selectedTabIndex = adminViewMode,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            ) {
                                Tab(
                                    selected = adminViewMode == 0,
                                    onClick = { handleAdminModeSwitch(0) },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("Verified Feed (Public)")
                                        }
                                    }
                                )
                                Tab(
                                    selected = adminViewMode == 1,
                                    onClick = { handleAdminModeSwitch(1) },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.PendingActions, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("Pending Review")
                                            if (unverifiedCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFD97706),
                                                    modifier = Modifier.padding(start = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "$unverifiedCount",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onCompose, modifier = Modifier.heightIn(min = 48.dp).weight(1f)) {
                        Text("Report an issue")
                    }
                    TextButton(onClick = viewModel::refresh, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Refresh")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.filters.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search reported issues by keyword or location…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                QuickFilterChipsRow(
                    selectedTag = state.filters.quickFilterTag,
                    onSelectTag = viewModel::selectQuickFilter,
                )
            }

            item { PublicReportFilterSheet(state = state, onEvent = viewModel) }

            item {
                Text(
                    text = state.filters.summary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Applied filters: ${state.filters.summary}" },
                )
            }

            if (state.loading) {
                item { Text("Loading Public Reports…") }
            }

            // Display filtered reports: in admin mode 1, prioritize unverified reports
            val displayedReports = if (isAdmin && adminViewMode == 1) {
                state.reports.filter { !it.verified }
            } else if (!isAdmin) {
                state.reports.filter { it.verified }
            } else {
                state.reports
            }

            if (!state.loading && displayedReports.isEmpty()) {
                item {
                    RtcCard {
                        Text(
                            text = if (isAdmin && adminViewMode == 1) "No pending reports requiring verification!" else "No ${state.filters.effectiveScope.label} Public Reports match these filters.",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isAdmin && adminViewMode == 1) "All submitted civic reports are currently reviewed." else "Try clearing filters or check again later.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = viewModel::clearFilters, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear filters") }
                    }
                }
            }

            items(displayedReports.size, key = { displayedReports[it].id }) { index ->
                val report = displayedReports[index]
                PublicReportCard(
                    report = report,
                    isAdmin = isAdmin,
                    onOpen = {
                        selectedReportIdForSheet = report.id
                        viewModel.openReport(report.id)
                        onOpenReport(report.id)
                    },
                    onVote = { direction -> viewModel.voteOnCard(report.id, direction) },
                    onAdminVerify = { verified ->
                        viewModel.adminVerifyReport(report.id, verified)
                    },
                    onAdminReject = {
                        reportToRejectId = report.id
                    }
                )
                if (index == displayedReports.lastIndex && !state.endReached) {
                    LaunchedEffect(report.id) { viewModel.loadNext() }
                }
            }

            if (state.endReached && displayedReports.isNotEmpty()) {
                item { Text("You have reached the end of this list.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            state.message?.let { message ->
                item {
                    Column {
                        Text(message, color = if (message.contains("verified") || message.contains("approved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh") }
                    }
                }
            }
        }
    }
}

