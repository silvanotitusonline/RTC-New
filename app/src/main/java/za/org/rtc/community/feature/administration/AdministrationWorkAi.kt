package za.org.rtc.community.feature.administration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.Instant
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.AiProposal
import za.org.rtc.community.core.AssignedSupportCase
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.components.RtcProtectedAreaBanner
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing
import za.org.rtc.community.ui.theme.RtcStroke

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MyWorkProfileScreen(viewModel: RtcViewModel, onOpenAccount: () -> Unit) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val preferences by viewModel.staffWorkPreferences.collectAsStateWithLifecycle()
    val preferenceUi by viewModel.workPreferencesUi.collectAsStateWithLifecycle()
    val assignedSupportCases by viewModel.assignedSupportCases.collectAsStateWithLifecycle()
    val assignedSupportCaseUi by viewModel.assignedSupportCaseUi.collectAsStateWithLifecycle()
    var availability by rememberSaveable { mutableStateOf("available") }
    var queueOrderKey by rememberSaveable { mutableStateOf("priority,age") }
    var assignedNotifications by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(preferences) { availability = preferences.availabilityStatus; queueOrderKey = preferences.queueOrder.joinToString(",").ifBlank { "priority,age" }; assignedNotifications = preferences.assignedWorkNotifications }
    LaunchedEffect(Unit) { viewModel.refreshOperationsHub() }
    LaunchedEffect(session.authority, session.role) { if (session.authority == SessionAuthority.SUPABASE_AUTH && session.role == UserRole.CASE_STAFF) viewModel.refreshAssignedSupportCases() }
    val queueOrders = listOf("priority,age" to "Priority, then age", "age,priority" to "Oldest first", "due_date,priority" to "Due time, then priority")
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item {
            RtcCard {
                Text("STAFF PROFILE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(RtcSize.heroIcon * 2)) { Box(contentAlignment = Alignment.Center) { Text(session.displayName.take(1).ifBlank { "R" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(session.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${session.role.name.replace('_', ' ')} · ${preferences.availabilityStatus.replaceFirstChar(Char::titlecase)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { OutlinedButton(onClick = onOpenAccount) { Text("Account profile") }; TextButton(onClick = viewModel::signOutToPublicWelcome) { Icon(Icons.Filled.Logout, contentDescription = null); Spacer(Modifier.width(RtcSpacing.relatedText)); Text("Sign out") } }
            }
        }
        item { RtcSectionHeader("My Work Profile", "Manage your own availability, queue ordering, and assigned-work notifications without location or productivity tracking.") }
        if (session.role == UserRole.CASE_STAFF && session.authority == SessionAuthority.SUPABASE_AUTH) {
            item { RtcSectionHeader("Assigned support cases", "This view contains only cases currently assigned to you. Resident identity, attachments, message history, and assignment controls are not shown here.") }
            assignedSupportCaseUi.message?.let { message -> item { CommunityActionFeedback(message = message, isError = !assignedSupportCaseUi.isSuccess, onDismiss = viewModel::dismissAssignedSupportCaseMessage) } }
            if (assignedSupportCases.isEmpty() && !assignedSupportCaseUi.isWorking) item { RtcEmptyState("No assigned support cases", "Cases appear here only after a verified administrator assigns them to your Case Staff account.") }
            items(assignedSupportCases, key = { it.id }) { assignedCase -> AssignedSupportCaseCard(assignedCase, assignedSupportCaseUi.isWorking, viewModel::updateAssignedSupportCaseState) }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    Text("Availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf("available" to "Available", "away" to "Away", "busy" to "Off duty").forEach { option -> FilterChip(selected = availability == option.first, onClick = { availability = option.first }, label = { Text(option.second) }) } }
                    Text("Availability is self-managed and informs your work view only; it does not collect location or performance data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Saved queue order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    queueOrders.forEach { option -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = queueOrderKey == option.first, onClick = { queueOrderKey = option.first }); Text(option.second, modifier = Modifier.clickable { queueOrderKey = option.first }) } }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("Assigned-work notifications", fontWeight = FontWeight.SemiBold); Text("Receive operational notifications for items assigned to you. Safety and expiry-critical notifications remain protected by platform rules.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = assignedNotifications, onCheckedChange = { assignedNotifications = it }) }
                    preferenceUi.message?.let { message -> CommunityActionFeedback(message, isError = !preferenceUi.isSuccess, onDismiss = viewModel::dismissWorkPreferencesMessage) }
                    Button(onClick = { viewModel.saveStaffWorkPreferences(queueOrderKey.split(',').filter { it.isNotBlank() }, assignedNotifications, availability) }, enabled = !preferenceUi.isWorking, modifier = Modifier.fillMaxWidth()) { Text(if (preferenceUi.isWorking) "Saving…" else "Save work profile") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignedSupportCaseCard(assignedCase: AssignedSupportCase, isWorking: Boolean, onUpdate: (String, String, String) -> Unit) {
    var updateOpen by rememberSaveable(assignedCase.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(assignedCase.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("Priority ${assignedCase.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            Text("${assignedCase.category} · ${assignedCase.state.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            assignedCase.locationLabel?.let { location -> Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Updated ${assignedCaseUpdatedLabel(assignedCase.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { updateOpen = true }, enabled = !isWorking, modifier = Modifier.align(Alignment.End)) { Text("Update assigned case") }
        }
    }
    if (updateOpen) AssignedSupportCaseUpdateDialog(assignedCase.state, isWorking, { state, note -> onUpdate(assignedCase.id, state, note); updateOpen = false }, { if (!isWorking) updateOpen = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignedSupportCaseUpdateDialog(currentState: String, isWorking: Boolean, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    val allowedStates = listOf("IN_REVIEW", "IN_PROGRESS", "RESOLVED", "CLOSED")
    var selectedState by rememberSaveable { mutableStateOf(currentState.takeIf { it in allowedStates } ?: "IN_REVIEW") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Update assigned case") }, text = { Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Text("Choose a Case Staff state and add a 3–1000 character audit note. The server verifies your assignment before recording the update."); FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { allowedStates.forEach { state -> FilterChip(selected = selectedState == state, onClick = { selectedState = state }, enabled = !isWorking, label = { Text(state.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)) }) } }; OutlinedTextField(value = note, onValueChange = { note = it.take(1000) }, label = { Text("Case update note") }, supportingText = { Text("${note.trim().length}/1000 · minimum 3 characters") }, modifier = Modifier.fillMaxWidth(), enabled = !isWorking, minLines = 3) } }, confirmButton = { Button(onClick = { onConfirm(selectedState, note.trim()) }, enabled = !isWorking && note.trim().length in 3..1000) { Text(if (isWorking) "Updating…" else "Save update") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") } })
}

private fun assignedCaseUpdatedLabel(updatedAt: String): String = runCatching {
    val minutes = Duration.between(Instant.parse(updatedAt), Instant.now()).toMinutes().coerceAtLeast(0)
    when { minutes < 1 -> "just now"; minutes < 60 -> "$minutes min ago"; minutes < 1_440 -> "${minutes / 60} hr ago"; else -> "${minutes / 1_440} day${if (minutes / 1_440 == 1L) "" else "s"} ago" }
}.getOrDefault("recently")

@Composable
internal fun AiAssistantScreen(viewModel: RtcViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val proposals by viewModel.aiProposals.collectAsStateWithLifecycle()
    val aiUi by viewModel.aiUi.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    if (!session.role.canUseAi) { za.org.rtc.community.ui.components.PurposefulEmptyState("RTC AI is available only to authorised editorial, moderation, and system administration roles.", "Return to Admin", {}); return }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item { RtcProtectedAreaBanner("RTC AI is a guarded proposal service. It cannot publish, delete, assign roles, or apply production configuration directly.") }
        item { RtcCard(protected = true) { RtcStatusChip("Protected AI workflow", RtcStatusTone.PROTECTED); Text("Safe, role-scoped assistance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Every request follows Proposal → Review → Confirm → Transaction → Audit."); Text("A generated proposal is not a completed change. Confirmation creates only the server-authorised draft transaction permitted for your role.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            RtcSectionHeader("Suggested safe prompts")
            val examples = when (session.role) { UserRole.CONTENT_EDITOR -> listOf("Draft a service interruption notice from approved public information", "Suggest an accessible title and summary"); UserRole.MODERATOR -> listOf("Summarise the selected public moderation context", "Prepare a review recommendation without taking action"); else -> listOf("Summarise current editorial workload", "Prepare a maintenance-banner draft proposal") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { examples.forEach { example -> AssistChip(onClick = { prompt = example }, label = { Text(example) }) } }
        }
        item {
            RtcCard {
                OutlinedTextField(value = prompt, onValueChange = { prompt = it.take(12_000) }, label = { Text("Describe the proposal you need") }, modifier = Modifier.fillMaxWidth(), enabled = !aiUi.isWorking, minLines = 4, supportingText = { Text("${prompt.length}/12000 · A fresh human-verification token is required for each request.") })
                Button(onClick = { viewModel.proposeAiAction(prompt); prompt = "" }, enabled = prompt.isNotBlank() && !aiUi.isWorking, modifier = Modifier.fillMaxWidth()) { if (aiUi.isWorking) CircularProgressIndicator(modifier = Modifier.size(RtcSize.actionIcon), strokeWidth = RtcStroke.emphasis) else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null); Spacer(Modifier.width(RtcSpacing.compact)); Text(if (aiUi.isWorking) "Verifying and preparing…" else "Prepare proposal") }
            }
        }
        aiUi.message?.let { message -> item { CommunityActionFeedback(message = message, isError = !aiUi.isSuccess, onDismiss = viewModel::dismissAiMessage) } }
        item { RtcSectionHeader("Recent proposals", "Review the server-generated scope before confirming.") }
        if (proposals.isEmpty()) item { RtcEmptyState("No proposals yet", "RTC AI will prepare a reviewable proposal; it will not execute an action automatically.") }
        items(proposals, key = { it.id }) { proposal -> AiProposalCard(proposal, aiUi.isWorking, { viewModel.discardAiProposal(proposal.id) }, { viewModel.confirmAiProposal(proposal.id) }) }
    }
}

@Composable
private fun AiProposalCard(proposal: AiProposal, working: Boolean, onDiscard: () -> Unit, onConfirm: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Visibility, null); Spacer(Modifier.width(RtcSpacing.compact)); Text(proposal.status, fontWeight = FontWeight.Bold) }; Text(proposal.summary); Text("Affected records", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold); proposal.affectedRecords.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (proposal.status == "Awaiting review") { Text("Review the affected records and summary before confirmation. Confirmation remains a separate audited transaction in production.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { OutlinedButton(onClick = onDiscard, enabled = !working, modifier = Modifier.weight(1f)) { Text("Discard") }; Button(onClick = onConfirm, enabled = !working, modifier = Modifier.weight(1f)) { Text("Confirm audited draft") } } } } }
}
