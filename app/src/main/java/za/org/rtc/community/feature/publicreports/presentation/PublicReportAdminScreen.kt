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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.publicreports.domain.PublicReportAdminRow
import za.org.rtc.community.feature.publicreports.domain.PublicReportComment
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportPrivateDetails
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.feature.publicreports.domain.PublicReportTimelineEntry
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

data class PublicReportAdminState(
    val rows: List<PublicReportAdminRow> = emptyList(),
    val selected: PublicReportAdminRow? = null,
    val privateDetails: PublicReportPrivateDetails? = null,
    val timelineEntries: List<PublicReportTimelineEntry> = emptyList(),
    val comments: List<PublicReportComment> = emptyList(),
    val dashboard: PublicReportDashboard? = null,
    val reason: String = "",
    val publicNote: String = "",
    val duplicateOf: String = "",
    val customTimelineNote: String = "",
    val selectedFilter: String = "All",
    val loading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PublicReportAdminViewModel @Inject constructor(
    private val repository: PublicReportRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PublicReportAdminState())
    val state = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            repository.dashboard().onSuccess { dash -> _state.update { it.copy(dashboard = dash) } }
            repository.adminPage(null, null, null, null)
                .onSuccess { rows -> _state.update { it.copy(rows = rows, loading = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, message = SafeUiError.generic(error, error.message ?: "Administrator reports could not be loaded."))
                    }
                }
        }
    }

    fun selectFilter(filter: String) {
        _state.update { it.copy(selectedFilter = filter) }
    }

    fun select(row: PublicReportAdminRow) {
        _state.update { it.copy(selected = row, privateDetails = null, timelineEntries = emptyList(), comments = emptyList()) }
        loadSelectedDetails(row.id)
    }

    private fun loadSelectedDetails(reportId: String) {
        viewModelScope.launch {
            repository.timeline(reportId).onSuccess { timeline ->
                _state.update { it.copy(timelineEntries = timeline) }
            }
            repository.comments(reportId).onSuccess { commentsList ->
                _state.update { it.copy(comments = commentsList) }
            }
        }
    }

    fun updateReason(value: String) = _state.update { it.copy(reason = value) }
    fun updatePublicNote(value: String) = _state.update { it.copy(publicNote = value) }
    fun updateDuplicateOf(value: String) = _state.update { it.copy(duplicateOf = value) }
    fun updateCustomTimelineNote(value: String) = _state.update { it.copy(customTimelineNote = value) }

    fun revealPrivate() {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            repository.ownerPrivateDetails(id)
                .onSuccess { details -> _state.update { it.copy(privateDetails = details) } }
                .onFailure { error ->
                    _state.update { it.copy(message = SafeUiError.generic(error, error.message ?: "Private details are not available.")) }
                }
        }
    }

    fun setVerified(verified: Boolean) {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            repository.adminSetVerification(id, verified, _state.value.reason, UUID.randomUUID().toString())
                .onSuccess {
                    load()
                    loadSelectedDetails(id)
                }
                .onFailure { error -> _state.update { it.copy(message = SafeUiError.generic(error, error.message ?: "Verification could not be updated.")) } }
        }
    }

    fun transition(status: PublicReportStatus) {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            repository.adminTransition(
                reportId = id,
                toStatus = status,
                publicNote = _state.value.publicNote.ifBlank { _state.value.customTimelineNote }.takeIf { it.isNotBlank() },
                privateNote = _state.value.reason.takeIf { it.isNotBlank() },
                duplicateOf = _state.value.duplicateOf.takeIf { it.isNotBlank() },
                requestId = UUID.randomUUID().toString(),
            ).onSuccess {
                _state.update { it.copy(publicNote = "", customTimelineNote = "", reason = "") }
                load()
                loadSelectedDetails(id)
            }.onFailure { error ->
                _state.update { it.copy(message = SafeUiError.generic(error, error.message ?: "Status could not be updated.")) }
            }
        }
    }

    fun postOfficialTimelineEntry() {
        val selected = _state.value.selected ?: return
        val note = _state.value.customTimelineNote.trim()
        if (note.isEmpty()) return
        transition(selected.status)
    }

    fun moderateComment(commentId: String) {
        val selectedId = _state.value.selected?.id ?: return
        _state.update { current ->
            current.copy(
                comments = current.comments.map { comment ->
                    if (comment.id == commentId) {
                        comment.copy(body = "[Comment redacted by moderator due to community guidelines violation]")
                    } else comment
                }
            )
        }
    }
}

