package za.org.rtc.community.feature.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.DailyPostPresets
import za.org.rtc.community.feature.dailypost.ui.DailyPostTemplateCard
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.ui.animation.RtcMotionPatterns
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExploreScreen(
    projects: List<ProjectRecord>,
    opportunities: List<OpportunityRecord>,
    notices: List<OfficialNotice>,
    dailyPosts: List<DailyPostArticle> = emptyList(),
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onOpenDailyPost: (DailyPostArticle) -> Unit = {},
    onToggleDailyPostLike: (String) -> Unit = {},
    onOpenDailyPostStudio: () -> Unit = {},
    reports: List<PublicReport> = emptyList(),
    onOpenReport: (String) -> Unit = {},
    canManageDailyPosts: Boolean = false,
) {
    val publishedNoticeCount = notices.count { it.status == NoticeStatus.PUBLISHED }
    var activeTab by remember { mutableStateOf(0) }

    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item {
                RtcSectionHeader(
                    title = "Explore",
                    subtitle = "Notices, projects, opportunities, and official daily posts.",
                )
            }
            item {
                TabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Overview", style = MaterialTheme.typography.titleSmall) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Daily Posts", style = MaterialTheme.typography.titleSmall)
                                if (dailyPosts.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${dailyPosts.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            item {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = RtcMotionPatterns.lateralTransitionSpec(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount < -40 && activeTab == 0) {
                                    activeTab = 1
                                } else if (dragAmount > 40 && activeTab == 1) {
                                    activeTab = 0
                                }
                            }
                        },
                    label = "ExploreTabLateral",
                ) { tab ->
                    if (tab == 0) {
                        if (isRefreshing && projects.isEmpty() && opportunities.isEmpty() && notices.isEmpty()) {
                            ExploreSkeletonLoader()
                        } else if (projects.isEmpty() && opportunities.isEmpty() && notices.isEmpty() && reports.isEmpty()) {
                            ExploreLocalizedEmptyState(onRefresh = onRefresh)
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
                            ) {
                                RtcCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onOpenDirectory("notices") },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ExploreIcon(Icons.Filled.Campaign)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
                                        ) {
                                            Text("Community Notices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Read official updates published for the community.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        RtcStatusChip("$publishedNoticeCount published", RtcStatusTone.NEUTRAL)
                                    }
                                }
                                RtcCard(modifier = Modifier.fillMaxWidth()) {
                                    Text("Projects and Opportunities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Track current community work and find ways to participate.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ExploreActionRow(
                                        title = "Projects",
                                        subtitle = "Current community projects",
                                        count = "${projects.size} available",
                                        icon = Icons.Filled.Assignment,
                                        onClick = { onOpenDirectory("projects") },
                                    )
                                    ExploreActionRow(
                                        title = "Opportunities",
                                        subtitle = "Jobs, support, and learning",
                                        count = "${opportunities.size} open",
                                        icon = Icons.Filled.Assignment,
                                        onClick = { onOpenDirectory("opportunities") },
                                    )
                                }
                            }
                        }
                    } else {
                        DailyPostsExploreFeed(
                            articles = dailyPosts,
                            onOpenArticle = onOpenDailyPost,
                            onToggleLike = onToggleDailyPostLike,
                            onOpenStudio = onOpenDailyPostStudio,
                            canManage = canManageDailyPosts,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyPostsExploreFeed(
    articles: List<DailyPostArticle>,
    onOpenArticle: (DailyPostArticle) -> Unit,
    onToggleLike: (String) -> Unit,
    onOpenStudio: () -> Unit,
    canManage: Boolean,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember { listOf("All") + DailyPostPresets.Categories }

    val filteredArticles = remember(articles, searchQuery, selectedCategory) {
        articles.filter { article ->
            (selectedCategory == "All" || article.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() ||
             article.title.contains(searchQuery, ignoreCase = true) ||
             article.subtitle.contains(searchQuery, ignoreCase = true) ||
             article.content.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)
    ) {
        // Admin Banner / Studio shortcut if admin or always visible as shortcut
        if (canManage) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Admin Daily Post Studio",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Publish articles with Canva & Blog templates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Button(
                        onClick = onOpenStudio,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Post")
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Daily Posts...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories, key = { it }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Article Feed
        if (filteredArticles.isEmpty()) {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedCategory != "All") "No matching articles found" else "No Daily Posts Published Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedCategory != "All") "Try clearing your search or choosing another category filter." else "Administrator articles, official municipal updates, and Canva/Blog stories will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (canManage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onOpenStudio,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Daily Post")
                        }
                    }
                }
            }
        } else {
            filteredArticles.forEach { article ->
                DailyPostTemplateCard(
                    article = article,
                    onClick = { onOpenArticle(article) },
                    onToggleLike = { onToggleLike(article.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ExploreActionRow(
    title: String,
    subtitle: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RtcSize.minimumTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreIcon(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RtcStatusChip(count, RtcStatusTone.NEUTRAL)
    }
}

@Composable
private fun ExploreIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(RtcSize.avatarStandard),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RtcSize.actionIcon),
            )
        }
    }
}

@Composable
fun ExploreSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
    ) {
        repeat(3) {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(RtcSpacing.compact),
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(RtcSize.avatarStandard),
                    ) {}
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(0.6f).height(18.dp),
                        ) {}
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(0.85f).height(14.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreLocalizedEmptyState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RtcCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = RtcSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "No Community Updates Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "New notices, municipal projects, and local opportunities will appear here once published.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = RtcSpacing.standard),
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Check for Updates")
            }
        }
    }
}
