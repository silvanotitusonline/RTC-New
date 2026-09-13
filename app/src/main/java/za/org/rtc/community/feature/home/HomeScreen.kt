package za.org.rtc.community.feature.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import za.org.rtc.community.MainActivity
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.HomeSection
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.feature.dailypost.domain.DailyPost
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlock
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlockType
import za.org.rtc.community.feature.dailypost.domain.DailyPostType
import za.org.rtc.community.feature.dailypost.presentation.DailyPostViewModel
import za.org.rtc.community.ui.components.LocalLazyListState
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.parallaxHeader
import za.org.rtc.community.ui.components.parallaxScrollItem
import za.org.rtc.community.ui.config.HomeImageWidgetCard
import za.org.rtc.community.ui.config.HomeRenderItem
import za.org.rtc.community.ui.config.renderItems
import za.org.rtc.community.ui.home.HomeRouteContract
import za.org.rtc.community.ui.theme.RtcHomeDashboard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun HomeScreen(contract: HomeRouteContract) {
    val state = contract.state
    val actions = contract.actions
    val layout = state.layout
    val name = state.displayName
    val readingMode = state.readingMode
    val context = LocalContext.current
    val dailyPostViewModel: DailyPostViewModel = hiltViewModel()
    val dailyPostState by dailyPostViewModel.state.collectAsStateWithLifecycle()
    val resolvedLayout = remember(layout) { HomeLayout.validatedOrDefault(layout) }
    val renderItems = remember(resolvedLayout) { resolvedLayout.renderItems() }
    val upperHomeItems = remember(renderItems) {
        val quickAccessIndex = renderItems.indexOfFirst { item ->
            item is HomeRenderItem.Section && item.section == HomeSection.QUICK_ACCESS
        }
        val itemsBeforeFormerLowerHalf = if (quickAccessIndex >= 0) {
            renderItems.take(quickAccessIndex)
        } else {
            renderItems
        }
        itemsBeforeFormerLowerHalf.filter { item ->
            item is HomeRenderItem.Image ||
                item is HomeRenderItem.Section && item.section in setOf(
                    HomeSection.WELCOME,
                    HomeSection.COMMUNITY_SNAPSHOT,
                )
        }
    }
    val listState = rememberLazyListState()

    ResidentPullToRefresh(
        isRefreshing = state.isRefreshing || state.publicReports.refreshing || dailyPostState.loading,
        onRefresh = {
            actions.onRefresh()
            dailyPostViewModel.refresh()
        },
    ) {
        CompositionLocalProvider(LocalLazyListState provides listState) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(RtcHomeDashboard.pagePadding),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                upperHomeItems.forEach { renderItem ->
                    when (renderItem) {
                        is HomeRenderItem.Image -> item(key = "home_image_${renderItem.widget.assetId}") {
                            Box(modifier = Modifier.parallaxScrollItem(index = 0, rate = 0.05f)) {
                                HomeImageWidgetCard(renderItem.widget)
                            }
                        }

                        is HomeRenderItem.Section -> when (renderItem.section) {
                            HomeSection.WELCOME -> item(key = "home_welcome") {
                                Column(modifier = Modifier.parallaxHeader(rate = 0.35f)) {
                                    Text(
                                        "Welcome back, $name",
                                        style = if (readingMode) {
                                            MaterialTheme.typography.headlineMedium
                                        } else {
                                            MaterialTheme.typography.headlineSmall
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Community updates and support.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            HomeSection.COMMUNITY_SNAPSHOT -> item(key = "home_community_snapshot") {
                                Column(
                                    modifier = Modifier.parallaxScrollItem(index = 1, rate = 0.05f),
                                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                                ) {
                                    HomeCommunityStatusCard(
                                        state = state.publicReports,
                                        onOpenScope = actions.onOpenPublicReportScope,
                                        onRetry = actions.onRefresh,
                                    )
                                    HomePostComposerCard(
                                        onCommunityPost = actions.onCommunityPostDraft,
                                        onPublicReport = actions.onPublicReportDraft,
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }
                }

                item(key = "home_daily_post_snapshot") {
                    DailyPostSnapshotSection(
                        items = dailyPostState.items,
                        loading = dailyPostState.loading,
                        message = dailyPostState.message,
                        onRetry = dailyPostViewModel::refresh,
                        onOpenPost = { postId -> openDailyPost(context, postId) },
                        onViewAll = { actions.onNavigate(MainDestination.EXPLORE) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyPostSnapshotSection(
    items: List<DailyPost>,
    loading: Boolean,
    message: String?,
    onRetry: () -> Unit,
    onOpenPost: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val latestPosts = remember(items) { items.take(2) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "The Daily Post",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "A quick snapshot of RTC's latest publications.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onViewAll) {
                Text("View all Daily Post")
            }
        }

        when {
            loading && latestPosts.isEmpty() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(RtcSpacing.standard),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("Loading the latest Daily Post publications…")
                    }
                }
            }

            latestPosts.isEmpty() && !message.isNullOrBlank() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(RtcSpacing.standard),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        Text(
                            "The Daily Post snapshot is temporarily unavailable.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onRetry) { Text("Try again") }
                    }
                }
            }

            latestPosts.isEmpty() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "No Daily Post publications are available yet.",
                        modifier = Modifier.padding(RtcSpacing.standard),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> latestPosts.forEach { post ->
                DailyPostSnapshotCard(post = post, onOpenPost = onOpenPost)
            }
        }
    }
}

@Composable
private fun DailyPostSnapshotCard(
    post: DailyPost,
    onOpenPost: (String) -> Unit,
) {
    val publishedLabel = remember(post.publishedAt) {
        post.publishedAt
            ?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm"))
            ?: "Recently published"
    }
    val summaryBlocks = remember(post.blocks) {
        post.blocks.filter { block ->
            block.text.isNotBlank() && block.type in setOf(
                DailyPostBlockType.PARAGRAPH,
                DailyPostBlockType.SUBHEADING,
                DailyPostBlockType.PULL_QUOTE,
                DailyPostBlockType.INFO_CALLOUT,
            )
        }.take(2)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPost(post.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(RtcSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PublicationTypeBadge(post.publicationType)
                Text(
                    publishedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                post.headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (post.excerpt.isNotBlank()) {
                Text(
                    post.excerpt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            summaryBlocks.forEach { block ->
                DailyPostSummaryBlock(block)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${post.commentCount} comment${if (post.commentCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Read full story →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PublicationTypeBadge(type: DailyPostType) {
    val isBreaking = type == DailyPostType.BREAKING
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isBreaking) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Text(
            if (isBreaking) "BREAKING" else "NEWS",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = if (isBreaking) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

@Composable
private fun DailyPostSummaryBlock(block: DailyPostBlock) {
    when (block.type) {
        DailyPostBlockType.SUBHEADING -> Text(
            block.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        DailyPostBlockType.PULL_QUOTE -> Text(
            "“${block.text}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        DailyPostBlockType.INFO_CALLOUT -> Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                block.text,
                modifier = Modifier.padding(RtcSpacing.compact),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        else -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun openDailyPost(context: Context, postId: String) {
    val safePostId = postId.takeIf { value ->
        value.length in 1..128 && value.all { it.isLetterOrDigit() || it in "-_" }
    } ?: return

    context.startActivity(
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_DAILY_POST)
            .putExtra(MainActivity.EXTRA_DAILY_POST_ID, safePostId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
}
