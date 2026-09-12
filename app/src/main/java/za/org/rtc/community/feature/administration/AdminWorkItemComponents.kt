package za.org.rtc.community.feature.administration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OperationsWorkItemCard(
    item: OperationsWorkItem,
    canReassign: Boolean,
    onClaim: () -> Unit,
    onRelease: (String) -> Unit,
    onReadyForReview: (String) -> Unit,
    eligibleAssignees: List<AdminEligibleAssignee>,
    assigneesLoading: Boolean,
    assigneesError: String?,
    onLoadEligibleAssignees: () -> Unit,
    onRetryEligibleAssignees: () -> Unit,
    onClearEligibleAssignees: () -> Unit,
    onReassign: (String, String) -> Unit,
    onOpen: () -> Unit,
) {
    var releaseOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var readyOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var reassignOpen by rememberSaveable(item.id) { mutableStateOf(false) }

    fun openReassignPicker() {
        reassignOpen = true
        onLoadEligibleAssignees()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RtcSize.minimumTouchTarget * 2)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(RtcSpacing.small),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    item.priority.lowercase().replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${item.sourceType.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)} · ${item.state.lowercase().replaceFirstChar(Char::titlecase)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            when {
                item.isUnassigned -> OutlinedButton(
                    onClick = onClaim,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Claim") }

                item.assignedToMe -> {
                    Text(
                        "Assigned to you",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        OutlinedButton(onClick = { readyOpen = true }) { Text("Ready for review") }
                        TextButton(onClick = { releaseOpen = true }) { Text("Release") }
                        if (canReassign) TextButton(onClick = { openReassignPicker() }) { Text("Reassign") }
                    }
                }

                canReassign -> TextButton(
                    onClick = { openReassignPicker() },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Reassign") }
            }
        }
    }

    if (releaseOpen) {
        WorkItemReasonDialog(
            title = "Release work item",
            label = "Release reason",
            confirmLabel = "Release",
            onConfirm = { reason -> onRelease(reason); releaseOpen = false },
            onDismiss = { releaseOpen = false },
        )
    }
    if (readyOpen) {
        WorkItemReasonDialog(
            title = "Mark ready for review",
            label = "Review note",
            confirmLabel = "Mark ready",
            onConfirm = { note -> onReadyForReview(note); readyOpen = false },
            onDismiss = { readyOpen = false },
        )
    }
    if (reassignOpen) {
        WorkItemReassignDialog(
            assignees = eligibleAssignees,
            isLoading = assigneesLoading,
            errorMessage = assigneesError,
            onRetry = onRetryEligibleAssignees,
            onConfirm = { ownerId, reason ->
                onReassign(ownerId, reason)
                onClearEligibleAssignees()
                reassignOpen = false
            },
            onDismiss = {
                onClearEligibleAssignees()
                reassignOpen = false
            },
        )
    }
}

@Composable
private fun WorkItemReasonDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.trim().length >= 3,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkItemReassignDialog(
    assignees: List<AdminEligibleAssignee>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    val selectedAssignee = assignees.firstOrNull { it.userId == selectedUserId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reassign work item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text(
                    "Choose an eligible staff member. Only accounts returned by the protected server role check can be selected.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                when {
                    isLoading -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.widthIn(max = 24.dp))
                        Text("Loading eligible staff…", style = MaterialTheme.typography.bodySmall)
                    }

                    errorMessage != null -> Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(RtcSpacing.compact),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                errorMessage,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }

                    assignees.isEmpty() -> Text(
                        "No eligible staff accounts are currently available for this work item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedAssignee?.let {
                                "${it.displayName} · ${it.role.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)}"
                            }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assign to") },
                            placeholder = { Text("Select eligible staff") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            assignees.forEach { assignee ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(assignee.displayName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                assignee.role.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedUserId = assignee.userId
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reassignment reason") },
                    supportingText = { Text("Required for the assignment audit trail") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUserId?.let { onConfirm(it, reason.trim()) } },
                enabled = selectedUserId != null && reason.trim().length >= 3 && !isLoading,
            ) { Text("Reassign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

