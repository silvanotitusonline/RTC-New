package za.org.rtc.community.feature.administration

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.ZoneId
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcProtectedAreaBanner
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun SystemHealthScreen(viewModel: RtcViewModel) {
    val health by viewModel.systemHealth.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshOperationsHub() }
    RtcScreenScaffold(density = RtcContentDensity.ANALYTICAL_DENSE) {
        item { Text("System Health & Delivery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Live, privacy-safe operational health indicators. Individual resident delivery data is not shown.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (health.isEmpty()) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        items(health, key = { it.serviceKey }) { item ->
            Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(item.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(item.status, color = if (item.status == "RED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.affectedCount > 0) Text("Affected aggregate count: ${item.affectedCount}", style = MaterialTheme.typography.labelSmall) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminActivityScreen(viewModel: RtcViewModel) {
    val activity by viewModel.administrativeActivity.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(category) { viewModel.refreshAdministrativeActivity(category) }
    RtcScreenScaffold(density = RtcContentDensity.ANALYTICAL_DENSE) {
        item { Text("Administrative Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Read-only, paginated server activity. Exports and individual resident analytics are not available from this view.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf<String?>(null, "ACCESS", "MODERATION", "EDITORIAL", "OPERATIONS").forEach { option -> FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option ?: "All") }) } } }
        if (activity.isEmpty()) item { Text("No events match the selected safe filter.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(activity, key = { it.id }) { event -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(event.eventType.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase), fontWeight = FontWeight.SemiBold); Text("${event.category} · ${event.actorEmail} · ${event.occurredAt.take(16).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); event.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OperationalControlsScreen(viewModel: RtcViewModel, onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val controls by viewModel.operationsControls.collectAsStateWithLifecycle()
    val incidents by viewModel.operationalIncidents.collectAsStateWithLifecycle()
    val health by viewModel.systemHealth.collectAsStateWithLifecycle()
    val activity by viewModel.administrativeActivity.collectAsStateWithLifecycle()
    val operationsUi by viewModel.operationsUi.collectAsStateWithLifecycle()
    var controlType by rememberSaveable { mutableStateOf("MAINTENANCE_BANNER") }
    var reason by rememberSaveable { mutableStateOf("") }
    var displayMessage by rememberSaveable { mutableStateOf("") }
    var expiryAt by rememberSaveable { mutableStateOf("") }
    var auditNote by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var incidentTitle by rememberSaveable { mutableStateOf("") }
    var incidentImpact by rememberSaveable { mutableStateOf("") }
    var incidentSeverity by rememberSaveable { mutableStateOf("MODERATE") }
    var incidentUpdateId by rememberSaveable { mutableStateOf<String?>(null) }
    var incidentUpdateState by rememberSaveable { mutableStateOf("MITIGATING") }
    fun openOperationalExpiryPicker() {
        val now = LocalDateTime.now().plusMinutes(15)
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                expiryAt = LocalDateTime.of(year, month + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toString()
            }, now.hour, now.minute, true).show()
        }, now.year, now.monthValue - 1, now.dayOfMonth).show()
    }
    LaunchedEffect(Unit) { viewModel.refreshOperationsHub() }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item { RtcSectionHeader("System Control Centre", "MFA-gated operational controls with deliberate impact review and immutable audit results.") }
        item { RtcProtectedAreaBanner("Protected administration. Consequential controls require action context, impact preview, reason, typed confirmation, expiry where applicable, and a server audit result.") }
        operationsUi.message?.let { message -> item { CommunityActionFeedback(message, !operationsUi.isSuccess, viewModel::dismissOperationsMessage) } }
        item { Text("System Health & Delivery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (health.isEmpty()) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        items(health, key = { it.serviceKey }) { item -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(item.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(item.status, style = MaterialTheme.typography.labelLarge, color = when (item.status) { "RED" -> MaterialTheme.colorScheme.error; "AMBER" -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }); Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.affectedCount > 0) Text("Affected aggregate count: ${item.affectedCount}", style = MaterialTheme.typography.labelSmall) } } }
        item { Text("Protected control", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf("MAINTENANCE_BANNER" to "Maintenance banner", "COMMUNITY_PAUSE" to "Pause Community").forEach { option -> FilterChip(selected = controlType == option.first, onClick = { controlType = option.first }, label = { Text(option.second) }) } }
                    Text(if (controlType == "MAINTENANCE_BANNER") "A resident-facing banner is visible until the scheduled expiry." else "New Community posts and comments are blocked at the server until the scheduled expiry.", color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedTextField(reason, { reason = it }, label = { Text("Operational reason") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    if (controlType == "MAINTENANCE_BANNER") OutlinedTextField(displayMessage, { displayMessage = it }, label = { Text("Resident-facing message") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    RtcCard(protected = true) { Text("Impact preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(if (controlType == "MAINTENANCE_BANNER") "Residents will see a maintenance banner until the selected expiry. Existing content remains available." else "New Community posts and comments will be blocked at the server until the selected expiry. Existing published content remains readable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onClick = ::openOperationalExpiryPicker, modifier = Modifier.fillMaxWidth()) { Text(if (expiryAt.isBlank()) "Choose expiry date and time" else "Expires ${expiryAt.take(16).replace('T', ' ')} · ${ZoneId.systemDefault().id}") }
                    Text("Expiry is converted to UTC before the protected server transaction and must be within 14 days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(auditNote, { auditNote = it }, label = { Text("Required audit note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type CONFIRM OPERATIONAL CONTROL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { viewModel.setOperationalControl(controlType, true, reason, displayMessage, expiryAt, auditNote, confirmation) }, enabled = !operationsUi.isWorking && reason.trim().length >= 3 && auditNote.trim().length >= 3 && expiryAt.isNotBlank() && confirmation == "CONFIRM OPERATIONAL CONTROL" && (controlType != "MAINTENANCE_BANNER" || displayMessage.trim().length >= 3), modifier = Modifier.fillMaxWidth()) { Text("Activate protected control") }
                }
            }
        }
        if (controls.maintenanceMessage != null || controls.communityPaused) item { Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text("Currently active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); controls.maintenanceMessage?.let { Text("Maintenance banner: $it") }; if (controls.communityPaused) Text("Community posting and commenting are paused."); Text("Controls expire automatically and cannot be extended without creating a new audited control.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Text("Incident management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("Open incident", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Incidents are linked to System Administrator work and require a closing summary when resolved or cancelled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(incidentTitle, { incidentTitle = it }, label = { Text("Incident title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(incidentImpact, { incidentImpact = it }, label = { Text("Impact summary") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf("LOW", "MODERATE", "HIGH", "CRITICAL").forEach { severity -> FilterChip(selected = incidentSeverity == severity, onClick = { incidentSeverity = severity }, label = { Text(severity.lowercase().replaceFirstChar(Char::titlecase)) }) } }
                    Button(onClick = { viewModel.createOperationalIncident(incidentTitle, incidentImpact, incidentSeverity); incidentTitle = ""; incidentImpact = "" }, enabled = !operationsUi.isWorking && incidentTitle.trim().length >= 3 && incidentImpact.trim().length >= 3, modifier = Modifier.fillMaxWidth()) { Text("Open incident") }
                }
            }
        }
        if (incidents.isEmpty()) item { Text("No operational incidents are currently recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(incidents, key = { it.id }) { incident -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(incident.severity.lowercase().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }; Text("${incident.state.lowercase().replaceFirstChar(Char::titlecase)} · opened ${incident.openedAt.take(16).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(incident.impactSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); incident.closingSummary?.let { Text("Closing summary: $it", style = MaterialTheme.typography.bodySmall) }; if (incident.state in setOf("OPEN", "MITIGATING")) OutlinedButton(onClick = { incidentUpdateId = incident.id; incidentUpdateState = if (incident.state == "OPEN") "MITIGATING" else "RESOLVED" }) { Text("Update incident") } } } }
        item { Text("Administrative Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(activity.take(20), key = { it.id }) { event -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(event.eventType.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase), fontWeight = FontWeight.SemiBold); Text("${event.category} · ${event.actorEmail} · ${event.occurredAt.take(16).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); event.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } } }
        item { OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Exit System Control Centre") } }
    }
    incidentUpdateId?.let { incidentId -> IncidentUpdateDialog(initialState = incidentUpdateState, onConfirm = { state, closingSummary -> viewModel.updateOperationalIncident(incidentId, state, closingSummary); incidentUpdateId = null }, onDismiss = { incidentUpdateId = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncidentUpdateDialog(initialState: String, onConfirm: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var state by rememberSaveable(initialState) { mutableStateOf(initialState) }
    var closingSummary by rememberSaveable { mutableStateOf("") }
    val closingRequired = state in setOf("RESOLVED", "CANCELLED")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Update incident") }, text = { Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf("MITIGATING", "RESOLVED", "CANCELLED").forEach { option -> FilterChip(selected = state == option, onClick = { state = option }, label = { Text(option.lowercase().replaceFirstChar(Char::titlecase)) }) } }; if (closingRequired) { Text("A closing summary is required to preserve the audit record.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(closingSummary, { closingSummary = it }, label = { Text("Closing summary") }, modifier = Modifier.fillMaxWidth(), minLines = 3) } } }, confirmButton = { Button(onClick = { onConfirm(state, closingSummary.takeIf { it.isNotBlank() }) }, enabled = !closingRequired || closingSummary.trim().length >= 3) { Text("Save update") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
