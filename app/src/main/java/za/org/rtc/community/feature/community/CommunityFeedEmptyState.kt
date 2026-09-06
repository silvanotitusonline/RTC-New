package za.org.rtc.community.feature.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import za.org.rtc.community.R
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcCivicGold
import za.org.rtc.community.ui.theme.RtcRadius
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Visually engaging empty state view displayed when the community feed contains no posts
 * after a refresh or filter application.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityFeedEmptyState(
    selectedTab: String,
    isRefreshing: Boolean,
    onCreatePost: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchToLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTrending = selectedTab == "Trending"

    val title = if (isTrending) {
        "No trending discussions yet"
    } else {
        "All quiet in the community"
    }

    val subtitle = if (isTrending) {
        "Discussions with active comments and resident likes will surface here. Switch to the Latest tab to see all recent activity, or start a new thread."
    } else {
        "The community feed is refreshed and up to date! There are no recent posts on the board. Share local announcements, ask a question, or introduce yourself."
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(350)) + scaleIn(initialScale = 0.95f),
        modifier = modifier.testTag("community_feed_empty_state"),
    ) {
        Card(
            shape = RoundedCornerShape(RtcRadius.large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = RtcSpacing.small),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RtcSpacing.standard),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
            ) {
                // Header Illustration with overlay badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RtcRadius.medium))
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_feed),
                        contentDescription = "Illustration of serene community neighborhood",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .testTag("empty_state_illustration"),
                    )

                    // Subtle bottom gradient vignette
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f),
                                    ),
                                    startY = 100f,
                                ),
                            ),
                    )

                    // Status pill at top-end corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(RtcSpacing.compact),
                    ) {
                        RtcStatusChip(
                            text = if (isRefreshing) "Refreshing…" else if (isTrending) "Quiet topic" else "All caught up",
                            tone = if (isRefreshing) RtcStatusTone.PROTECTED else RtcStatusTone.SUCCESS,
                        )
                    }
                }

                // Typography Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
                    modifier = Modifier.padding(horizontal = RtcSpacing.compact),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Suggested conversation starters
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Quick conversation starters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TopicStarterChip(
                            icon = Icons.Filled.WavingHand,
                            label = "Say hello",
                            onClick = onCreatePost,
                        )
                        TopicStarterChip(
                            icon = Icons.Filled.Campaign,
                            label = "Local update",
                            onClick = onCreatePost,
                        )
                        TopicStarterChip(
                            icon = Icons.Filled.HelpOutline,
                            label = "Ask neighbors",
                            onClick = onCreatePost,
                        )
                    }
                }

                // Interactive Primary and Secondary Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onCreatePost,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RtcSize.minimumTouchTarget)
                            .testTag("empty_state_create_post_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(RtcSize.actionIcon),
                        )
                        Spacer(modifier = Modifier.width(RtcSpacing.compact))
                        Text("Create first post", fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        if (isTrending) {
                            OutlinedButton(
                                onClick = onSwitchToLatest,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = RtcSize.minimumTouchTarget)
                                    .testTag("empty_state_switch_tab_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(RtcSize.inlineIcon),
                                )
                                Spacer(modifier = Modifier.width(RtcSpacing.compact))
                                Text("View Latest")
                            }
                        }

                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = RtcSize.minimumTouchTarget)
                                .testTag("empty_state_refresh_button"),
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(RtcSize.inlineIcon),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(RtcSize.inlineIcon),
                                )
                            }
                            Spacer(modifier = Modifier.width(RtcSpacing.compact))
                            Text(if (isRefreshing) "Refreshing…" else "Check again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicStarterChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .heightIn(min = 36.dp)
            .clickable(role = Role.Button, onClickLabel = "Start conversation: $label", onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.tiny),
            modifier = Modifier.padding(horizontal = RtcSpacing.small, vertical = RtcSpacing.tiny),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RtcCivicGold,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
