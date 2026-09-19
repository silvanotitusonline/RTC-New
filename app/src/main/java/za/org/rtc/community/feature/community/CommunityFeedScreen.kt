package za.org.rtc.community.feature.community

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import za.org.rtc.community.ui.components.PostCardSkeleton
import za.org.rtc.community.ui.components.parallaxHeader
import za.org.rtc.community.ui.components.parallaxScrollItem
import androidx.compose.material3.ExtendedFloatingActionButton
import za.org.rtc.community.ui.animation.RtcMotionAlertDialog
import za.org.rtc.community.ui.animation.RtcMotionFab
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.CommunityAction
import za.org.rtc.community.app.CommunityActionUiState
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.core.ModerationReason
import za.org.rtc.community.feature.home.ContinueDraftCard
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing
import za.org.rtc.community.ui.theme.RtcStroke

import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.UserRole

@Composable
internal fun CommunityScreen(
    readingMode: Boolean,
    useSyntheticPortraits: Boolean,
    draft: LocalDraft?,
    communityActionUi: CommunityActionUiState,
    guidelinesAccepted: Boolean?,
    onAcceptGuidelines: () -> Unit,
    onDismissCommunityMessage: () -> Unit,
    onCreatePost: (String, List<Uri>, String) -> Unit,
    onSaveDraft: (String) -> Unit,
    onDiscardDraft: () -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onOpenReport: (String) -> Unit = {},
    onNavigateToReports: (() -> Unit)? = null,
    session: RtcSession? = null,
    openComposerOnEntry: Boolean = false,
    communityViewModel: CommunityViewModel = hiltViewModel(),
) {
    val feedState by communityViewModel.feedState.collectAsStateWithLifecycle()
    val pendingLikeIds by communityViewModel.pendingLikeIds.collectAsStateWithLifecycle()
    val pendingPostIds by communityViewModel.pendingPostIds.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Latest") }
    var searchQueryInput by rememberSaveable { mutableStateOf("") }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    var guidelinesOpen by rememberSaveable { mutableStateOf(false) }
    var resumeComposerAfterGuidelines by rememberSaveable { mutableStateOf(false) }
    var initialComposerHandled by rememberSaveable { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<CommunityPost?>(null) }
    var deletingPost by remember { mutableStateOf<CommunityPost?>(null) }
    val isPostWorking = communityActionUi.action == CommunityAction.POST && communityActionUi.isWorking

    fun requestPost() {
        if (guidelinesAccepted == true) {
            composerOpen = true
        } else {
            resumeComposerAfterGuidelines = true
            guidelinesOpen = true
        }
    }

    LaunchedEffect(openComposerOnEntry) {
        if (openComposerOnEntry && !initialComposerHandled) {
            initialComposerHandled = true
            requestPost()
        }
    }

    LaunchedEffect(communityActionUi.action, communityActionUi.isSuccess, guidelinesAccepted) {
        if (communityActionUi.action == CommunityAction.POST && communityActionUi.isSuccess) {
            composerOpen = false
            communityViewModel.refresh()
        }
        if ((communityActionUi.action == CommunityAction.GUIDELINES && communityActionUi.isSuccess) ||
            (guidelinesOpen && guidelinesAccepted == true)
        ) {
            guidelinesOpen = false
            if (resumeComposerAfterGuidelines) {
                resumeComposerAfterGuidelines = false
                composerOpen = true
            }
        }
    }

    val displayPosts = when {
        feedState.searchResults != null -> feedState.searchResults ?: emptyList()
        tab == "Trending" -> feedState.items.sortedByDescending { it.trendingScore }
        tab == "Saved" -> feedState.items.filter { it.isBookmarkedByViewer }
        else -> feedState.items
    }
    val initialError = feedState.initialError

    ResidentPullToRefresh(
        isRefreshing = feedState.refreshing || feedState.publicReportsLoading,
        onRefresh = communityViewModel::refresh,
        modifier = Modifier.testTag("community_feed_pull_refresh"),
    ) {
        RtcScreenScaffold(
            density = RtcContentDensity.FEED_CONTENT,
            floatingActionButton = {
                RtcMotionFab(visible = !feedState.initialLoading) {
                    ExtendedFloatingActionButton(
                        onClick = ::requestPost,
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        text = { Text("Post") },
                    )
                }
            },
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .parallaxHeader(rate = 0.35f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("Community", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("What is happening near you", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RtcStatusChip("Local feed", RtcStatusTone.SUCCESS)
                }
            }
            item {
                OutlinedTextField(
                    value = searchQueryInput,
                    onValueChange = { query ->
                        searchQueryInput = query
                        if (query.isBlank()) {
                            communityViewModel.clearSearch()
                        } else {
                            communityViewModel.searchPosts(query)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search community posts, tags, authors…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQueryInput.isNotBlank()) {
                            IconButton(onClick = {
                                searchQueryInput = ""
                                communityViewModel.clearSearch()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    AssistChip(
                        onClick = {
                            tab = "Latest"
                            if (feedState.searchResults != null) {
                                searchQueryInput = ""
                                communityViewModel.clearSearch()
                            }
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Latest")
                                if (feedState.unreadCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.error,
                                    ) {
                                        Text(
                                            text = "${feedState.unreadCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        },
                        colors = if (tab == "Latest" && feedState.searchResults == null) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors(),
                    )
                    AssistChip(
                        onClick = {
                            tab = "Trending"
                            if (feedState.searchResults != null) {
                                searchQueryInput = ""
                                communityViewModel.clearSearch()
                            }
                        },
                        label = { Text("Trending") },
                        colors = if (tab == "Trending" && feedState.searchResults == null) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors(),
                    )
                    AssistChip(
                        onClick = {
                            tab = "Saved"
                            if (feedState.searchResults != null) {
                                searchQueryInput = ""
                                communityViewModel.clearSearch()
                            }
                        },
                        label = { Text("Saved") },
                        colors = if (tab == "Saved" && feedState.searchResults == null) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors(),
                    )
                }
            }
            if (feedState.unreadCount > 0 || feedState.hasNewPosts) {
                item {
                    Surface(
                        onClick = {
                            communityViewModel.markAllPostsAsRead()
                            communityViewModel.refresh()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("community_unread_updates_badge"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (feedState.unreadCount > 0) {
                                        "${feedState.unreadCount} new update${if (feedState.unreadCount > 1) "s" else ""} since last visit • Tap to view"
                                    } else {
                                        "New posts published • Tap to refresh"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            RtcStatusChip("Mark read", RtcStatusTone.PROTECTED)
                        }
                    }
                }
            }
            communityActionUi.message?.let { message ->
                item { CommunityActionFeedback(message = message, isError = !communityActionUi.isSuccess, onDismiss = onDismissCommunityMessage) }
            }
            feedState.mutationError?.let { message ->
                item { CommunityActionFeedback(message = message, isError = true, onDismiss = communityViewModel::dismissMutationError) }
            }
            feedState.appendError?.let { message ->
                item { CommunityActionFeedback(message = message, isError = true, onDismiss = communityViewModel::dismissAppendError) }
            }
            draft?.let { savedDraft ->
                item { ContinueDraftCard(draft = savedDraft, onResume = { requestPost() }, onDiscard = onDiscardDraft) }
            }
            item {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .parallaxScrollItem(index = 5, rate = 0.05f)
                ) {
                    ComposerCard(onClick = ::requestPost)
                }
            }
            if (feedState.publicReports.isNotEmpty() || feedState.publicReportsDashboard != null) {
                item {
                    CommunityPublicReportsHighlightCard(
                        reports = feedState.publicReports,
                        dashboard = feedState.publicReportsDashboard,
                        isLoading = feedState.publicReportsLoading,
                        onRefresh = communityViewModel::refresh,
                        onOpenReport = onOpenReport,
                        onViewAll = onNavigateToReports,
                    )
                }
            }
            if (feedState.initialLoading && displayPosts.isEmpty()) {
                items(3) {
                    PostCardSkeleton()
                }
            } else if (initialError != null && displayPosts.isEmpty()) {
                item {
                    RtcEmptyState(
                        title = "Community unavailable",
                        message = initialError,
                        actionLabel = "Retry",
                        onAction = communityViewModel::refresh,
                    )
                }
            } else if (displayPosts.isEmpty()) {
                item {
                    CommunityFeedEmptyState(
                        selectedTab = tab,
                        isRefreshing = feedState.refreshing,
                        onCreatePost = ::requestPost,
                        onRefresh = communityViewModel::refresh,
                        onSwitchToLatest = { tab = "Latest" },
                    )
                }
            }
            itemsIndexed(displayPosts, key = { _, post -> post.id }) { index, post ->
                if (index >= displayPosts.lastIndex - 2 && feedState.hasMore && !feedState.appendLoading && feedState.appendError == null) {
                    LaunchedEffect(post.id, feedState.nextCursor) { communityViewModel.loadNextPage() }
                }
                // The server is authoritative, and the UI must not infer ownership
                // from mutable display names, handles, or staff roles.
                val isPostOwner = session != null && post.authorId == session.id
                val isUnread = post.id in feedState.unreadPostIds
                CommunityPostCard(
                    post = post,
                    readingMode = readingMode,
                    onOpenPost = { clickedPost ->
                        communityViewModel.markPostAsRead(clickedPost.id)
                        onOpenPost(clickedPost)
                    },
                    onToggleLike = communityViewModel::toggleLike,
                    onToggleReaction = communityViewModel::toggleReaction,
                    onRepost = communityViewModel::repostPost,
                    onBookmark = communityViewModel::toggleBookmark,
                    onSharePost = onSharePost,
                    onEditPost = { editingPost = it },
                    onDeletePost = { postId -> deletingPost = post },
                    canEdit = isPostOwner && !post.isLocked && isPostWithinEditWindow(post.createdAt),
                    canDelete = isPostOwner,
                    onRefreshMediaUrl = communityViewModel::refreshMediaUrl,
                    isLikePending = post.id in pendingLikeIds,
                    isUnread = isUnread,
                    syntheticAvatarRes = if (useSyntheticPortraits && post.authorAvatarUrl == null) syntheticAvatarResource(post.author) else null,
                    modifier = Modifier.parallaxScrollItem(index = index + 6, rate = 0.06f),
                )
            }
            if (feedState.hasMore || feedState.appendLoading || feedState.appendError != null) {
                item(key = "community-load-more") {
                    Button(
                        onClick = communityViewModel::loadNextPage,
                        enabled = feedState.hasMore && !feedState.appendLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (feedState.appendLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(RtcSize.inlineIcon), strokeWidth = RtcStroke.emphasis)
                            Spacer(Modifier.width(RtcSpacing.compact))
                        }
                        Text(if (feedState.appendLoading) "Loading more…" else if (feedState.appendError != null) "Retry loading more" else "Load more posts")
                    }
                }
            }
        }
    }

    if (composerOpen) {
        PostComposer(
            draft = draft,
            onDismiss = { composerOpen = false },
            isSubmitting = isPostWorking,
            onSubmit = onCreatePost,
            onSaveDraft = { onSaveDraft(it) },
            onDiscardDraft = { onDiscardDraft() },
        )
    }
    if (guidelinesOpen) {
        CommunityGuidelinesDialog(
            isCheckingStatus = guidelinesAccepted == null,
            isAccepting = communityActionUi.action == CommunityAction.GUIDELINES && communityActionUi.isWorking,
            onDismiss = {
                guidelinesOpen = false
                resumeComposerAfterGuidelines = false
            },
            onAccept = onAcceptGuidelines,
        )
    }
    editingPost?.let { post ->
        CommunityPostEditorDialog(
            post = post,
            saving = post.id in pendingPostIds,
            onDismiss = { if (post.id !in pendingPostIds) editingPost = null },
            onSave = { body ->
                communityViewModel.updatePost(post, body) { editingPost = null }
            },
        )
    }
    deletingPost?.let { post ->
        AlertDialog(
            onDismissRequest = { if (post.id !in pendingPostIds) deletingPost = null },
            title = { Text("Delete post?") },
            text = { Text("This permanently removes the post, its comments, reactions, media records, and feed entries. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { communityViewModel.deletePost(post.id) { deletingPost = null } },
                    enabled = post.id !in pendingPostIds,
                ) { Text(if (post.id in pendingPostIds) "Deleting…" else "Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { deletingPost = null }, enabled = post.id !in pendingPostIds) { Text("Cancel") } },
        )
    }
}

internal fun isPostWithinEditWindow(createdAt: String): Boolean = runCatching {
    java.time.Instant.parse(createdAt).isAfter(java.time.Instant.now().minusSeconds(60 * 60))
}.getOrDefault(false)

@Composable
private fun ComposerCard(onClick: () -> Unit) {
    RtcCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(RtcSize.avatarStandard)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RtcSize.actionIcon)) }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text("What's happening in your community?", style = MaterialTheme.typography.bodyLarge)
                Text("Add a photo or video when you post", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard)) {
            Text("Photo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Video", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Community guidelines apply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CommunityActionFeedback(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(RtcSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(RtcSpacing.compact))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
internal fun CommunityGuidelinesDialog(
    isCheckingStatus: Boolean,
    isAccepting: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isAccepting) onDismiss() },
        title = { Text("Community Guidelines") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text("Before posting or commenting, please agree to these Community Guidelines.")
                Text("• Be respectful and do not harass, threaten, or target others.")
                Text("• Do not share personal case, health, contact, or other sensitive information.")
                Text("• Share only material you have the right to use, and report safety concerns instead of escalating them.")
                Text(
                    if (isCheckingStatus) {
                        "We are checking whether you have already accepted this version. You may still continue; the server will safely save or confirm your acknowledgement."
                    } else {
                        "Your acknowledgement is saved once for this guideline version and can be reviewed later from Community."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = !isAccepting,
            ) { Text(if (isAccepting) "Saving…" else "I agree and continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAccepting) { Text("Not now") }
        },
    )
}

@Composable
internal fun CommunityReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ModerationReason, String) -> Unit,
) {
    var selectedReason by rememberSaveable { mutableStateOf(ModerationReason.SPAM) }
    var detail by rememberSaveable { mutableStateOf("") }
    val detailIsRequired = selectedReason == ModerationReason.OTHER

    RtcMotionAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Community post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text("Choose the reason that best describes the concern. A moderator reviews every report before a final decision.")
                ModerationReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { selectedReason = reason }
                            .padding(vertical = RtcSpacing.tiny),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                        )
                        Text(reason.label)
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(500) },
                    label = { Text(if (detailIsRequired) "Details" else "Additional details (optional)") },
                    supportingText = { Text("Do not include unnecessary private information · ${detail.length}/500") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, detail.trim()) },
                enabled = !detailIsRequired || detail.trim().length >= 3,
            ) { Text("Send report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CommunityPublicReportsHighlightCard(
    reports: List<PublicReport>,
    dashboard: PublicReportDashboard?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenReport: (String) -> Unit,
    onViewAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("public_reports_highlight_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Public Reports",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        RtcStatusChip("Civic Watch", RtcStatusTone.PROTECTED)
                    }
                    Text(
                        text = "Verified neighborhood reports • Pull feed to re-fetch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("public_reports_refresh_button"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh public reports",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (dashboard != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RtcStatusChip("${dashboard.verifiedReports} Verified", RtcStatusTone.SUCCESS)
                    RtcStatusChip("${dashboard.activeReports} Active", RtcStatusTone.PROTECTED)
                    RtcStatusChip("${dashboard.resolvedReports} Resolved", RtcStatusTone.NEUTRAL)
                }
            }

            if (reports.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reports.take(2).forEach { report ->
                        Surface(
                            onClick = { onOpenReport(report.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("public_report_item_${report.id}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = report.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                        if (report.verified) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Verified report",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = report.categoryLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        if (report.publicLocationLabel.isNotBlank()) {
                                            Text(
                                                text = "• ${report.publicLocationLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                val statusTone = when (report.urgency) {
                                    PublicReportUrgency.CRITICAL, PublicReportUrgency.HIGH -> RtcStatusTone.DANGER
                                    else -> RtcStatusTone.NEUTRAL
                                }
                                RtcStatusChip(report.status.name.replace("_", " "), statusTone)
                            }
                        }
                    }
                }
            }

            if (onViewAll != null) {
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("public_reports_view_all_button"),
                ) {
                    Text("View all public reports")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
