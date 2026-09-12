package za.org.rtc.community.feature.events.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import za.org.rtc.community.feature.events.domain.CommunityEventState
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun AdminEventsScreen(
    viewModel: AdminEventsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var eventId by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var startsAt by rememberSaveable { mutableStateOf(Instant.now().plusSeconds(86_400).toString()) }
    var endsAt by rememberSaveable { mutableStateOf(Instant.now().plusSeconds(90_000).toString()) }
    var locality by rememberSaveable { mutableStateOf("") }
    var venue by rememberSaveable { mutableStateOf("") }
    var isLocal by rememberSaveable { mutableStateOf(true) }
    var cancellationReason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }

    RtcScreenScaffold {
        item { RtcSectionHeader("Events calendar", "Create, edit, publish and cancel Community Events.") }
        item {
            RtcCard(protected = true) {
                Text(if (eventId == null) "New Event" else "Edit Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it.take(140) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(2_000) }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startsAt, { startsAt = it }, label = { Text("Starts at (ISO date/time)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endsAt, { endsAt = it }, label = { Text("Ends at (ISO date/time)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(venue, { venue = it.take(180) }, label = { Text("Venue") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(locality, { locality = it.take(120) }, label = { Text("Locality (optional)") }, modifier = Modifier.fillMaxWidth())
                FilterChip(selected = isLocal, onClick = { isLocal = !isLocal }, label = { Text(if (isLocal) "Local Event" else "Wider-area Event") })
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    OutlinedButton(
                        onClick = { viewModel.save(eventId, title, description, startsAt, endsAt, locality, venue, isLocal, publishAfterSave = false) },
                        enabled = !state.submitting && title.trim().isNotEmpty() && description.trim().isNotEmpty() && venue.trim().isNotEmpty(),
                    ) { Text("Save draft") }
                    Button(
                        onClick = { viewModel.save(eventId, title, description, startsAt, endsAt, locality, venue, isLocal, publishAfterSave = true) },
                        enabled = !state.submitting && title.trim().isNotEmpty() && description.trim().isNotEmpty() && venue.trim().isNotEmpty(),
                    ) { Text("Save & publish") }
                }
                if (eventId != null) {
                    OutlinedButton(onClick = {
                        eventId = null
                        title = ""
                        description = ""
                        venue = ""
                        locality = ""
                        cancellationReason = ""
                    }) { Text("Clear editor") }
                }
            }
        }
        if (state.loading) item { Text("Loading administrator Events…") }
        item { Text("Calendar entries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(state.events.size, key = { state.events[it].id }) { index ->
            val event = state.events[index]
            RtcCard(protected = true) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${event.startsAt} → ${event.endsAt}")
                Text("${event.venueLabel}${event.locality?.let { " · $it" }.orEmpty()}")
                Text(event.state.name, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    AssistChip(onClick = {
                        eventId = event.id
                        title = event.title
                        description = event.description
                        startsAt = event.startsAt.toString()
                        endsAt = event.endsAt.toString()
                        venue = event.venueLabel
                        locality = event.locality.orEmpty()
                        isLocal = event.isLocal
                    }, label = { Text("Edit") })
                    if (event.state == CommunityEventState.DRAFT) AssistChip(onClick = { viewModel.publish(event.id) }, label = { Text("Publish") })
                    AssistChip(onClick = { viewModel.delete(event.id) }, label = { Text("Delete") })
                }
                if (event.state != CommunityEventState.CANCELLED) {
                    OutlinedTextField(cancellationReason, { cancellationReason = it.take(500) }, label = { Text("Cancellation reason") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { viewModel.cancel(event.id, cancellationReason) }, enabled = cancellationReason.trim().length >= 3 && !state.submitting) { Text("Cancel Event") }
                }
            }
        }
        state.message?.let { message -> item { Text(message, color = if (message.contains("could not", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
    }
}