@Composable
fun PublicReportAdminScreen(
    viewModel: PublicReportAdminViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { viewModel.load() }

    val filteredRows = when (state.selectedFilter) {
        "Open" -> state.rows.filter { it.status == PublicReportStatus.SUBMITTED || it.status == PublicReportStatus.ACKNOWLEDGED }
        "In Progress" -> state.rows.filter { it.status == PublicReportStatus.IN_PROGRESS }
        "Resolved" -> state.rows.filter { it.status == PublicReportStatus.COMPLETED || it.status == PublicReportStatus.CLOSED }
        "Critical" -> state.rows.filter { it.urgency == PublicReportUrgency.CRITICAL || it.urgency == PublicReportUrgency.HIGH }
        else -> state.rows
    }

    RtcScreenScaffold {
        item {
            RtcSectionHeader(
                title = "Public Reports & Timeline Moderation",
                subtitle = "Authorized staff tools to review reports, publish official timeline updates, and moderate community issues.",
            )
        }

        // 1. Queue Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Open", "In Progress", "Resolved", "Critical").forEach { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { viewModel.selectFilter(filter) },
                        label = { Text(filter) },
                    )
                }
            }
        }

        if (state.loading && state.rows.isEmpty()) {
            item { Text("Loading public reports moderation queue…") }
        }

        // 3. Moderation Queue Items List
        items(filteredRows.size, key = { filteredRows[it].id }) { index ->
            val row = filteredRows[index]
            val isSelected = state.selected?.id == row.id

            RtcCard {
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (row.urgency) {
                                PublicReportUrgency.CRITICAL -> Color(0xFFEF4444)
                                PublicReportUrgency.HIGH -> Color(0xFFF97316)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Text(
                                text = row.urgency.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (row.urgency == PublicReportUrgency.CRITICAL || row.urgency == PublicReportUrgency.HIGH) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = "Category: ${row.categorySlug} · Status: ${row.status.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = { viewModel.select(row) },
                        colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(if (isSelected) "Active Report (Selected)" else "Moderate Report & Timeline")
                    }
                }
            }
        }

        // 4. Detailed Selected Report Moderation Workspace
        state.selected?.let { selected ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(RtcSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MODERATION WORKSPACE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Text(
                            text = selected.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "Report ID: ${selected.id}\nCategory: ${selected.categorySlug} · Status: ${selected.status.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Divider()

                        // A. Workflow Action Inputs
                        Text("1. Workflow Notes & Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = state.publicNote,
                            onValueChange = viewModel::updatePublicNote,
                            label = { Text("Public Note (Visible on Timeline)") },
                            placeholder = { Text("E.g., Technical inspection dispatched for repair.") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = state.reason,
                            onValueChange = viewModel::updateReason,
                            label = { Text("Private Internal Note / Audit Reason") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = state.duplicateOf,
                            onValueChange = viewModel::updateDuplicateOf,
                            label = { Text("Duplicate of Report ID (If applicable)") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // B. Status Transition Action Buttons
                        Text("2. Status Workflow Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.transition(PublicReportStatus.ACKNOWLEDGED) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Acknowledge")
                                }

                                Button(
                                    onClick = { viewModel.transition(PublicReportStatus.IN_PROGRESS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("In Progress")
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.transition(PublicReportStatus.COMPLETED) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Mark Complete")
                                }

                                Button(
                                    onClick = { viewModel.transition(PublicReportStatus.CLOSED) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Close Report")
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { viewModel.transition(PublicReportStatus.REJECTED) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Reject Report")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.transition(PublicReportStatus.DUPLICATE) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Mark Duplicate")
                                }
                            }
                        }

                        Divider()

                        // C. Verification & Privacy Controls
                        Text("3. Verification & Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.setVerified(true) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify Report")
                            }

                            OutlinedButton(
                                onClick = { viewModel.setVerified(false) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text("Clear Verification")
                            }
                        }

                        OutlinedButton(
                            onClick = viewModel::revealPrivate,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reveal Protected Reporter Details")
                        }

                        state.privateDetails?.let { details ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Private Address: ${details.exactAddress ?: "Not provided"}", fontWeight = FontWeight.Bold)
                                    Text("Contact Permission: ${if (details.contactPermission) "Granted" else "Declined"}")
                                    Text("Reporter ID: ${selected.reporterId ?: "Anonymous"}")
                                }
                            }
                        }

                        Divider()

                        // D. Official Timeline Updates Management
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("4. Official Report Timeline Moderation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = state.customTimelineNote,
                            onValueChange = viewModel::updateCustomTimelineNote,
                            label = { Text("Publish Official Timeline Update") },
                            placeholder = { Text("E.g., Crews dispatched to repair main line valve.") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            onClick = viewModel::postOfficialTimelineEntry,
                            enabled = state.customTimelineNote.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publish Official Update to Public Timeline")
                        }

                        if (state.timelineEntries.isNotEmpty()) {
                            Text("Timeline History (${state.timelineEntries.size} updates):", style = MaterialTheme.typography.labelLarge)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.timelineEntries.forEach { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = "Status: ${entry.toStatus.label}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    text = formatTimestamp(entry.createdAt),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            entry.publicNote?.let { note ->
                                                Text(
                                                    text = note,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Divider()

                        // E. Resident Comment Moderation
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("5. Community Comments Moderation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        if (state.comments.isEmpty()) {
                            Text("No public comments posted on this report.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.comments.forEach { comment ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(comment.authorDisplayName, fontWeight = FontWeight.Bold)
                                                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { viewModel.moderateComment(comment.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Redact comment", tint = Color(0xFFEF4444))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        state.message?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun formatTimestamp(instant: Instant): String {
    return runCatching {
        DateTimeFormatter.ofPattern("dd MMM, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault("Recently")
}
