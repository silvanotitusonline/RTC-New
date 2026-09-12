package za.org.rtc.community.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcDesignSystem
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun HomeFeedHeader(selectedTab: String, onTabSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FeedTabItem("For You", "for_you", selectedTab, onTabSelected)
        FeedTabItem("Following", "following", selectedTab, onTabSelected)
    }
}

@Composable
fun FeedTabItem(label: String, route: String, selectedTab: String, onTabSelected: (String) -> Unit) {
    Text(
        text = label,
        modifier = Modifier.clickable { onTabSelected(route) }.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selectedTab == route) RtcDesignSystem.TextPrimary else RtcDesignSystem.TextSecondary,
        fontWeight = if (selectedTab == route) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun ContinueDraftCard(
    draft: LocalDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    var confirmingDiscard by rememberSaveable(draft.id) { mutableStateOf(false) }
    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Continue saved draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (draft.title.isNotBlank()) Text(draft.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                draft.body.ifBlank { "This draft has no body text yet." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
            Text("Saved ${draft.savedAt}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Continue") }
                OutlinedButton(onClick = { confirmingDiscard = true }, modifier = Modifier.weight(1f)) { Text("Discard") }
            }
        }
    }
    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text("Discard saved draft?") },
            text = { Text("This removes the saved draft from this device. This action cannot be undone.") },
            confirmButton = {
                Button(onClick = { confirmingDiscard = false; onDiscard() }) { Text("Discard draft") }
            },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep draft") } },
        )
    }
}

@Composable
fun QuickAccessSection(
    onNavigate: (MainDestination) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onHelp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
        Text("Quick access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            FilledTonalButton(onClick = { onNavigate(MainDestination.COMMUNITY) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Campaign, contentDescription = null)
                Text("Community")
            }
            FilledTonalButton(onClick = { onNavigate(MainDestination.EXPLORE) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Explore, contentDescription = null)
                Text("Explore")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            OutlinedButton(onClick = { onOpenDirectory("projects") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Explore, contentDescription = null)
                Text("Projects")
            }
            OutlinedButton(onClick = { onOpenDirectory("centres") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Text("Centres")
            }
        }
        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
            Text("Help")
        }
    }
}
