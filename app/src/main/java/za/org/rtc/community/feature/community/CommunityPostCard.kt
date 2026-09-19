package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.ui.components.RtcCommunityFeedCard

@Composable
fun CommunityPostCard(
    post: CommunityPost,
    readingMode: Boolean = false,
    onOpenPost: (CommunityPost) -> Unit = {},
    onToggleLike: (String) -> Unit = {},
    onToggleReaction: (String, String) -> Unit = { _, _ -> },
    onRepost: (String) -> Unit = {},
    onBookmark: (String) -> Unit = {},
    onSharePost: (CommunityPost) -> Unit = {},
    onEditPost: ((CommunityPost) -> Unit)? = null,
    onDeletePost: ((String) -> Unit)? = null,
    canEdit: Boolean = false,
    canDelete: Boolean = false,
    onRefreshMediaUrl: (suspend (String) -> String?)? = null,
    isLikePending: Boolean = false,
    syntheticAvatarRes: Int? = null,
    isUnread: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val timestampLabel = relativeTimeLabel(post.createdAt)
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    
    RtcCommunityFeedCard(
        post = post,
        onOpen = { onOpenPost(post) },
        syntheticAvatarRes = syntheticAvatarRes,
        timestampLabel = timestampLabel,
        isUnread = isUnread,
        headerTrailing = {
            if ((canEdit && onEditPost != null) || (canDelete && onDeletePost != null)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canEdit && onEditPost != null) {
                        IconButton(onClick = { onEditPost(post) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit post")
                        }
                    }
                    if (canDelete && onDeletePost != null) {
                        IconButton(onClick = { onDeletePost(post.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete post")
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        if (post.media.isNotEmpty()) {
            CommunityMediaPreview(
                media = post.media,
                onOpen = { onOpenPost(post) },
                onRefreshMediaUrl = onRefreshMediaUrl ?: { null },
            )
        }
        if (!readingMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            scale.animateTo(0.7f, tween(100))
                            scale.animateTo(1.2f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                        onToggleLike(post.id)
                    },
                    enabled = !isLikePending
                ) {
                    Icon(
                        if (post.viewerHasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (post.viewerHasLiked) "Unlike" else "Like",
                        modifier = Modifier.scale(scale.value)
                    )
                }
                IconButton(onClick = { onSharePost(post) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share post")
                }
            }
        }
    }
}

fun syntheticAvatarResource(author: String): Int? = null
