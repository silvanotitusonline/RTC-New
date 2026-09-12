package za.org.rtc.community.feature.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.feature.dailypost.presentation.DailyPostScreen
import za.org.rtc.community.feature.publicreports.data.PublicReportMockData
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.ui.animation.RtcMotionPatterns
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Explore now has exactly two product surfaces:
 * 1. The Daily Post — RTC's official publication and breaking-news experience.
 * 2. Community Updates — the former Explore overview (notices, map, projects, opportunities).
 *
 * The legacy Events Calendar is intentionally absent from this surface and from application routing.
 */
@Composable
internal fun ExploreScreen(
    projects: List<ProjectRecord>,
    opportunities: List<OpportunityRecord>,
    notices: List<OfficialNotice>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    currentUserId: String? = null,
    initialDailyPostId: String? = null,
    reports: List<PublicReport> = PublicReportMockData.getSampleReports(),
    onOpenReport: (String) -> Unit = {},
) {
    var activeTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Explore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Official stories and practical community information.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TabRow(selectedTabIndex = activeTab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("The Daily Post", style = MaterialTheme.typography.titleSmall) },
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Community Updates", style = MaterialTheme.typography.titleSmall) },
            )
        }
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = RtcMotionPatterns.lateralTransitionSpec(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "ExplorePrimaryTabs",
        ) { tab ->
            if (tab == 0) {
                DailyPostScreen(
                    currentUserId = currentUserId,
                    initialPostId = initialDailyPostId,
                )
            } else {
                CommunityUpdatesScreen(
                    projects = projects,
                    opportunities = opportunities,
                    notices = notices,
                    reports = reports,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onOpenDirectory = onOpenDirectory,
                    onOpenReport = onOpenReport,
                )
            }
        }
    }
}

@Composable
private fun CommunityUpdatesScreen(
    projects: List<ProjectRecord>,
    opportunities: List<OpportunityRecord>,
    notices: List<OfficialNotice>,
    reports: List<PublicReport>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onOpenReport: (String) -> Unit,
) {
    val publishedNoticeCount = notices.count { it.status == NoticeStatus.PUBLISHED }
    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Community Updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "Notices, public-report map, projects and opportunities.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
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
            }
            item {
                InteractiveMunicipalCanvasMap(
                    verifiedReports = reports,
                    onOpenReport = onOpenReport,
                )
            }
            item {
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
                        icon = Icons.Filled.Article,
                        onClick = { onOpenDirectory("opportunities") },
                    )
                }
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
        shape = androidx.compose.foundation.shape.CircleShape,
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
