package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onDeletePost: ((String) -> Unit)? = null,
    canDelete: Boolean = false,
    onRefreshMediaUrl: (suspend (String) -> String?)? = null,
    isLikePending: Boolean = false,
    syntheticAvatarRes: Int? = null,
    modifier: Modifier = Modifier,
) {
    val timestampLabel = relativeTimeLabel(post.createdAt)
    RtcCommunityFeedCard(
        post = post,
        onOpen = { onOpenPost(post) },
        syntheticAvatarRes = syntheticAvatarRes,
        timestampLabel = timestampLabel,
        headerTrailing = {
            if (canDelete && onDeletePost != null) {
                IconButton(onClick = { onDeletePost(post.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete post")
                }
            }
        },
        modifier = modifier,
    ) {
        if (!readingMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleLike(post.id) }, enabled = !isLikePending) {
                    Icon(
                        if (post.viewerHasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (post.viewerHasLiked) "Unlike" else "Like"
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
