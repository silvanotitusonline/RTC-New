package za.org.rtc.community.feature.dailypost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.DailyPostComment
import za.org.rtc.community.feature.dailypost.domain.DailyPostTemplateStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyPostDetailScreen(
    article: DailyPostArticle?,
    onBack: () -> Unit,
    onToggleLike: (String) -> Unit,
    onShare: (DailyPostArticle) -> Unit,
    comments: List<DailyPostComment> = emptyList(),
    commentsLoading: Boolean = false,
    commentsHasMore: Boolean = false,
    liveUpdatesAvailable: Boolean = true,
    pendingCommentId: String? = null,
    currentUserId: String? = null,
    canModerateComments: Boolean = false,
    onRefreshComments: () -> Unit = {},
    onLoadOlderComments: () -> Unit = {},
    onCreateComment: (String, String?) -> Unit = { _, _ -> },
    onUpdateComment: (String, String) -> Unit = { _, _ -> },
    onDeleteComment: (String) -> Unit = {},
    onModerateComment: (String, String) -> Unit = { _, _ -> },
    onReportComment: (String, String, String) -> Unit = { _, _, _ -> },
) {
    if (article == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Daily Post") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Article not found or has been removed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val accentColor = remember(article.accentColorHex) { parseColorSafe(article.accentColorHex) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "The Daily Post",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(article) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Article")
                    }
                    IconButton(onClick = { onToggleLike(article.id) }) {
                        Icon(
                            imageVector = if (article.viewerHasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (article.viewerHasLiked) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Banner depending on Template Style
            when (article.templateStyle) {
                DailyPostTemplateStyle.CANVA_HERO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(accentColor, accentColor.copy(alpha = 0.85f))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "CANVA POST • ${article.category.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = Color.White)
                            )
                            if (article.subtitle.isNotBlank()) {
                                Text(
                                    text = article.subtitle,
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.9f))
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Published ${formatPublishedDate(article.publishedAtEpochMillis)} • ${article.readTimeMinutes} min read",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                            }
                        }
                    }
                }
                DailyPostTemplateStyle.BREAKING_BULLETIN -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accentColor)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(
                            text = "OFFICIAL BREAKING BULLETIN",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        )
                    }
                }
                DailyPostTemplateStyle.CIVIC_SPOTLIGHT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accentColor.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "MUNICIPAL CIVIC SPOTLIGHT",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                    color = accentColor
                                )
                                Text(
                                    text = "OFFICIAL DISPATCH FROM THE OFFICE OF THE ADMINISTRATOR",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                DailyPostTemplateStyle.MAGAZINE_STORY -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(accentColor)
                    )
                }
                else -> {
                    // Modern Blog & Minimalist
                }
            }

            // Article Body Content Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If not Canva hero (which already showed title in banner):
                if (article.templateStyle != DailyPostTemplateStyle.CANVA_HERO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = article.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "${article.readTimeMinutes} min read • ${formatPublishedDate(article.publishedAtEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (article.subtitle.isNotBlank()) {
                        Text(
                            text = article.subtitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Author Byline Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = article.authorName.take(1).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(article.authorName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                            }
                            Text(article.authorRole, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Pull Quote Callout Box (if available)
                article.quoteText?.takeIf { it.isNotBlank() }?.let { quote ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = quote,
                                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                article.quoteAuthor?.takeIf { it.isNotBlank() }?.let { author ->
                                    Text(
                                        text = "— $author",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accentColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Key Highlights / Takeaways
                if (article.keyHighlights.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "KEY HIGHLIGHTS & SUMMARY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                color = accentColor
                            )
                            article.keyHighlights.forEach { highlight ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Text(
                                        text = highlight,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Rich Full Article Body Content
                Text(
                    text = article.content,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bottom Engagement & Reactions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onToggleLike(article.id) },
                        colors = if (article.viewerHasLiked) {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        }
                    ) {
                        Icon(
                            imageVector = if (article.viewerHasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (article.viewerHasLiked) "Liked (${article.reactionsCount})" else "Like Article (${article.reactionsCount})")
                    }

                    Button(
                        onClick = { onShare(article) },
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (!liveUpdatesAvailable) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Live updates unavailable. Comments may be stale.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = onRefreshComments) { Text("Refresh") }
                        }
                    }
                }

                DailyPostCommentsSection(
                    articleId = article.id,
                    comments = comments,
                    totalCount = article.commentCount,
                    loading = commentsLoading,
                    hasMore = commentsHasMore,
                    pendingCommentId = pendingCommentId,
                    currentUserId = currentUserId,
                    canModerate = canModerateComments,
                    onRefresh = onRefreshComments,
                    onLoadOlder = onLoadOlderComments,
                    onCreate = onCreateComment,
                    onUpdate = onUpdateComment,
                    onDelete = onDeleteComment,
                    onModerate = onModerateComment,
                    onReport = onReportComment,
                )
            }
        }
    }
}
