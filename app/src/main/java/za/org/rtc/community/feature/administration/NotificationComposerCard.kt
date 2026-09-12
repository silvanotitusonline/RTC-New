package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Administrator composer for a broadcast push notification.
 *
 * Supports a title, message body, optional image URL and an audience selector.
 * When an image URL is supplied the receiving client renders it as an expanded
 * BigPicture notification.
 */
@Composable
fun NotificationComposerCard(
    viewModel: NotificationComposerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RtcCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Broadcast a notification",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Push a message to your community. Add an image URL to send a rich notification.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::updateTitle,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.body,
            onValueChange = viewModel::updateBody,
            label = { Text("Message") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.mediaUrl,
            onValueChange = viewModel::updateMediaUrl,
            label = { Text("Image URL (optional)") },
            singleLine = true,
            placeholder = { Text("https://") },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Text(
                text = "Send to",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NotificationTarget.entries.forEach { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.target == target,
                            onClick = { viewModel.updateTarget(target) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.target == target,
                        onClick = { viewModel.updateTarget(target) },
                    )
                    Text(target.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.lastResult?.let { result ->
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.lastResultIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        Button(
            onClick = viewModel::send,
            enabled = state.canSend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Sending")
            } else {
                Text("Dispatch notification")
            }
        }
    }
}
