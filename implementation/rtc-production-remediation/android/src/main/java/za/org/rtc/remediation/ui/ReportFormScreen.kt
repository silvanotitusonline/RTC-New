package za.org.rtc.remediation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.remediation.presentation.PresentationRules
import za.org.rtc.remediation.presentation.ReportFormState
import za.org.rtc.remediation.presentation.ReportFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionField(
    label: String,
    selected: String?,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = choices.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    trailingIcon = { if (selected == id) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

/** The host owns BackHandler so its toolbar, system back, and tabs share one dirty guard.
 * IME padding applies once here; the form scrolls above a permanently reachable submit row. */
@Composable
fun ReportFormScreen(
    state: ReportFormState,
    viewModel: ReportFormViewModel,
    modifier: Modifier = Modifier,
    onRequestLocation: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Report an issue", style = MaterialTheme.typography.headlineSmall)
            Text("App support goes to the IT team. Infrastructure reports go to the municipal queue.")
            SelectionField(
                "Category", state.category,
                listOf("APP_SUPPORT" to "App Support", "INFRASTRUCTURE" to "Infrastructure"),
                !state.submitting, viewModel::setCategory,
            )
            if (state.attempted && state.category == null) Text("Choose a category.", color = MaterialTheme.colorScheme.error)
            val count = PresentationRules.characterCount(state.body.trim())
            val showBodyError = state.body.isNotEmpty() || state.attempted
            OutlinedTextField(
                value = state.body, onValueChange = viewModel::setBody,
                label = { Text("What happened?") },
                placeholder = { Text("Describe the issue and where it happened.") },
                supportingText = {
                    Column {
                        Text("$count/20 minimum · 4,000 maximum")
                        if (showBodyError) state.bodyError?.let { Text(it) }
                    }
                },
                isError = showBodyError && state.bodyError != null,
                enabled = !state.submitting, minLines = 5, maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            SelectionField(
                "Priority", state.priority,
                listOf("LOW" to "Low", "NORMAL" to "Normal", "HIGH" to "High", "URGENT" to "Urgent"),
                !state.submitting, viewModel::setPriority,
            )
            if (state.latitude != null) {
                Text("Current location attached")
                TextButton(onClick = { viewModel.setLocation(null, null) }, enabled = !state.submitting) {
                    Text("Remove location")
                }
            } else if (onRequestLocation != null) {
                TextButton(onClick = onRequestLocation, enabled = !state.submitting) { Text("Attach current location") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.submitting) "Saving report…" else "Submit report")
                }
            }
        }
    }
}
