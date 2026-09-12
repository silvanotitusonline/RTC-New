package za.org.rtc.community.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.feature.events.domain.CommunityEvent
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun CommunityEventsWeeklySummarySection(
    events: List<CommunityEvent>,
    onNavigateToCalendar: () -> Unit,
    onToggleRsvp: (String) -> Unit,
) {
    val visibleEvents = events
        .filter { it.state == za.org.rtc.community.feature.events.domain.CommunityEventState.PUBLISHED }
        .sortedBy { it.startsAt }
        .take(3)

    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Upcoming events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNavigateToCalendar) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text("Calendar")
                }
            }
            if (visibleEvents.isEmpty()) {
                Text("No published community events are currently scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                visibleEvents.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${event.startsAt.toString().take(16).replace('T', ' ')} · ${event.venueLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onToggleRsvp(event.id) }) {
                            Text(if (event.isRsvped) "Cancel RSVP" else "RSVP")
                        }
                    }
                }
            }
        }
    }
}
