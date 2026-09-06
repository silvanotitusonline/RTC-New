package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.SharedTransitionScope
import za.org.rtc.community.ui.navigation.LocalSharedTransitionScope
import za.org.rtc.community.ui.navigation.LocalNavAnimatedVisibilityScope
import za.org.rtc.community.ui.navigation.EmphasizedEasing
import za.org.rtc.community.ui.navigation.CardExpandDurationMs
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import za.org.rtc.community.R
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.ui.components.RtcCommunityFeedCard
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.Surface

internal fun syntheticAvatarResource(name: String): Int = when ((name.hashCode() and Int.MAX_VALUE) % 5) {
    0 -> R.drawable.synthetic_member_01
    1 -> R.drawable.synthetic_member_02
    2 -> R.drawable.synthetic_member_03
    3 -> R.drawable.synthetic_member_04
    else -> R.drawable.synthetic_member_05
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun CommunityPostCard(
    post: CommunityPost,
    readingMode: Boolean,
    onOpenPost: (CommunityPost) -> Unit,
    onToggleLike: ((String) -> Unit)? = null,
    onToggleReaction: ((String, String) -> Unit)? = null,
    onSharePost: ((CommunityPost) -> Unit)? = null,
    onDeletePost: ((String) -> Unit)? = null,
    canDelete: Boolean = false,
    onRefreshMediaUrl: suspend (String) -> String? = { null },
    isLikePending: Boolean = false,
    syntheticAvatarRes: Int? = null,
    modifier: Modifier = Modifier,
) {
    val collapseAt = if (readingMode) 420 else 280
    val canCollapse = post.content.length > collapseAt
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable(post.id) { mutableStateOf(false) }
    var showEmojiPicker by rememberSaveable(post.id) { mutableStateOf(false) }
    val standardEmojis = listOf("❤️", "👍", "💡", "🔥", "🙏", "🙌")
    val displayContent = if (canCollapse && !expanded) {
        post.content.take(collapseAt).trimEnd() + "…"
    } else {
        post.content
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "post_card_${post.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(CardExpandDurationMs, easing = EmphasizedEasing) },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                renderInOverlayDuringTransition = false,
                zIndexInOverlay = 1f,
            )
        }
    } else {
        Modifier
    }

    RtcCommunityFeedCard(
        post = if (displayContent == post.content) post else post.copy(content = displayContent),
        onOpen = { onOpenPost(post) },
        syntheticAvatarRes = syntheticAvatarRes,
        timestampLabel = relativeTimeLabel(post.createdAt),
        headerTrailing = if (canDelete && onDeletePost != null) {
            {
                IconButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.size(RtcSize.minimumTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete post",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(RtcSize.actionIcon),
                    )
                }
            }
        } else null,
        modifier = Modifier.then(modifier).then(sharedModifier),
    ) {
        if (canCollapse) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.sizeIn(minHeight = RtcSize.minimumTouchTarget),
            ) { 
                Text(if (expanded) "Show less" else "Read more") 
            }
        }
        if (post.editedAt != null) {
            Text("Edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (post.media.isNotEmpty()) {
            CommunityMediaPreview(
                media = post.media,
                onOpen = { onOpenPost(post) },
                onRefreshMediaUrl = onRefreshMediaUrl,
            )
        }
        if (post.reactionCounts.isNotEmpty() || onToggleReaction != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                post.reactionCounts.forEach { (emoji, count) ->
                    val isActive = emoji in post.userReactions
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable(enabled = (onToggleReaction != null || onToggleLike != null) && !isLikePending) {
                                if (onToggleReaction != null) {
                                    onToggleReaction.invoke(post.id, emoji)
                                } else {
                                    onToggleLike?.invoke(post.id)
                                }
                            }
                            .semantics { contentDescription = "Reaction $emoji $count" },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(emoji, style = MaterialTheme.typography.labelMedium)
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
                if (onToggleReaction != null || onToggleLike != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .clickable { showEmojiPicker = !showEmojiPicker }
                            .semantics { contentDescription = "Add emoji reaction" },
                    ) {
                        Icon(
                            Icons.Outlined.AddReaction,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(6.dp).size(18.dp),
                        )
                    }
                }
            }
        }
        if (showEmojiPicker) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    standardEmojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clickable {
                                    showEmojiPicker = false
                                    if (onToggleReaction != null) {
                                        onToggleReaction.invoke(post.id, emoji)
                                    } else {
                                        onToggleLike?.invoke(post.id)
                                    }
                                }
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val likeLabel = if (post.viewerHasLiked) "Unlike post" else "Like post"
            TextButton(
                onClick = { onToggleLike?.invoke(post.id) },
                enabled = onToggleLike != null && !isLikePending,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (isLikePending) "$likeLabel. Saving reaction." else likeLabel
                    },
            ) {
                Icon(
                    if (post.viewerHasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (post.viewerHasLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(RtcSize.inlineIcon),
                )
                Spacer(Modifier.width(RtcSpacing.relatedText))
                Text(
                    text = if (isLikePending) "…" else post.reactions.toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = { onOpenPost(post) },
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                    .semantics { contentDescription = "Open ${post.comments} comments" },
            ) {
                Icon(
                    Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(RtcSize.inlineIcon),
                )
                Spacer(Modifier.width(RtcSpacing.relatedText))
                Text(
                    text = post.comments.toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = { onSharePost?.invoke(post) },
                enabled = onSharePost != null,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                    .semantics { contentDescription = "Share Community post" },
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(RtcSize.inlineIcon),
                )
                Spacer(Modifier.width(RtcSpacing.relatedText))
                Text("Share", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (post.isLocked) {
            RtcStatusChip("Comments closed", RtcStatusTone.NEUTRAL)
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete post?") },
            text = { Text("This post and its comments will be permanently removed from the community feed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePost?.invoke(post.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
