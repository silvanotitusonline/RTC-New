package za.org.rtc.community.feature.events.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun CommunityEventsScreen(
    viewModel: CommunityEventsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { viewModel.load() }

    RtcScreenScaffold {
        item {
            RtcSectionHeader(
                title = "Community Events",
                subtitle = "Upcoming local and wider-area events.",
            )
        }
        item {
            OutlinedTextField(
                value = state.locality,
                onValueChange = viewModel::updateLocality,
                label = { Text("Your locality (optional)") },
                supportingText = { Text("Use a locality to place nearby Events first.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.loading) {
            item { Text("Loading Events…", modifier = Modifier.padding(RtcSpacing.standard)) }
        }
        if (state.events.isEmpty() && !state.loading) {
            item {
                RtcCard {
                    Text("No upcoming Events yet.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Check back soon for local meetings, activities, and community opportunities.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.events.size, key = { state.events[it].id }) { index ->
            val event = state.events[index]
            RtcCard {
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(event.description, style = MaterialTheme.typography.bodyMedium)
                    Text(event.venueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.startsAt.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        state.message?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }
}
