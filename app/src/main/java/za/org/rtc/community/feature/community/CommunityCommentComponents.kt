package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.ui.theme.RtcSpacing

internal data class ThreadedComment(
    val comment: CommunityComment,
    val depth: Int,
)

internal fun buildThreadedComments(comments: List<CommunityComment>): List<ThreadedComment> {
    val byParent = comments.groupBy { it.parentId }
    val result = mutableListOf<ThreadedComment>()

    fun addChildren(parentId: String?, depth: Int) {
        val children = byParent[parentId] ?: return
        for (child in children) {
            result.add(ThreadedComment(child, depth))
            addChildren(child.id, (depth + 1).coerceAtMost(3))
        }
    }

    addChildren(null, 0)
    val addedIds = result.map { it.comment.id }.toSet()
    for (comment in comments) {
        if (comment.id !in addedIds) {
            result.add(ThreadedComment(comment, 0))
        }
    }
    return result
}

@Composable
internal fun ModerateCommentDialog(
    moderationPending: Boolean,
    moderationReason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!moderationPending) onDismiss() },
        title = { Text("Remove comment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text("The comment will be hidden from residents and recorded in the moderation audit log.")
                OutlinedTextField(
                    value = moderationReason,
                    onValueChange = onReasonChange,
                    label = { Text("Moderation reason") },
                    supportingText = { Text("Required · ${moderationReason.trim().length}/1,000") },
                    enabled = !moderationPending,
                    minLines = 3,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !moderationPending) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(moderationReason) },
                enabled = !moderationPending && moderationReason.trim().length in 3..1_000,
            ) { Text(if (moderationPending) "Removing…" else "Remove") }
        }
    )
}
