package za.org.rtc.community.feature.publicreports.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportValidation
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun PublicReportComposerScreen(
    guidelinesVersion: String,
    onSubmitted: (String) -> Unit = {},
    onPickEvidence: () -> Unit = {},
    viewModel: PublicReportComposerViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take(PublicReportValidation.EVIDENCE_MAX).forEach(viewModel::addEvidence)
    }
    if (state.guidelinesVersion != guidelinesVersion && guidelinesVersion.isNotBlank()) {
        viewModel.setGuidelines(state.guidelinesAccepted, guidelinesVersion)
    }
    LaunchedEffect(state.submittedReportId) {
        state.submittedReportId?.let { id ->
            onSubmitted(id)
            viewModel.consumeSubmittedId()
        }
    }

    RtcScreenScaffold {
        item { RtcSectionHeader(title = "New Public Report", subtitle = "Tell RTC what is happening in a public place.") }
        item {
            RtcCard {
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        label = { Text("Short title") },
                        supportingText = { Text("Example: Water leaking near clinic · ${state.title.length}/${PublicReportValidation.TITLE_MAX}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::setDescription,
                        label = { Text("What’s happening?") },
                        supportingText = { Text("What happened, when it started, and any immediate risk. · ${state.description.length}/${PublicReportValidation.DESCRIPTION_MAX}") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )
                    Text("When did it start?")
                    TextButton(onClick = { viewModel.setStartedAt(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (state.startedUnknown) "Not sure" else "Use Not sure")
                    }
                    Text("Category")
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        state.categories.forEach { category ->
                            FilterChip(
                                selected = state.categoryId == category.id,
                                onClick = { viewModel.setCategory(category.id) },
                                label = { Text(category.label) },
                            )
                        }
                    }
                    Text("Urgency")
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        listOf(PublicReportUrgency.LOW, PublicReportUrgency.NORMAL, PublicReportUrgency.HIGH, PublicReportUrgency.CRITICAL).forEach { urgency ->
                            FilterChip(
                                selected = state.urgency == urgency,
                                onClick = { viewModel.setUrgency(urgency) },
                                label = { Text(urgency.label) },
                            )
                        }
                    }
                    if (state.showCriticalNotice) {
                        Text(PublicReportValidation.CRITICAL_NOTICE, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = state.publicLocationLabel,
                        onValueChange = viewModel::setPublicLocation,
                        label = { Text("Public landmark or area") },
                        supportingText = { Text("${state.publicLocationLabel.length}/${PublicReportValidation.LOCATION_MAX}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.exactAddress,
                        onValueChange = viewModel::setExactAddress,
                        label = { Text("Manual address or landmark") },
                        supportingText = { Text("${state.mapUnavailableNotice} · ${state.exactAddress.length}/${PublicReportValidation.ADDRESS_MAX}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Evidence")
                    TextButton(
                        onClick = {
                            onPickEvidence()
                            picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm"))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add photo or video") }
                    state.evidence.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${item.kind.name} · ${item.byteSize} bytes")
                            TextButton(onClick = { viewModel.removeEvidence(item.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.cannotProvideEvidence, onCheckedChange = viewModel::setCannotProvideEvidence)
                        Text("I cannot safely provide evidence")
                    }
                    if (state.cannotProvideEvidence) {
                        OutlinedTextField(
                            value = state.noEvidenceReason,
                            onValueChange = viewModel::setNoEvidenceReason,
                            label = { Text("Why evidence cannot be provided") },
                            supportingText = { Text("${state.noEvidenceReason.length}/${PublicReportValidation.EXCEPTION_MAX}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text("Identity")
                    FilterChip(
                        selected = state.identityMode == PublicReportIdentityMode.NAMED,
                        onClick = { viewModel.setIdentity(PublicReportIdentityMode.NAMED) },
                        label = { Text("Post with my name") },
                    )
                    FilterChip(
                        selected = state.identityMode == PublicReportIdentityMode.ANONYMOUS,
                        onClick = { viewModel.setIdentity(PublicReportIdentityMode.ANONYMOUS) },
                        label = { Text("Post anonymously to the community") },
                    )
                    Text(PublicReportValidation.ANONYMOUS_NOTICE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.guidelinesAccepted,
                            onCheckedChange = { viewModel.setGuidelines(it, guidelinesVersion.ifBlank { state.guidelinesVersion }) },
                        )
                        Text("I accept the current Public Reports / Community guidelines")
                    }
                    Button(
                        onClick = viewModel::submit,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (state.submitting) "Submitting…" else "Submit Public Report")
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
