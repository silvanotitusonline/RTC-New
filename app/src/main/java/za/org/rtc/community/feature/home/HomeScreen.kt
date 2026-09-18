package za.org.rtc.community.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.HomeSection
import za.org.rtc.community.core.NoticeStatus
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import za.org.rtc.community.ui.components.LocalLazyListState
import za.org.rtc.community.ui.components.parallaxHeader
import za.org.rtc.community.ui.components.parallaxScrollItem
import za.org.rtc.community.ui.components.NoticeCard
import za.org.rtc.community.ui.components.PendingSyncIndicator
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.SectionHeader
import za.org.rtc.community.ui.config.HomeImageWidgetCard
import za.org.rtc.community.ui.config.HomeRenderItem
import za.org.rtc.community.ui.config.renderItems
import za.org.rtc.community.ui.home.HomeRouteContract
import za.org.rtc.community.ui.theme.RtcHomeDashboard
import za.org.rtc.community.ui.theme.RtcSpacing
import za.org.rtc.community.feature.support.ResidentAssistantCard
import za.org.rtc.community.feature.support.ResidentAssistantViewModel

@Composable
internal fun HomeScreen(contract: HomeRouteContract) {
    val state = contract.state
    val actions = contract.actions
    val layout = state.layout
    val name = state.displayName
    val notices = state.notices
    val readingMode = state.readingMode
    val draft = state.draft
    val pendingSyncCount = state.pendingSyncCount
    val onRefresh = actions.onRefresh
    val onOpenDirectory = actions.onOpenDirectory
    val onOpenNotice = actions.onOpenNotice
    val onResumeDraft = actions.onResumeDraft
    val onDiscardDraft = actions.onDiscardDraft
    val onHelp = actions.onHelp
    val resolvedLayout = remember(layout) { HomeLayout.validatedOrDefault(layout) }
    val renderItems = remember(resolvedLayout) { resolvedLayout.renderItems() }
    val listState = rememberLazyListState()
    val assistantViewModel: ResidentAssistantViewModel = hiltViewModel()
    val assistantState by assistantViewModel.state.collectAsStateWithLifecycle()
    ResidentPullToRefresh(
        isRefreshing = state.isRefreshing || state.publicReports.refreshing,
        onRefresh = onRefresh,
    ) {
        CompositionLocalProvider(LocalLazyListState provides listState) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(RtcHomeDashboard.pagePadding),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                renderItems.forEach { renderItem ->
                    when (renderItem) {
                        is HomeRenderItem.Image -> item(key = "home_image_${renderItem.widget.assetId}") {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.parallaxScrollItem(index = 0, rate = 0.05f)) {
                                HomeImageWidgetCard(renderItem.widget)
                            }
                        }
                        is HomeRenderItem.Section -> when (val section = renderItem.section) {
                            HomeSection.WELCOME -> item(key = "home_welcome") {
                                Column(modifier = Modifier.parallaxHeader(rate = 0.35f)) {
                                    Text("Welcome back, $name", style = if (readingMode) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Community updates and support.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HomeSection.COMMUNITY_SNAPSHOT -> item(key = "home_community_snapshot") {
                                Column(
                                    modifier = Modifier.parallaxScrollItem(index = 1, rate = 0.05f),
                                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
                                ) {
                                    HomeCommunityStatusCard(
                                        state = state.publicReports,
                                        onOpenScope = actions.onOpenPublicReportScope,
                                        onRetry = onRefresh,
                                    )
                                    HomePostComposerCard(
                                        onCommunityPost = actions.onCommunityPostDraft,
                                        onPublicReport = actions.onPublicReportDraft,
                                    )
                                }
                            }
                            HomeSection.QUICK_ACCESS -> item(key = "home_quick_access") {
                                androidx.compose.foundation.layout.Box(modifier = Modifier.parallaxScrollItem(index = 2, rate = 0.05f)) {
                                    ResidentAssistantCard(
                                        state = assistantState,
                                        onDraftChange = assistantViewModel::updateDraft,
                                        onAsk = assistantViewModel::ask,
                                    )
                                }
                            }
                        HomeSection.CONTINUE_DRAFT -> draft?.let { savedDraft ->
                            item(key = "home_continue_draft") {
                                ContinueDraftCard(draft = savedDraft, onResume = { onResumeDraft(savedDraft) }, onDiscard = { onDiscardDraft(savedDraft) })
                            }
                        }
                        HomeSection.PENDING_SYNC -> if (pendingSyncCount > 0) {
                            item(key = "home_pending_sync") {
                                PendingSyncIndicator(pendingSyncCount, "Local changes sync when connected.")
                            }
                        }
                        HomeSection.NEXT_STEPS -> Unit
                        HomeSection.LATEST_UPDATES -> {
                            item(key = "home_latest_updates_header") {
                                SectionHeader("Official updates", "View all") { onOpenDirectory("notices") }
                            }
                            items(notices.filter { it.status == NoticeStatus.PUBLISHED }.take(2), key = { "home_notice_${it.id}" }) {
                                NoticeCard(it, onClick = { onOpenNotice(it) })
                            }
                        }
                        HomeSection.HELP -> item(key = "home_help") {
                            FilledTonalButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, null)
                                Spacer(Modifier.width(RtcSpacing.compact))
                                Text("Help & FAQs")
                            }
                        }
                    }
                }
            }
        }
    }
}
}
