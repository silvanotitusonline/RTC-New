package za.org.rtc.community.feature.dailypost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.core.TimeFormatters
import za.org.rtc.community.feature.dailypost.domain.DailyPostComment

@Composable
fun DailyPostCommentsSection(
    articleId: String,
    comments: List<DailyPostComment>,
    totalCount: Int = comments.size,
    loading: Boolean,
    hasMore: Boolean = false,
    pendingCommentId: String?,
    currentUserId: String?,
    canModerate: Boolean,
    onRefresh: () -> Unit,
    onLoadOlder: () -> Unit = {},
    onCreate: (String, String?) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onModerate: (String, String) -> Unit,
    onReport: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var composerText by remember(articleId) { mutableStateOf("") }
    var editingCommentId by remember(articleId) { mutableStateOf<String?>(null) }
    var replyToCommentId by remember(articleId) { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var moderateTargetId by remember { mutableStateOf<String?>(null) }
    var moderationReason by remember { mutableStateOf("") }
    var reportTargetId by remember { mutableStateOf<String?>(null) }
    var reportDetail by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Comments", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = if (totalCount == 0) "Join the conversation respectfully." else "$totalCount visible comment${if (totalCount == 1) "" else "s"} in this article",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, enabled = pendingCommentId == null) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh comments")
            }
        }

        val replyTarget = comments.firstOrNull { it.id == replyToCommentId }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (replyTarget != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to ${displayAuthor(replyTarget, currentUserId)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { replyToCommentId = null }) { Text("Cancel") }
                    }
                }
                OutlinedTextField(
                    value = composerText,
                    onValueChange = { if (it.length <= 2000) composerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    label = { Text(if (editingCommentId == null) "Write a comment" else "Edit your comment") },
                    supportingText = { Text("${composerText.length}/2000") },
                    enabled = pendingCommentId == null,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (editingCommentId != null) {
                        TextButton(onClick = { editingCommentId = null; composerText = "" }) { Text("Cancel edit") }
                    }
                    Button(
                        onClick = {
                            val text = composerText.trim()
                            if (text.isNotEmpty()) {
                                editingCommentId?.let { onUpdate(it, text) } ?: onCreate(text, replyToCommentId)
                                composerText = ""
                                editingCommentId = null
                                replyToCommentId = null
                            }
                        },
                        enabled = composerText.trim().isNotEmpty() && pendingCommentId == null,
                    ) {
                        Text(if (editingCommentId == null) "Post comment" else "Save changes")
                    }
                }
            }
        }

        if (loading && comments.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else if (comments.isEmpty()) {
            Text("No comments yet. Be the first to contribute.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            comments.forEach { comment ->
                DailyPostCommentRow(
                    comment = comment,
                    currentUserId = currentUserId,
                    canModerate = canModerate,
                    isPending = pendingCommentId == comment.id,
                    onReply = { replyToCommentId = comment.id; composerText = "" },
                    onEdit = { editingCommentId = comment.id; composerText = comment.body; replyToCommentId = null },
                    onDelete = { deleteTargetId = comment.id },
                    onModerate = { moderateTargetId = comment.id; moderationReason = "" },
                    onReport = { reportTargetId = comment.id; reportDetail = "" },
                )
            }
            if (hasMore) {
                OutlinedButton(onClick = onLoadOlder, enabled = pendingCommentId == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Load older comments")
                }
            }
        }
    }

    deleteTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { if (pendingCommentId == null) deleteTargetId = null },
            title = { Text("Remove comment?") },
            text = { Text("Your comment will be removed from the article. This action cannot be undone from the app.") },
            confirmButton = {
                Button(onClick = { onDelete(targetId); deleteTargetId = null }, enabled = pendingCommentId == null) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTargetId = null }, enabled = pendingCommentId == null) { Text("Cancel") } },
        )
    }

    moderateTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { if (pendingCommentId == null) moderateTargetId = null },
            title = { Text("Hide comment") },
            text = {
                OutlinedTextField(
                    value = moderationReason,
                    onValueChange = { if (it.length <= 500) moderationReason = it },
                    label = { Text("Moderation reason") },
                    supportingText = { Text("${moderationReason.length}/500") },
                )
            },
            confirmButton = {
                Button(onClick = { onModerate(targetId, moderationReason.trim()); moderateTargetId = null }, enabled = moderationReason.trim().length >= 3 && pendingCommentId == null) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { moderateTargetId = null }, enabled = pendingCommentId == null) { Text("Cancel") } },
        )
    }

    reportTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { if (pendingCommentId == null) reportTargetId = null },
            title = { Text("Report comment") },
            text = {
                OutlinedTextField(
                    value = reportDetail,
                    onValueChange = { if (it.length <= 1000) reportDetail = it },
                    label = { Text("Why should this be reviewed?") },
                    supportingText = { Text("${reportDetail.length}/1000") },
                )
            },
            confirmButton = {
                Button(
                    onClick = { onReport(targetId, "OTHER", reportDetail.trim()); reportTargetId = null },
                    enabled = pendingCommentId == null,
                ) { Text("Report") }
            },
            dismissButton = { TextButton(onClick = { reportTargetId = null }, enabled = pendingCommentId == null) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DailyPostCommentRow(
    comment: DailyPostComment,
    currentUserId: String?,
    canModerate: Boolean,
    isPending: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onModerate: () -> Unit,
    onReport: () -> Unit,
) {
    val isOwner = comment.authorId == currentUserId
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (comment.depth.coerceIn(0, 2) * 18).dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) {
                    Text(displayAuthor(comment, currentUserId).take(1).uppercase(), modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayAuthor(comment, currentUserId), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(TimeFormatters.formatRelativeTime(comment.createdAtEpochMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isPending) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReply, enabled = !isPending) {
                    Icon(Icons.Filled.Reply, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reply")
                }
                if (isOwner) {
                    TextButton(onClick = onEdit, enabled = !isPending) { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
                    TextButton(onClick = onDelete, enabled = !isPending) { Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Remove") }
                }
                if (canModerate) {
                    TextButton(onClick = onModerate, enabled = !isPending) { Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Hide") }
                }
                if (!isOwner) {
                    TextButton(onClick = onReport, enabled = !isPending) { Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Report") }
                }
            }
        }
    }
}

private fun displayAuthor(comment: DailyPostComment, currentUserId: String?): String =
    if (comment.authorId == currentUserId) "You" else comment.authorName
