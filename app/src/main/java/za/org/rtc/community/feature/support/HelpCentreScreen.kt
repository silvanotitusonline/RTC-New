package za.org.rtc.community.feature.support

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import za.org.rtc.community.feature.explore.DirectoryCard
import za.org.rtc.community.ui.animation.RtcMotionAlertDialog
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun HelpCentreScreen(viewModel: ResidentAssistantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(RtcSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
    ) {
        item {
            Text("How can we help?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }
        item {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Search Help Centre") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            )
        }
        item {
            ResidentAssistantCard(state = state, onDraftChange = viewModel::updateDraft, onAsk = viewModel::ask)
        }
        item { DirectoryCard("Using Support", "Start and track requests.", Icons.Filled.SupportAgent) }
        item { DirectoryCard("Community guidelines", "Posting, reporting, and privacy.", Icons.Filled.Forum) }
        item { DirectoryCard("Privacy controls", "Data exports and account deletion.", Icons.Filled.Shield) }
        item { DirectoryCard("Accessibility", "Reading mode, contrast, and text scaling.", Icons.Filled.AccessibilityNew) }
        item { ApprovedExternalLinkCard("Public service portal", "Access the official public service portal.", "https://www.gov.za/") }
    }
}

@Composable
internal fun ResidentAssistantCard(
    state: ResidentAssistantUiState,
    onDraftChange: (String) -> Unit,
    onAsk: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(RtcSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("RTC Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Guidance only. It cannot change your account or publish content.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.messages.takeLast(6).forEach { message ->
                Surface(
                    color = if (message.fromResident) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(message.text, modifier = Modifier.padding(RtcSpacing.small), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.heightIn(max = 28.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                listOf("How do I reset my password?", "How do I create a post?", "How do I report an issue?").forEach { prompt ->
                    AssistChip(onClick = { onAsk(prompt) }, label = { Text(prompt, maxLines = 1) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    label = { Text("Ask RTC Assistant") },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading,
                    maxLines = 3,
                )
                IconButton(onClick = { onAsk(state.draft) }, enabled = state.draft.trim().length >= 2 && !state.isLoading) {
                    Icon(Icons.Filled.Send, contentDescription = "Send question")
                }
            }
        }
    }
}

@Composable
private fun ApprovedExternalLinkCard(title: String, description: String, url: String) {
    val context = LocalContext.current
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    DirectoryCard(title, description, Icons.Filled.Visibility) { chooserOpen = true }
    if (chooserOpen) {
        RtcMotionAlertDialog(
            onDismissRequest = { chooserOpen = false },
            title = { Text(title) },
            text = { Text("This approved external link opens in the in-app browser by default. You may instead use your device browser.") },
            confirmButton = {
                Button(onClick = { chooserOpen = false; CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }) { Text("Open in app") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { chooserOpen = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Use device browser") }
            },
        )
    }
}
