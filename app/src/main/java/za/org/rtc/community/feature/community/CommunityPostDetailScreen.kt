package za.org.rtc.community.feature.community

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.CommunityAction
import za.org.rtc.community.app.CommunityActionUiState
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.ModerationReason
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.ui.components.PostCardSkeleton
import za.org.rtc.community.ui.components.PurposefulEmptyState
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.components.SkeletonBox
import za.org.rtc.community.ui.components.skeletonPulse
import za.org.rtc.community.ui.navigation.CardExpandDurationMs
import za.org.rtc.community.ui.navigation.EmphasizedEasing
import za.org.rtc.community.ui.navigation.LocalNavAnimatedVisibilityScope
import za.org.rtc.community.ui.navigation.LocalSharedTransitionScope
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun CommunityPostDetailScreen(
    postId: String,
    session: RtcSession,
    communityActionUi: CommunityActionUiState,
    guidelinesAccepted: Boolean?,
    onAcceptGuidelines: () -> Unit,
    onDismissCommunityMessage: () -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onReportPost: (String, ModerationReason, String) -> Unit,
    commentDraft: LocalDraft? = null,
    onSaveCommentDraft: (String) -> Unit = {},
    onDiscardCommentDraft: () -> Unit = {},
    communityViewModel: CommunityViewModel = hiltViewModel(),
) {
    val detailState by communityViewModel.detailState.collectAsStateWithLifecycle()
    val pendingLikeIds by communityViewModel.pendingLikeIds.collectAsStateWithLifecycle()
    val activePost = detailState.post
    val activeComments = detailState.comments
    var newComment by rememberSaveable(postId) { mutableStateOf(commentDraft?.body.orEmpty()) }
    var editingCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingText by rememberSaveable(postId) { mutableStateOf("") }
    var mediaGalleryOpen by rememberSaveable(postId) { mutableStateOf(false) }
    var selectedMediaIndex by rememberSaveable(postId) { mutableStateOf<Int?>(null) }
    var reportOpen by rememberSaveable(postId) { mutableStateOf(false) }
    var guidelinesOpen by rememberSaveable(postId) { mutableStateOf(false) }
    var postCommentAfterGuidelines by rememberSaveable(postId) { mutableStateOf(false) }
    var replyingToComment by rememberSaveable(postId) { mutableStateOf<CommunityComment?>(null) }
    var showEmojiPicker by rememberSaveable(postId) { mutableStateOf(false) }
    val standardEmojis = listOf("❤️", "👍", "💡", "🔥", "🙏", "🙌")
    var moderatingCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var moderationReason by rememberSaveable(postId) { mutableStateOf("") }
    val canModerateComments = session.role in setOf(
        UserRole.CONTENT_EDITOR,
        UserRole.MODERATOR,
        UserRole.SYSTEM_ADMIN,
    )

    LaunchedEffect(commentDraft?.id) {
        if (commentDraft != null && newComment.isBlank()) newComment = commentDraft.body
    }
    LaunchedEffect(newComment) {
        if (newComment.isNotBlank()) onSaveCommentDraft(newComment)
    }
    DisposableEffect(Unit) {
        onDispose {
            if (newComment.isNotBlank()) onSaveCommentDraft(newComment)
        }
    }
    LaunchedEffect(postId) {
        if (postId.isNotBlank()) communityViewModel.loadPostDetail(postId)
    }
    LaunchedEffect(detailState.isCreatingComment, detailState.isSuccess, detailState.message) {
        if (!detailState.isCreatingComment && detailState.isSuccess && detailState.message == "Comment posted.") {
            newComment = ""
            replyingToComment = null
            onDiscardCommentDraft()
        }
    }
    LaunchedEffect(communityActionUi.action, communityActionUi.isSuccess, guidelinesAccepted, postId) {
        if (
            (communityActionUi.action == CommunityAction.GUIDELINES && communityActionUi.isSuccess) ||
            (guidelinesOpen && guidelinesAccepted == true)
        ) {
            guidelinesOpen = false
            if (postCommentAfterGuidelines && postId.isNotBlank() && newComment.trim().isNotEmpty()) {
                postCommentAfterGuidelines = false
                communityViewModel.createComment(postId, newComment, parentId = replyingToComment?.id)
            }
        }
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "post_card_$postId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(CardExpandDurationMs, easing = EmphasizedEasing) },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                renderInOverlayDuringTransition = false,
                zIndexInOverlay = 1f,
            ).skipToLookaheadSize()
        }
    } else {
        Modifier
    }

    if (activePost == null) {
        if (detailState.isLoading || postId.isNotBlank()) {
            if (detailState.message == null) {
                LazyColumn(
                    contentPadding = PaddingValues(RtcSpacing.standard),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
                ) {
                    item { PostCardSkeleton(modifier = Modifier.fillMaxWidth().then(sharedModifier)) }
                    item {
                        Spacer(Modifier.height(RtcSpacing.small))
                        SkeletonBox(height = 20.dp, width = 120.dp)
                    }
                    items(3) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(RtcSpacing.small),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(RtcSize.avatarCompact)
                                        .skeletonPulse(shape = CircleShape),
                                )
                                Spacer(Modifier.width(RtcSpacing.compact))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    SkeletonBox(height = 14.dp, width = 100.dp)
                                    SkeletonBox(height = 14.dp)
                                    SkeletonBox(height = 14.dp, width = 180.dp)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PurposefulEmptyState(
                        detailState.message ?: "Community conversation unavailable.",
                        "Retry",
                    ) { communityViewModel.loadPostDetail(postId) }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PurposefulEmptyState("This Community post is no longer available.", "Return to Community", {})
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(RtcSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth().then(sharedModifier)) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommunityAvatar(
                            activePost.authorAvatarUrl,
                            activePost.author,
                            Modifier.size(RtcSize.avatarStandard),
                        )
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(activePost.author, fontWeight = FontWeight.Bold)
                            Text(
                                "${activePost.handle} · ${relativeTimeLabel(activePost.createdAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (activePost.isOfficial) {
                            Text(
                                "Staff",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text(activePost.content, style = MaterialTheme.typography.bodyLarge)
                    if (activePost.editedAt != null) {
                        Text(
                            "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (activePost.media.isNotEmpty()) {
                        CommunityMediaPreview(
                            media = activePost.media,
                            onOpen = { selectedMediaIndex = 0 },
                            onMediaClick = { index -> selectedMediaIndex = index },
                            onRefreshMediaUrl = communityViewModel::refreshMediaUrl,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        activePost.reactionCounts.forEach { (emoji, count) ->
                            val isActive = emoji in activePost.userReactions
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable { communityViewModel.toggleReaction(activePost.id, emoji) }
                                    .semantics { contentDescription = "Reaction $emoji $count" },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.labelMedium)
                                    Text(count.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                                                communityViewModel.toggleReaction(activePost.id, emoji)
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
                        val likePending = activePost.id in pendingLikeIds
                        val likeLabel = if (activePost.viewerHasLiked) "Unlike post" else "Like post"
                        TextButton(
                            onClick = { communityViewModel.toggleLike(activePost.id) },
                            enabled = !likePending,
                            modifier = Modifier
                                .weight(1f)
                                .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = if (likePending) "$likeLabel. Saving reaction." else likeLabel
                                },
                        ) {
                            Icon(
                                if (activePost.viewerHasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (activePost.viewerHasLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(RtcSize.inlineIcon),
                            )
                            Spacer(Modifier.width(RtcSpacing.relatedText))
                            Text(
                                if (likePending) "…" else activePost.reactions.toString(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                                .semantics { contentDescription = "${activePost.comments} comments" },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(RtcSize.inlineIcon),
                            )
                            Spacer(Modifier.width(RtcSpacing.relatedText))
                            Text(activePost.comments.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                        val repostTint = if (activePost.isRepostedByViewer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        TextButton(
                            onClick = { communityViewModel.repostPost(activePost.id) },
                            modifier = Modifier
                                .weight(1f)
                                .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                                .semantics { contentDescription = "Repost" },
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = repostTint,
                                modifier = Modifier.size(RtcSize.inlineIcon),
                            )
                            Spacer(Modifier.width(RtcSpacing.relatedText))
                            Text(
                                if (activePost.repostCount > 0) activePost.repostCount.toString() else "Repost",
                                style = MaterialTheme.typography.labelMedium,
                                color = repostTint,
                            )
                        }
                        val bookmarkTint = if (activePost.isBookmarkedByViewer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        TextButton(
                            onClick = { communityViewModel.toggleBookmark(activePost.id) },
                            modifier = Modifier
                                .weight(1f)
                                .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                                .semantics { contentDescription = "Bookmark" },
                        ) {
                            Icon(
                                if (activePost.isBookmarkedByViewer) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = bookmarkTint,
                                modifier = Modifier.size(RtcSize.inlineIcon),
                            )
                            Spacer(Modifier.width(RtcSpacing.relatedText))
                            Text(
                                if (activePost.bookmarkCount > 0) activePost.bookmarkCount.toString() else "Save",
                                style = MaterialTheme.typography.labelMedium,
                                color = bookmarkTint,
                            )
                        }
                        TextButton(
                            onClick = { onSharePost(activePost) },
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
                    if (activePost.isLocked) RtcStatusChip("Comments closed", RtcStatusTone.NEUTRAL)
                }
            }
        }

        communityCommentsSection(
            activePost = activePost,
            activeComments = activeComments,
            session = session,
            pendingCommentIds = detailState.pendingCommentIds,
            isCreatingComment = detailState.isCreatingComment,
            newComment = newComment,
            replyingToComment = replyingToComment,
            editingCommentId = editingCommentId,
            editingText = editingText,
            canModerateComments = canModerateComments,
            onEditingCommentIdChange = { editingCommentId = it },
            onEditingTextChange = { editingText = it },
            onModerateCommentRequested = { commentId ->
                moderatingCommentId = commentId
                moderationReason = ""
            },
            onUpdateComment = { commentId, body ->
                communityViewModel.updateComment(activePost.id, commentId, body)
            },
            onDeleteComment = { commentId -> communityViewModel.deleteComment(activePost.id, commentId) },
            onReplyToComment = { replyingToComment = it },
            onNewCommentChange = { newComment = it },
            onSubmitComment = { parentId ->
                if (guidelinesAccepted == true) {
                    communityViewModel.createComment(activePost.id, newComment, parentId = parentId)
                } else {
                    postCommentAfterGuidelines = true
                    guidelinesOpen = true
                }
            },
        )
    }

    if (selectedMediaIndex != null || mediaGalleryOpen) {
        FullScreenMediaGallery(
            media = activePost.media,
            initialIndex = selectedMediaIndex ?: 0,
            onRefreshMediaUrl = communityViewModel::refreshMediaUrl,
            onDismiss = {
                mediaGalleryOpen = false
                selectedMediaIndex = null
            },
        )
    }
    if (reportOpen) {
        CommunityReportDialog(
            onDismiss = { reportOpen = false },
            onSubmit = { reason, detail ->
                onReportPost(activePost.id, reason, detail)
                reportOpen = false
            },
        )
    }
    if (guidelinesOpen) {
        CommunityGuidelinesDialog(
            isCheckingStatus = guidelinesAccepted == null,
            isAccepting = communityActionUi.action == CommunityAction.GUIDELINES && communityActionUi.isWorking,
            onDismiss = { guidelinesOpen = false },
            onAccept = onAcceptGuidelines,
        )
    }
    moderatingCommentId?.let { commentId ->
        val moderationPending = commentId in detailState.pendingCommentIds
        ModerateCommentDialog(
            moderationPending = moderationPending,
            moderationReason = moderationReason,
            onReasonChange = { moderationReason = it.take(1_000) },
            onDismiss = {
                moderatingCommentId = null
                moderationReason = ""
            },
            onConfirm = { reason -> communityViewModel.moderateComment(activePost.id, commentId, reason) },
        )
    }
}
