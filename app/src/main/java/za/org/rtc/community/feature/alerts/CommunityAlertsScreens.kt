package za.org.rtc.community.feature.alerts

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.CommunityAlert
import za.org.rtc.community.core.CommunityAlertCategory
import za.org.rtc.community.core.CommunityAlertDashboardItem
import za.org.rtc.community.core.CommunityAlertState
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.ui.components.PurposefulEmptyState
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun CommunityAlertsScreen(alerts: List<CommunityAlert>, isRefreshing: Boolean, onRefresh: () -> Unit, onOpen: (CommunityAlert) -> Unit, onMarkAllRead: () -> Unit) {
    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Community alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Important updates from authorised RTC staff.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onMarkAllRead, enabled = alerts.any { it.unread }) { Text("Mark all read") }
                }
            }
            if (alerts.isEmpty()) item { PurposefulEmptyState("You are up to date.", "Explore Community", {}) }
            else items(alerts, key = { it.id }) { alert ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(alert) }) {
                    Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { if (alert.unread) Badge(); Text(alert.category.label, style = MaterialTheme.typography.labelLarge, color = if (alert.category.isSafety) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); if (alert.state != CommunityAlertState.ORIGINAL) Text(alert.state.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                        Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(alert.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Expires ${alert.expiresAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommunityAlertDetailScreen(alert: CommunityAlert?, onOpenLinkedNotice: (String) -> Unit) {
    if (alert == null) { PurposefulEmptyState("This Community alert is no longer available.", "Return to alerts", {}); return }
    LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text(alert.category.label, style = MaterialTheme.typography.labelLarge, color = if (alert.category.isSafety) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (alert.state != CommunityAlertState.ORIGINAL) Text(alert.state.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                Text(alert.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(alert.summary, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Text(alert.body, style = MaterialTheme.typography.bodyLarge)
                Text("This alert expires ${alert.expiresAt}.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                alert.linkedNoticeId?.let { noticeId -> OutlinedButton(onClick = { onOpenLinkedNotice(noticeId) }) { Text("Open linked community notice") } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StaffCommunityAlertsScreen(viewModel: RtcViewModel, alerts: List<CommunityAlertDashboardItem>) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (session.role !in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) { PurposefulEmptyState("Community Alerts is not available for this role.", "Return to Operations Hub", {}); return }
    var step by rememberSaveable { mutableStateOf(1) }
    var category by rememberSaveable { mutableStateOf(CommunityAlertCategory.COMMUNITY_UPDATE) }
    var title by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var scheduledAt by rememberSaveable { mutableStateOf("") }
    var expiryAt by rememberSaveable { mutableStateOf("") }
    var emergencyReason by rememberSaveable { mutableStateOf("") }
    var emergencyConfirmation by rememberSaveable { mutableStateOf("") }
    var publishConfirmation by rememberSaveable { mutableStateOf("") }
    var alertState by rememberSaveable { mutableStateOf(CommunityAlertState.ORIGINAL) }
    var originalAlertId by rememberSaveable { mutableStateOf("") }
    var correctionReason by rememberSaveable { mutableStateOf("") }
    val eligibleCategories = CommunityAlertCategory.entries.filter { !it.isSafety || session.role == UserRole.SYSTEM_ADMIN }
    fun clearWizard() { step = 1; title = ""; summary = ""; body = ""; scheduledAt = ""; expiryAt = ""; emergencyReason = ""; emergencyConfirmation = ""; publishConfirmation = ""; originalAlertId = ""; correctionReason = ""; alertState = CommunityAlertState.ORIGINAL }
    fun openSchedulePicker() {
        val now = LocalDateTime.now()
        DatePickerDialog(context, { _, year, month, day -> TimePickerDialog(context, { _, hour, minute -> scheduledAt = LocalDateTime.of(year, month + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toString() }, now.hour, now.minute, true).show() }, now.year, now.monthValue - 1, now.dayOfMonth).show()
    }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item { Text("Community Alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Guided resident alert creation. Delivery outcomes are aggregated; individual recipient status is never exposed.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("Create alert · Step $step of 3", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (step == 1) {
                        Text("1. Choose the authorised category and whether this is a new alert or a correction/retraction.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { eligibleCategories.forEach { option -> FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.label) }) } }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { CommunityAlertState.entries.forEach { option -> FilterChip(selected = alertState == option, onClick = { alertState = option }, label = { Text(option.label) }) } }
                        Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    } else if (step == 2) {
                        Text("2. Write the resident-facing message. Use clear, direct language and include what people should do next.")
                        if (alertState != CommunityAlertState.ORIGINAL) { Text("The original alert remains immutable. This alert will be labelled as a ${alertState.label.lowercase()}.", color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(originalAlertId, { originalAlertId = it }, label = { Text("Original alert ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(correctionReason, { correctionReason = it }, label = { Text("Reason for ${alertState.label.lowercase()}") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(summary, { summary = it }, label = { Text("Short resident summary") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(body, { body = it }, label = { Text("Full in-app detail") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f)) { Text("Back") }; Button(onClick = { step = 3 }, enabled = title.trim().length >= 3 && summary.trim().length >= 3 && body.trim().length >= 3 && (alertState == CommunityAlertState.ORIGINAL || (originalAlertId.isNotBlank() && correctionReason.trim().length >= 3)), modifier = Modifier.weight(1f)) { Text("Continue") } }
                    } else {
                        Text("3. Choose delivery timing and an expiry suggestion, then confirm the resident preview.")
                        Text("Resident preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(title, fontWeight = FontWeight.Bold); Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { OutlinedButton(onClick = { scheduledAt = "" }, modifier = Modifier.weight(1f)) { Text(if (scheduledAt.isBlank()) "Send now" else "Send now selected") }; OutlinedButton(onClick = ::openSchedulePicker, modifier = Modifier.weight(1f)) { Text(if (scheduledAt.isBlank()) "Schedule date & time" else "Scheduled ${scheduledAt.take(16).replace('T', ' ')}") } }
                        Text("Expiry suggestion", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { listOf(6 to "6 hours", 24 to "24 hours", 72 to "3 days", 168 to "7 days").forEach { option -> FilterChip(selected = expiryAt == Instant.now().plus(Duration.ofHours(option.first.toLong())).toString(), onClick = { expiryAt = Instant.now().plus(Duration.ofHours(option.first.toLong())).toString() }, label = { Text(option.second) }) } }
                        Text(if (expiryAt.isBlank()) "Choose an expiry suggestion to protect residents from stale updates." else "Expires ${expiryAt.take(16).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (category.isSafety) { Text("Safety/emergency alerts are restricted to System Administrators and bypass ordinary-alert quiet hours only when necessary.", color = MaterialTheme.colorScheme.error); OutlinedTextField(emergencyReason, { emergencyReason = it }, label = { Text("Operational reason") }, modifier = Modifier.fillMaxWidth(), minLines = 2); OutlinedTextField(emergencyConfirmation, { emergencyConfirmation = it }, label = { Text("Type SEND SAFETY ALERT") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        OutlinedTextField(publishConfirmation, { publishConfirmation = it }, label = { Text(if (category.isSafety) "Type SEND SAFETY ALERT" else "Type PUBLISH COMMUNITY ALERT") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                            OutlinedButton(onClick = { step = 2 }, modifier = Modifier.weight(1f)) { Text("Back") }
                            Button(onClick = { viewModel.createCommunityAlert(category, title, summary, body, scheduledAt.ifBlank { null }, expiryAt.ifBlank { null }, alertState, originalAlertId.ifBlank { null }, correctionReason.ifBlank { null }, publishConfirmation, emergencyConfirmation.ifBlank { null }, emergencyReason.ifBlank { null }); clearWizard() }, enabled = expiryAt.isNotBlank() && publishConfirmation == (if (category.isSafety) "SEND SAFETY ALERT" else "PUBLISH COMMUNITY ALERT") && (!category.isSafety || (emergencyConfirmation == "SEND SAFETY ALERT" && emergencyReason.trim().length >= 10)), modifier = Modifier.weight(1f)) { Text(if (scheduledAt.isBlank()) "Publish alert" else "Schedule alert") }
                        }
                    }
                }
            }
        }
        item { Text("My alert delivery results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (alerts.isEmpty()) item { Text("No alerts have been created from this account.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(alerts, key = { it.id }) { alert ->
            Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${alert.category.label} · ${alert.status} · ${alert.dispatchState}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Recipients ${alert.intendedRecipients} · Devices ${alert.eligibleDevices} · Accepted ${alert.fcmAccepted} · Failed ${alert.fcmFailed} · Read ${alert.readCount}", style = MaterialTheme.typography.bodySmall); Text("Expires ${alert.expiresAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (alert.status == "PUBLISHED") Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { TextButton(onClick = { alertState = CommunityAlertState.CORRECTION; originalAlertId = alert.id; correctionReason = ""; step = 2 }) { Text("Issue correction") }; TextButton(onClick = { alertState = CommunityAlertState.RETRACTION; originalAlertId = alert.id; correctionReason = ""; step = 2 }) { Text("Issue retraction") } } } }
        }
    }
}
