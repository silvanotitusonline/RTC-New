package za.org.rtc.community.feature.community

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun CommunityPostEditorDialog(
    post: CommunityPost,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var body by remember(post.id, post.content) { mutableStateOf(post.content) }
    val trimmed = body.trim()
    val valid = trimmed.length in 1..280

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit post") },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(280) },
                label = { Text("Post") },
                supportingText = { Text("${trimmed.length}/280") },
                minLines = 4,
                enabled = !saving,
                modifier = Modifier,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
        confirmButton = {
            Button(onClick = { onSave(trimmed) }, enabled = valid && !saving) {
                if (saving) {
                    Row {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Text("Saving…")
                    }
                } else {
                    Text("Save")
                }
            }
        },
    )
}
