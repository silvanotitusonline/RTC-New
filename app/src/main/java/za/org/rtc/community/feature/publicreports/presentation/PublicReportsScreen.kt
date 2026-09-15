package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
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
    onOpenReport: (String) -> Unit = {},
    onCompose: () -> Unit = {},
    viewModel: PublicReportViewModel = hiltViewModel(),
) {
    val state = viewModel.feed.collectAsStateWithLifecycle().value
    val detailState = viewModel.detail.collectAsStateWithLifecycle().value
    var selectedReportIdForSheet by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialScope) { viewModel.loadInitial(initialScope) }

    if (selectedReportIdForSheet != null) {
        InteractiveReportDetailSheet(
            state = detailState,
            onDismiss = { selectedReportIdForSheet = null },
            onVote = viewModel::vote,
            onCommentDraftChange = viewModel::updateCommentDraft,
            onSubmitComment = viewModel::submitComment,
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
                    subtitle = "Verified civic issues reported by residents.",
                )
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
            if (!state.loading && state.reports.isEmpty()) {
                item {
                    RtcCard {
                        Text("No ${state.filters.effectiveScope.label} Public Reports match these filters.", fontWeight = FontWeight.SemiBold)
                        Text("Try clearing filters or check again later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = viewModel::clearFilters, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear filters") }
                    }
                }
            }
            items(state.reports.size, key = { state.reports[it].id }) { index ->
                val report = state.reports[index]
                PublicReportCard(
                    report = report,
                    onOpen = {
                        selectedReportIdForSheet = report.id
                        viewModel.openReport(report.id)
                        onOpenReport(report.id)
                    },
                    onVote = { direction -> viewModel.voteOnCard(report.id, direction) },
                )
                if (index == state.reports.lastIndex && !state.endReached) {
                    LaunchedEffect(report.id) { viewModel.loadNext() }
                }
            }
            if (state.endReached && state.reports.isNotEmpty()) {
                item { Text("You have reached the end of this list.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            state.message?.let { message ->
                item {
                    Column {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                    }
                }
            }
        }
    }
}
