package za.org.rtc.community.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * The canonical Community post row.
 *
 * Two call shapes are supported deliberately:
 *  - Explore directory lists pass only (post, readingMode, onOpenPost).
 *  - The Community feed passes the full engagement contract by name.
 * Every parameter after onOpenPost therefore has a default.
 */
@Composable
internal fun CommunityPostCard(
    post: CommunityPost,
    readingMode: Boolean,
    onOpenPost: (CommunityPost) -> Unit,
    onToggleLike: (String) -> Unit = {},
    onToggleReaction: (String, String) -> Unit = { _, _ -> },
    onRepost: (String) -> Unit = {},
    onBookmark: (String) -> Unit = {},
    onSharePost: (CommunityPost) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    canDelete: Boolean = false,
    onRefreshMediaUrl: suspend (String) -> String? = { null },
    isLikePending: Boolean = false,
    syntheticAvatarRes: Int? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenPost(post) }
            .padding(horizontal = RtcSpacing.cardPadding, vertical = RtcSpacing.listGap),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
    ) {
        if (post.repostOfId != null) {
            Text(
                text = "Reposted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            PostAvatar(
                avatarUrl = post.authorAvatarUrl,
                fallbackRes = syntheticAvatarRes,
                displayName = post.author,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
            ) {
                AuthorRow(post = post)
                BodyText(post = post, readingMode = readingMode)

                if (post.media.isNotEmpty()) {
                    CommunityMediaPreview(
                        media = post.media,
                        onRefreshMediaUrl = onRefreshMediaUrl,
                    )
                }

                if (post.quotedPost != null) {
                    QuotedPost(post = post.quotedPost)
                }

                ActionBar(
                    post = post,
                    isLikePending = isLikePending,
                    canDelete = canDelete,
                    onOpenPost = onOpenPost,
                    onToggleLike = onToggleLike,
                    onRepost = onRepost,
                    onBookmark = onBookmark,
                    onSharePost = onSharePost,
                    onDeletePost = onDeletePost,
                )
            }
        }
    }
}

@Composable
private fun PostAvatar(avatarUrl: String?, fallbackRes: Int?, displayName: String) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val model: Any? = avatarUrl ?: fallbackRes
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = displayName + " profile photo",
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(post: CommunityPost) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
    ) {
        Text(
            text = post.author,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (post.handle.isNotBlank()) {
            Text(
                text = "@" + post.handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text(
            text = relativeLabel(post.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (post.isOfficial) {
            RtcStatusChip(text = "Official", tone = RtcStatusTone.PROTECTED)
        }
    }
}

@Composable
private fun BodyText(post: CommunityPost, readingMode: Boolean) {
    Text(
        text = post.content,
        style = if (readingMode) {
            MaterialTheme.typography.bodyLarge
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (readingMode) Int.MAX_VALUE else 8,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun QuotedPost(post: CommunityPost) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(RtcSpacing.small),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
        ) {
            Text(
                text = post.author + "  @" + post.handle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionBar(
    post: CommunityPost,
    isLikePending: Boolean,
    canDelete: Boolean,
    onOpenPost: (CommunityPost) -> Unit,
    onToggleLike: (String) -> Unit,
    onRepost: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onDeletePost: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = RtcSpacing.relatedText),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = Icons.Filled.ChatBubbleOutline,
            count = post.comments,
            enabled = true,
            active = false,
            onClick = { onOpenPost(post) },
        )
        ActionButton(
            icon = Icons.Filled.Repeat,
            count = post.repostCount,
            enabled = true,
            active = post.isRepostedByViewer,
            onClick = { onRepost(post.id) },
        )
        ActionButton(
            icon = if (post.viewerHasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            count = post.reactions,
            enabled = !isLikePending,
            active = post.viewerHasLiked,
            onClick = { onToggleLike(post.id) },
        )
        ActionButton(
            icon = if (post.isBookmarkedByViewer) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            count = post.bookmarkCount,
            enabled = true,
            active = post.isBookmarkedByViewer,
            onClick = { onBookmark(post.id) },
        )
        ActionButton(
            icon = Icons.Filled.Share,
            count = null,
            enabled = true,
            active = false,
            onClick = { onSharePost(post) },
        )
        if (canDelete) {
            ActionButton(
                icon = Icons.Filled.Delete,
                count = null,
                enabled = true,
                active = false,
                onClick = { onDeletePost(post.id) },
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    count: Int?,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.opticalCorrection),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = RtcSpacing.compact, vertical = RtcSpacing.tiny),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.45f),
        )
        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) tint else tint.copy(alpha = 0.45f),
            )
        }
    }
}

/**
 * Best-effort relative label for an ISO-8601 timestamp. Falls back to the date
 * portion rather than throwing on unexpected input.
 */
private fun relativeLabel(createdAt: String): String {
    val iso = createdAt
    if (iso.length < 10) return iso
    val minutes = runCatching {
        val instant = java.time.Instant.parse(iso)
        java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
    }.getOrNull() ?: return iso.substring(0, 10)

    return when {
        minutes < 1L -> "now"
        minutes < 60L -> minutes.toString() + "m"
        minutes < 1440L -> (minutes / 60L).toString() + "h"
        minutes < 10080L -> (minutes / 1440L).toString() + "d"
        else -> iso.substring(0, 10)
    }
}
