package za.org.rtc.community.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import za.org.rtc.community.core.DraftArea
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcHomeDashboard
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun HomeSnapshotLiveBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(RtcHomeDashboard.liveBadgeRadius),
    ) {
        Text("Snapshot", modifier = Modifier.padding(horizontal = RtcHomeDashboard.liveBadgeHorizontalPadding, vertical = RtcHomeDashboard.liveBadgeVerticalPadding), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HomeSnapshotMetric(
    value: String,
    label: String,
    directory: String,
    onOpenDirectory: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RtcSize.minimumTouchTarget)
            .clickable(role = Role.Button) { onOpenDirectory(directory) }
            .padding(horizontal = RtcHomeDashboard.metricInset),
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CommunityEventsWeeklySummarySection(
    events: List<za.org.rtc.community.feature.events.domain.CommunityEvent>,
    onNavigateToCalendar: () -> Unit,
    onToggleRsvp: (String) -> Unit,
) {
    val publishedEvents = events.filter { it.state == za.org.rtc.community.feature.events.domain.CommunityEventState.PUBLISHED }
    val weeklyEvents = publishedEvents.take(3)

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Happening in the community this week",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Upcoming local gatherings, workshops, and municipal meetings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RtcStatusChip(
                text = "${weeklyEvents.size} events this week",
                tone = RtcStatusTone.PROTECTED,
            )
        }

        if (weeklyEvents.isEmpty()) {
            RtcCard {
                Text("No events scheduled for this week.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Check the monthly events calendar for upcoming meetings and community activities.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                weeklyEvents.forEach { event ->
                    RtcCard {
                        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = event.category.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = formatEventDateTime(event),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            Text(
                                text = event.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f, fill = false),
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = event.venueLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }

                                FilterChip(
                                    selected = event.isRsvped,
                                    onClick = { onToggleRsvp(event.id) },
                                    label = {
                                        Text(if (event.isRsvped) "✓ Attending (${event.rsvpCount})" else "RSVP (${event.rsvpCount})")
                                    },
                                    leadingIcon = {
                                        if (event.isRsvped) {
                                            androidx.compose.material3.Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onNavigateToCalendar,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(RtcSize.inlineIcon),
            )
            Spacer(modifier = Modifier.width(RtcSpacing.compact))
            Text("Explore Full Events Calendar")
        }
    }
}

private fun formatEventDateTime(event: za.org.rtc.community.feature.events.domain.CommunityEvent): String {
    return runCatching {
        val zdt = event.startsAt.atZone(java.time.ZoneId.of(event.timeZone))
        val dayName = zdt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val monthName = zdt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val dayOfMonth = zdt.dayOfMonth
        val hour = zdt.hour.toString().padStart(2, '0')
        val minute = zdt.minute.toString().padStart(2, '0')
        "$dayName, $monthName $dayOfMonth • $hour:$minute"
    }.getOrDefault("Sep 2026")
}

@Composable
fun ContinueDraftCard(draft: LocalDraft, onResume: () -> Unit, onDiscard: () -> Unit) {
    var discardConfirmationOpen by rememberSaveable(draft.id) { mutableStateOf(false) }
    RtcCard(protected = true) {
        RtcStatusChip("Saved draft", RtcStatusTone.PROTECTED)
        Text("Continue your draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            when (draft.area) {
                DraftArea.COMMUNITY -> "A Community post draft was saved ${draft.savedAt}."
                DraftArea.COMMUNITY_COMMENT -> "A Community comment draft was saved ${draft.savedAt}."
                DraftArea.NOTICE -> "A Community Notice draft was saved ${draft.savedAt}."
                DraftArea.SUPPORT -> "A support request draft was saved ${draft.savedAt}."
                DraftArea.STAFF_CONTENT -> "An editorial draft was saved ${draft.savedAt}."
                DraftArea.STAFF_MODERATION -> "A moderation response draft was saved ${draft.savedAt}."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Button(onClick = onResume) { Text("Continue") }
            TextButton(onClick = { discardConfirmationOpen = true }) { Text("Discard") }
        }
    }
    if (discardConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { discardConfirmationOpen = false },
            title = { Text("Discard saved draft?") },
            text = { Text("This removes the saved draft from this device. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirmationOpen = false
                    onDiscard()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmationOpen = false }) { Text("Keep draft") }
            },
        )
    }
}
