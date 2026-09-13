package za.org.rtc.community.feature.publicreports.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import za.org.rtc.community.core.maps.LiveMapPanel
import za.org.rtc.community.core.maps.RtcMapMarker
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
    var showMapPicker by rememberSaveable(state.clientRequestId, state.draftAccountId) { mutableStateOf(false) }
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
                        supportingText = { Text("Example: Water leaking near clinic") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::setDescription,
                        label = { Text("What’s happening?") },
                        supportingText = { Text("What happened, when it started, and any immediate risk.") },
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.exactAddress,
                        onValueChange = viewModel::setExactAddress,
                        label = { Text(if (state.selectedLocation == null) "Manual address or landmark" else "Private address or landmark (optional)") },
                        supportingText = { Text("The exact address is kept private for authorised RTC staff.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "The public map shows an approximate location. Your selected exact location is kept private for authorised RTC staff. Keep personal addresses out of the public landmark field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showMapPicker = true },
                        enabled = !state.submitting && state.draftAccountId != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (state.selectedLocation == null) "Choose location on map" else "Change map location")
                    }
                    if (state.selectedLocation != null) {
                        Text("Map location selected", style = MaterialTheme.typography.bodyMedium)
                        TextButton(
                            onClick = viewModel::clearMapLocation,
                            enabled = !state.submitting,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Remove map location and enter address manually") }
                    }
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
    if (showMapPicker) {
        Dialog(
            onDismissRequest = { showMapPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Choose report location", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showMapPicker = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
                    }
                    Text("Search for a place or use your current location, then confirm the selected location.", style = MaterialTheme.typography.bodySmall)
                    val existing = state.selectedLocation?.let { point ->
                        RtcMapMarker("report-draft-location", "Selected report location", point)
                    }
                    LiveMapPanel(
                        markers = listOfNotNull(existing),
                        initialSelectedMarkerId = existing?.id,
                        onOpenMarker = {},
                        onChooseLocation = { point ->
                            viewModel.setMapLocation(point)
                            showMapPicker = false
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }

}
