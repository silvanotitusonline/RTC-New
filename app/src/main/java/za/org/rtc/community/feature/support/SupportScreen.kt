package za.org.rtc.community.feature.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.CaseStage
import kotlinx.coroutines.launch
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.feature.community.UnsavedWorkDialog
import za.org.rtc.community.feature.community.relativeTimeLabel
import za.org.rtc.community.feature.home.ContinueDraftCard
import za.org.rtc.community.ui.components.*
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun SupportScreen(
    cases: List<SupportCase>,
    draft: LocalDraft?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onSaveDraft: (String, String) -> Unit,
    onDiscardDraft: () -> Unit,
    onOpenCase: (SupportCase) -> Unit = {},
    onOpenCentres: () -> Unit,
) {
    val activeCase = cases.firstOrNull { it.stage != CaseStage.RESOLVED }
    var view by rememberSaveable { mutableStateOf(if (activeCase == null) "Choice" else "Active case") }
    var requestOpen by rememberSaveable { mutableStateOf(false) }
    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item { EmergencyNotice() }
            item {
                RtcSectionHeader(
                    title = if (view == "Choice") "How can we help?" else view,
                    subtitle = "Check active requests, start a new non-emergency request, or find the right service.",
                )
            }
            draft?.let { savedDraft -> item { ContinueDraftCard(draft = savedDraft, onResume = { requestOpen = true }, onDiscard = onDiscardDraft) } }
            if (view == "Active case" && activeCase != null) {
                item { SupportCaseCard(activeCase, onClick = { onOpenCase(activeCase) }) }
                item { SupportChoice("View all my cases", "See updates and next steps for every open or resolved request.", Icons.Filled.Description) { view = "My Cases" } }
                item { SupportChoice("Start another request", "Create a separate request for a different issue.", Icons.Filled.Edit) { requestOpen = true } }
                item { SupportChoice("Find a centre", "Open published support locations and service information.", Icons.Filled.LocationOn, onOpenCentres) }
            } else if (view == "Choice") {
                item { SupportChoice("My Cases", "Check plain-language status and next steps.", Icons.Filled.Description) { view = "My Cases" } }
                item { SupportChoice("Start a request", "Draft and submit a non-emergency support request when you are ready.", Icons.Filled.Edit) { requestOpen = true } }
                item { SupportChoice("Find a centre", "Open published support locations and service information.", Icons.Filled.LocationOn, onOpenCentres) }
            } else if (view == "My Cases") {
                if (cases.isEmpty()) item { RtcEmptyState("No support cases yet", "When you submit a non-emergency request, its progress will appear here.", "Start a request") { requestOpen = true } }
                items(cases, key = { it.id }) { item -> SupportCaseCard(item, onClick = { onOpenCase(item) }) }
                item { TextButton(onClick = { view = if (activeCase == null) "Choice" else "Active case" }, modifier = Modifier.fillMaxWidth()) { Text("Return to Support") } }
            }
        }
    }
    if (requestOpen) {
        SupportRequestSheet(
            draft = draft,
            onDismiss = { requestOpen = false },
            onSubmit = { title, detail -> onSubmit(title, detail); requestOpen = false },
            onSaveDraft = { title, detail -> onSaveDraft(title, detail); requestOpen = false },
            onDiscardDraft = { onDiscardDraft(); requestOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportRequestSheet(
    draft: LocalDraft?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onSaveDraft: (String, String) -> Unit,
    onDiscardDraft: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(draft?.title.orEmpty()) }
    var detail by rememberSaveable { mutableStateOf(draft?.body.orEmpty()) }
    var leaveConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun hideAnd(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    fun requestClose() {
        if (title.isNotBlank() || detail.isNotBlank()) leaveConfirmationOpen = true else hideAnd(onDismiss)
    }
    ModalBottomSheet(onDismissRequest = ::requestClose, sheetState = sheetState) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Start a support request", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("This is not an emergency service. Do not include sensitive information that is not needed to understand your request.")
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What do you need help with?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Describe the issue") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                OutlinedButton(onClick = ::requestClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { hideAnd { onSubmit(title, detail) } }, enabled = title.isNotBlank() && detail.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Submit request") }
            }
        }
    }
    if (leaveConfirmationOpen) {
        UnsavedWorkDialog(
            onKeepEditing = { leaveConfirmationOpen = false },
            onSaveDraft = { onSaveDraft(title, detail); leaveConfirmationOpen = false; hideAnd(onDismiss) },
            onDiscardChanges = { onDiscardDraft(); leaveConfirmationOpen = false; hideAnd(onDismiss) }
        )
    }
}

@Composable
private fun SupportChoice(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    RtcQuickActionTile(title, description, icon, onClick)
}

@Composable
internal fun SupportCaseDetailScreen(viewModel: RtcViewModel, supportCase: SupportCase?) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val messages by viewModel.supportCaseMessages.collectAsStateWithLifecycle()
    val supportUi by viewModel.supportUi.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    RtcScreenScaffold {
        item {
            if (supportCase == null) {
                RtcEmptyState("Case unavailable", "This support case could not be loaded. Return to Support and refresh your cases.")
            } else {
                RtcSectionHeader(supportCase.title, supportCase.category.replace('_', ' '))
            }
        }
        supportCase?.let { case ->
            item {
                RtcCard {
                    RtcCaseProgress(case.stage)
                    Text(case.stage.nextStep, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    case.locationLabel?.let { Text("Location: $it", style = MaterialTheme.typography.bodySmall) }
                    Text("Priority ${case.priority}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            supportUi.message?.let { text -> item { CommunityActionFeedback(text, !supportUi.isSuccess, viewModel::dismissSupportMessage) } }
            item { RtcSectionHeader("Case conversation", "Messages are visible only to authorised case participants.") }
            if (messages.isEmpty()) item { RtcEmptyState("No messages yet", "Updates from you and assigned Case Staff will appear here.") }
            items(messages, key = { it.id }) { item ->
                RtcCard {
                    Text(if (item.authorId == session.id) "You" else "RTC case team", fontWeight = FontWeight.SemiBold)
                    Text(item.body)
                    Text(relativeTimeLabel(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RtcCard {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(10000) },
                        label = { Text("Add a case message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Button(
                        onClick = { val body = message.trim(); if (body.isNotBlank()) { viewModel.addSupportCaseMessage(case.id, body); message = "" } },
                        enabled = message.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Send message") }
                }
            }
        }
    }
}
