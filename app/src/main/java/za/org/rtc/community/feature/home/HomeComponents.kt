package za.org.rtc.community.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.feature.events.domain.CommunityEvent
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcDesignSystem
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun HomeFeedHeader(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FeedTabItem("For You", "for_you", selectedTab, onTabSelected)
        FeedTabItem("Following", "following", selectedTab, onTabSelected)
    }
}

@Composable
fun FeedTabItem(
    label: String,
    route: String,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clickable { onTabSelected(route) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selectedTab == route) RtcDesignSystem.TextPrimary else RtcDesignSystem.TextSecondary,
        fontWeight = if (selectedTab == route) FontWeight.Bold else FontWeight.Normal,
    )
}

/**
 * Resident continuity surface for work that exists only on this device until the resident resumes
 * and submits it. Copy deliberately says "saved locally" so a draft is never confused with a
 * server-confirmed Community post, Public Report, or support request.
 */
@Composable
internal fun ContinueDraftCard(
    draft: LocalDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    var confirmDiscard by rememberSaveable(draft.id) { mutableStateOf(false) }

    RtcCard {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Text("Continue saved draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Saved locally · ${draft.savedAt}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.title.isNotBlank()) {
                Text(draft.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(
                draft.body.ifBlank { "Your unfinished work is still on this device." },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
            Text(
                "This has not been submitted or confirmed by the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Resume") }
                OutlinedButton(
                    onClick = { confirmDiscard = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Discard") }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard saved draft?") },
            text = { Text("This removes the local draft from this device. It cannot be recovered from RTC because it has not been submitted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDiscard()
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep draft") }
            },
        )
    }
}

@Composable
internal fun QuickAccessSection(
    onNavigate: (MainDestination) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onHelp: () -> Unit,
) {
    RtcCard {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Quick access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                OutlinedButton(
                    onClick = { onOpenDirectory("projects") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Projects") }
                OutlinedButton(
                    onClick = { onOpenDirectory("centres") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Service centres") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                OutlinedButton(
                    onClick = { onNavigate(MainDestination.COMMUNITY) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Community") }
                OutlinedButton(
                    onClick = { onNavigate(MainDestination.SUPPORT) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Support") }
            }
            TextButton(
                onClick = onHelp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Help") }
        }
    }
}

@Composable
internal fun CommunityEventsWeeklySummarySection(
    events: List<CommunityEvent>,
    onNavigateToCalendar: () -> Unit,
    onToggleRsvp: (String) -> Unit,
) {
    val published = events.filter { it.state.name == "PUBLISHED" }.take(3)
    RtcCard {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Community events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNavigateToCalendar) { Text("View all") }
            }
            if (published.isEmpty()) {
                Text(
                    "No published community events are available right now.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                published.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${event.startsAt.toString().take(16).replace('T', ' ')} · ${event.venueLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { onToggleRsvp(event.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(if (event.isRsvped) "Cancel RSVP" else "RSVP")
                        }
                    }
                }
            }
        }
    }
}
