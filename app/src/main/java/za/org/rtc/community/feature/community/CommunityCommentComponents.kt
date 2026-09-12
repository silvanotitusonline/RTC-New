package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.ui.components.DirectoryEmptyState
import za.org.rtc.community.ui.theme.RtcSize
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

internal fun LazyListScope.communityCommentsSection(
    activePost: CommunityPost,
    activeComments: List<CommunityComment>,
    session: RtcSession,
    pendingCommentIds: Set<String>,
    isCreatingComment: Boolean,
    newComment: String,
    replyingToComment: CommunityComment?,
    editingCommentId: String?,
    editingText: String,
    canModerateComments: Boolean,
    onEditingCommentIdChange: (String?) -> Unit,
    onEditingTextChange: (String) -> Unit,
    onModerateCommentRequested: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onReplyToComment: (CommunityComment?) -> Unit,
    onNewCommentChange: (String) -> Unit,
    onSubmitComment: (String?) -> Unit,
) {
    item {
        Spacer(Modifier.size(RtcSpacing.small))
        Text(
            "Comments (${activeComments.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
    }

    val threadedComments = buildThreadedComments(activeComments)
    if (activeComments.isEmpty()) {
        item { DirectoryEmptyState("No comments yet. Start the conversation respectfully.") }
    }

    items(threadedComments, key = { it.comment.id }) { threaded ->
        CommunityThreadedCommentCard(
            activePost = activePost,
            comment = threaded.comment,
            depth = threaded.depth,
            session = session,
            commentPending = threaded.comment.id in pendingCommentIds,
            canModerateComments = canModerateComments,
            editing = editingCommentId == threaded.comment.id,
            editingText = editingText,
            onEditingTextChange = onEditingTextChange,
            onBeginEditing = {
                onEditingCommentIdChange(threaded.comment.id)
                onEditingTextChange(threaded.comment.content)
            },
            onCancelEditing = { onEditingCommentIdChange(null) },
            onSaveEditing = {
                onUpdateComment(threaded.comment.id, editingText)
                onEditingCommentIdChange(null)
            },
            onModerate = { onModerateCommentRequested(threaded.comment.id) },
            onDelete = { onDeleteComment(threaded.comment.id) },
            onReply = { onReplyToComment(threaded.comment) },
        )
    }

    if (!activePost.isLocked) {
        item {
            CommunityCommentComposer(
                replyingToComment = replyingToComment,
                newComment = newComment,
                isCreatingComment = isCreatingComment,
                onNewCommentChange = onNewCommentChange,
                onCancelReply = { onReplyToComment(null) },
                onSubmit = { onSubmitComment(replyingToComment?.id) },
            )
        }
    }
}

@Composable
private fun CommunityThreadedCommentCard(
    activePost: CommunityPost,
    comment: CommunityComment,
    depth: Int,
    session: RtcSession,
    commentPending: Boolean,
    canModerateComments: Boolean,
    editing: Boolean,
    editingText: String,
    onEditingTextChange: (String) -> Unit,
    onBeginEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveEditing: () -> Unit,
    onModerate: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
) {
    val isOwner = comment.authorId == session.id
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 18).dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(RtcSpacing.small),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CommunityAvatar(
                        comment.authorAvatarUrl,
                        comment.author,
                        Modifier.size(if (depth > 0) 28.dp else RtcSize.avatarCompact),
                    )
                    Spacer(Modifier.width(RtcSpacing.compact))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                comment.author,
                                fontWeight = FontWeight.SemiBold,
                                style = if (depth > 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                            )
                            if (comment.isStaff) {
                                Text(
                                    "Staff",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            "${comment.handle} · ${relativeTimeLabel(comment.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isOwner && canModerateComments) {
                        TextButton(onClick = onModerate, enabled = !commentPending) {
                            Text(if (commentPending) "Removing…" else "Remove as staff")
                        }
                    }
                }

                if (editing) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { onEditingTextChange(it.take(280)) },
                        label = { Text("Edit comment") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        supportingText = {
                            Text("Comments can be edited for one hour after posting · ${editingText.trim().length}/280")
                        },
                        enabled = !commentPending,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        OutlinedButton(
                            onClick = onCancelEditing,
                            modifier = Modifier.weight(1f),
                            enabled = !commentPending,
                        ) { Text("Cancel") }
                        Button(
                            onClick = onSaveEditing,
                            enabled = !commentPending && editingText.trim().length in 1..280,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (commentPending) "Saving…" else "Save") }
                    }
                } else {
                    Text(comment.content)
                    if (comment.editedAt != null) {
                        Text(
                            "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isOwner) {
                            if (isCommentWithinEditWindow(comment.createdAt)) {
                                TextButton(onClick = onBeginEditing, enabled = !commentPending) { Text("Edit") }
                            }
                            TextButton(onClick = onDelete, enabled = !commentPending) {
                                Text(if (commentPending) "Removing…" else "Delete")
                            }
                        }
                        if (!activePost.isLocked) {
                            TextButton(onClick = onReply, enabled = !commentPending) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Reply", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityCommentComposer(
    replyingToComment: CommunityComment?,
    newComment: String,
    isCreatingComment: Boolean,
    onNewCommentChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RtcSpacing.small),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (replyingToComment != null) "Replying to ${replyingToComment.author}" else "Add a comment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (replyingToComment != null) {
                    TextButton(onClick = onCancelReply) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel reply", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            OutlinedTextField(
                value = newComment,
                onValueChange = { onNewCommentChange(it.take(280)) },
                label = {
                    Text(
                        if (replyingToComment != null) {
                            "Write a reply to @${replyingToComment.handle}"
                        } else {
                            "Write a respectful comment"
                        }
                    )
                },
                enabled = !isCreatingComment,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Server-confirmed Community comment · ${newComment.trim().length}/280") },
                minLines = 3,
            )
            Button(
                onClick = onSubmit,
                enabled = !isCreatingComment && newComment.trim().length in 1..280,
            ) { Text(if (isCreatingComment) "Posting…" else "Post comment") }
        }
    }
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
        },
    )
}
