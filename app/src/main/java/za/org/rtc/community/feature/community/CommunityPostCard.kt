
package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun CommunityPostCard(
    post: CommunityPost,
    onPostClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onRepostClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RtcDesignSystem.FeedPadding, vertical = 8.dp)
            .clickable { onPostClick() },
        verticalAlignment = Alignment.Top
    ) {
        // Avatar Column
        AsyncImage(
            model = post.authorAvatar,
            contentDescription = "Profile",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Content Column
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.authorName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = RtcDesignSystem.TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "@${post.authorHandle} • ${post.timeAgo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RtcDesignSystem.TextSecondary
                )
            }

            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                color = RtcDesignSystem.TextPrimary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Media Section (Edge-to-Edge style)
            if (post.media.isNotEmpty()) {
                MediaGallery(post.media, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
            }

            // Global Action Bar (Standard Social Layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ActionButton(icon = Icons.Default.ChatBubble, count = post.commentCount, onClick = onCommentClick)
                ActionButton(icon = Icons.Default.Repeat, count = post.repostCount, onClick = onRepostClick)
                ActionButton(
                    icon = if (post.viewerHasLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    count = post.reactions,
                    active = post.viewerHasLiked,
                    onClick = onLikeClick
                )
                ActionButton(icon = Icons.Default.Bookmark, count = null, onClick = { /* Bookmark logic */ })
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, count: Int?, active: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) Color.Red else RtcDesignSystem.TextSecondary
        )
        if (count != null) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = RtcDesignSystem.TextSecondary
            )
        }
    }
}
