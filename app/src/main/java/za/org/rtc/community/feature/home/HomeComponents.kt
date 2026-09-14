package za.org.rtc.community.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        FeedTabItem("For You", "for_you", selectedTab, onTabSelected)
        FeedTabItem("Following", "following", selectedTab, onTabSelected)
    }
}

@Composable
fun FeedTabItem(label: String, route: String, selectedTab: String, onTabSelected: (String) -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clickable { onTabSelected(route) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selectedTab == route) RtcDesignSystem.TextPrimary else RtcDesignSystem.TextSecondary,
        fontWeight = if (selectedTab == route) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun QuickAccessSection(
    onNavigate: (MainDestination) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onHelp: () -> Unit,
) {
    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Quick access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
            ) {
                OutlinedButton(
                    onClick = { onOpenDirectory("projects") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Projects")
                }
                OutlinedButton(
                    onClick = { onOpenDirectory("centres") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Centres")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
            ) {
                OutlinedButton(
                    onClick = { onNavigate(MainDestination.COMMUNITY) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Community")
                }
                OutlinedButton(
                    onClick = onHelp,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Help")
                }
            }
        }
    }
}

@Composable
fun CommunityEventsWeeklySummarySection(
    events: List<CommunityEvent>,
    onNavigateToCalendar: () -> Unit,
    onToggleRsvp: (String) -> Unit,
) {
    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("This Week in Community", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNavigateToCalendar) {
                    Text("Calendar")
                }
            }
            if (events.isEmpty()) {
                Text("No community events scheduled this week.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else {
                events.take(3).forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(event.venueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onToggleRsvp(event.id) }) {
                            Text(if (event.isRsvped) "Attending" else "RSVP")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueDraftCard(
    draft: LocalDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Continue saved draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(draft.title.ifBlank { draft.body.take(60) }, style = MaterialTheme.typography.bodyMedium)
            Text("Saved ${draft.savedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Text("Resume")
                }
                OutlinedButton(onClick = { showConfirm = true }, modifier = Modifier.weight(1f)) {
                    Text("Discard")
                }
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Discard saved draft?") },
            text = { Text("This will permanently remove your unsaved draft.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onDiscard()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
