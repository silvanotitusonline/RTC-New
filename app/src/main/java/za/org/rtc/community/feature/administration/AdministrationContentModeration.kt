package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.feature.community.relativeTimeLabel
import za.org.rtc.community.ui.components.PurposefulEmptyState
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun ContentManagementScreen(
    viewModel: RtcViewModel,
    onOpenAi: () -> Unit,
    draft: LocalDraft?,
    onSaveDraft: (String, String) -> Unit,
    onDiscardDraft: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notices by viewModel.editorialNotices.collectAsStateWithLifecycle()
    val operationsUi by viewModel.operationsUi.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf(draft?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(draft?.body.orEmpty()) }
    var category by rememberSaveable { mutableStateOf("Community Updates") }
    var reviewNote by rememberSaveable { mutableStateOf("") }
    if (session.role !in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) {
        PurposefulEmptyState("Content Management is not available for this role.", "Return to Work Queue", {})
        return
    }
    LaunchedEffect(Unit) { viewModel.refreshOperationsHub() }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item { RtcSectionHeader("Content & publication", "Draft → Submit → Different-editor review → Publish or schedule → Retire. Every decision is recorded.") }
        operationsUi.message?.let { message -> item { CommunityActionFeedback(message, !operationsUi.isSuccess, viewModel::dismissOperationsMessage) } }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("Create official notice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Resident-facing content") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Button(onClick = { viewModel.createEditorialNoticeDraft(title, body, category, false); title = ""; body = "" }, enabled = !operationsUi.isWorking && title.trim().length >= 3 && body.trim().length >= 3) { Text("Create draft") }
                        if (session.role.canUseAi) OutlinedButton(onClick = onOpenAi) { Text("Ask RTC AI") }
                    }
                }
            }
        }
        item { OutlinedTextField(reviewNote, { reviewNote = it }, label = { Text("Review / decision note") }, supportingText = { Text("Required before submitting a review decision or retirement.") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { Text("Live notices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (notices.isEmpty()) item { Text("No notices are currently available for your authorised editorial view.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(notices, key = { it.id }) { notice ->
            EditorialNoticeCard(notice, reviewNote, operationsUi.isWorking, { viewModel.submitEditorialNotice(notice.id, reviewNote.ifBlank { "Submitted for review." }) }, { viewModel.reviewEditorialNotice(notice.id, "APPROVE", reviewNote, "PUBLISH") }, { viewModel.reviewEditorialNotice(notice.id, "CHANGES_REQUESTED", reviewNote) }, { viewModel.reviewEditorialNotice(notice.id, "REJECT", reviewNote) }, { viewModel.retireEditorialNotice(notice.id, reviewNote) })
        }
    }
}

@Composable
private fun EditorialNoticeCard(
    notice: za.org.rtc.community.core.EditorialNoticeRecord,
    reviewNote: String,
    working: Boolean,
    onSubmit: () -> Unit,
    onApprove: () -> Unit,
    onChanges: () -> Unit,
    onReject: () -> Unit,
    onRetire: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text(notice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${notice.category} · ${notice.status.replace('_', ' ').replaceFirstChar(Char::titlecase)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(notice.body, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (notice.status) {
                "draft", "not_published" -> Button(enabled = !working, onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text("Submit for review") }
                "submitted", "under_review" -> {
                    Text("A different authorised editor must decide this notice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Button(enabled = !working && reviewNote.trim().length >= 3, onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Approve & publish") }
                        OutlinedButton(enabled = !working && reviewNote.trim().length >= 3, onClick = onChanges, modifier = Modifier.weight(1f)) { Text("Changes") }
                    }
                    TextButton(enabled = !working && reviewNote.trim().length >= 3, onClick = onReject) { Text("Reject") }
                }
                "published", "scheduled" -> TextButton(enabled = !working && reviewNote.trim().length >= 3, onClick = onRetire) { Text("Retire with note") }
            }
        }
    }
}

@Composable
internal fun CreateNoticeChoiceDialog(onDismiss: () -> Unit, onAskAi: () -> Unit, onSaveDraft: (String, String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create notice") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Text("Choose how to begin. Every route remains subject to editorial review, publication controls, and audit requirements."); FilledTonalButton(onClick = { onSaveDraft("Community update", "") }, modifier = Modifier.fillMaxWidth()) { Text("Use template") }; OutlinedButton(onClick = onAskAi, modifier = Modifier.fillMaxWidth()) { Text("Ask RTC AI for proposal") }; TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Start blank") } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ModerationDashboard(
    viewModel: RtcViewModel,
    onOpenAi: () -> Unit,
    draft: LocalDraft?,
    onDiscardDraft: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val reports by viewModel.moderationQueue.collectAsStateWithLifecycle()
    val appeals by viewModel.moderationAppeals.collectAsStateWithLifecycle()
    val activity by viewModel.moderationActivity.collectAsStateWithLifecycle()
    val operationsUi by viewModel.operationsUi.collectAsStateWithLifecycle()
    var decisionReason by rememberSaveable { mutableStateOf("") }
    if (session.role !in setOf(UserRole.MODERATOR, UserRole.SYSTEM_ADMIN)) {
        PurposefulEmptyState("Moderation is not available for this role.", "Return to Work Queue", {})
        return
    }
    LaunchedEffect(Unit) { viewModel.refreshOperationsHub() }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item { RtcSectionHeader("Community Safety", "Automated thresholds may prioritise review; a human moderator makes every final decision and preserves the appeal path.") }
        operationsUi.message?.let { message -> item { CommunityActionFeedback(message, !operationsUi.isSuccess, viewModel::dismissOperationsMessage) } }
        item { OutlinedTextField(decisionReason, { decisionReason = it }, label = { Text("Required moderation / appeal reason") }, supportingText = { Text("Explain the decision clearly for the author, appeal path, and immutable audit history.") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { if (session.role.canUseAi) OutlinedButton(onClick = onOpenAi, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Psychology, null); Spacer(Modifier.width(RtcSpacing.compact)); Text("Ask RTC AI") } }
        item { Text("Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (reports.isEmpty()) item { Text("No open reports are currently assigned to the live moderator queue.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(reports, key = { it.reportId }) { report -> ModerationReportCard(report, decisionReason.trim().length >= 3, operationsUi.isWorking, { viewModel.decideModerationReport(report.reportId, "DISMISS", decisionReason) }, { viewModel.decideModerationReport(report.reportId, "HIDE", decisionReason) }, { viewModel.decideModerationReport(report.reportId, "LOCK", decisionReason) }, { viewModel.decideModerationReport(report.reportId, "REMOVE", decisionReason) }) }
        item { Text("Appeals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (appeals.isEmpty()) item { Text("No open appeals are currently available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(appeals, key = { it.appealId }) { appeal ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("${appeal.subjectType.lowercase().replaceFirstChar(Char::titlecase)} appeal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    appeal.postBody?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    Text(appeal.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Button(enabled = !operationsUi.isWorking && decisionReason.trim().length >= 3, onClick = { viewModel.decideModerationAppeal(appeal.appealId, "RESTORE", decisionReason) }, modifier = Modifier.weight(1f)) { Text("Restore") }
                        OutlinedButton(enabled = !operationsUi.isWorking && decisionReason.trim().length >= 3, onClick = { viewModel.decideModerationAppeal(appeal.appealId, "UPHOLD", decisionReason) }, modifier = Modifier.weight(1f)) { Text("Uphold") }
                    }
                }
            }
        }
        item { Text("Community moderation audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (activity.isEmpty()) item { Text("No post lifecycle or moderation activity is available in the retained audit window.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(activity, key = { it.id }) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                    Text(event.eventType.replace('_', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${event.outcome ?: "RECORDED"} · ${event.actorEmail} · ${relativeTimeLabel(event.occurredAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    event.targetLabel?.let { Text("Target: $it", style = MaterialTheme.typography.bodySmall) }
                    event.details?.let { Text("Reason: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun ModerationReportCard(report: za.org.rtc.community.core.ModerationQueueItem, reasonReady: Boolean, working: Boolean, onDismiss: () -> Unit, onHide: () -> Unit, onLock: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text(report.reasonCode.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.labelLarge, color = if (report.isAutoLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(report.postBody, style = MaterialTheme.typography.bodyLarge)
            Text("Reported by a Community member: ${report.reportDetail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${report.reportCount} independent report(s) · ${report.postState.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { OutlinedButton(enabled = !working && reasonReady, onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }; OutlinedButton(enabled = !working && reasonReady, onClick = onLock, modifier = Modifier.weight(1f)) { Text("Lock") } }
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Button(enabled = !working && reasonReady, onClick = onHide, modifier = Modifier.weight(1f)) { Text("Hide") }; Button(enabled = !working && reasonReady, onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Remove permanently") } }
        }
    }
}

@Composable
internal fun ModerationQueueCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Open the Moderation workspace to act on assigned queue items.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
