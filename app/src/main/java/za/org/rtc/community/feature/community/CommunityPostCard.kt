
package za.org.rtc.// Import added here
import za.org.rtc.community.ui.components.RtcEnterpriseImage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .clickable { onPostClick() },
        colors = CardDefaults.cardColors(containerColor = RtcDesignSystem.SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.border(1.dp, RtcDesignSystem.AccentBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Author Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RtcDesignSystem.PrimaryBrand),
                contentAlignment = Alignment.Center
            ) {
                Text(post.authorName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.weight(1f)) {
                // Header: Name and Time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = RtcDesignSystem.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "• 2h",
                        style = MaterialTheme.typography.bodySmall,
                        color = RtcDesignSystem.TextSecondary
                    )
                }

                // Body Content
                Text(
                    text = post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RtcDesignSystem.TextPrimary,
                    modifier = Modifier.padding(vertical = 4.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Media Placeholder
                if (post.mediaUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Image Loading...", color = RtcDesignSystem.TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Engagement Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EngagementButton(Icons.Default.Favorite, post.likeCount.toString(), onLikeClick, if (post.isLiked) RtcDesignSystem.PrimaryBrand else RtcDesignSystem.TextSecondary)
                    EngagementButton(Icons.Default.ChatBubble, post.replyCount.toString(), onCommentClick, RtcDesignSystem.TextSecondary)
                    EngagementButton(Icons.Default.Repeat, "0", onRepostClick, RtcDesignSystem.TextSecondary)
                }
            }
        }
    }
}

@Composable
fun EngagementButton(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String, onClick: () -> Unit, color: Color) {
    Row(
        modifier = Modifier.clickable { onClick() }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        Text(count, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
