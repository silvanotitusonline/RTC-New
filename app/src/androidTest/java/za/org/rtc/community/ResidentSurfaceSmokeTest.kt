package za.org.rtc.community

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import za.org.rtc.community.app.CommunityActionUiState
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.feature.community.CommunityCursor
import za.org.rtc.community.feature.community.CommunityFeedPage
import za.org.rtc.community.feature.community.CommunityLikeOutcome
import za.org.rtc.community.feature.community.CommunityRepository
import za.org.rtc.community.feature.community.CommunityScreen
import za.org.rtc.community.feature.community.CommunityViewModel
import za.org.rtc.community.feature.explore.ExploreScreen
import za.org.rtc.community.feature.home.HomeScreen
import za.org.rtc.community.feature.home.HomePublicReportState
import za.org.rtc.community.feature.support.SupportScreen
import za.org.rtc.community.ui.components.RtcResidentBottomNavigation
import za.org.rtc.community.ui.components.RtcResidentNavItem
import za.org.rtc.community.ui.home.HomeRouteActions
import za.org.rtc.community.ui.home.HomeRouteContract
import za.org.rtc.community.ui.home.HomeRouteState
import za.org.rtc.community.ui.navigation.navItems
import za.org.rtc.community.ui.theme.RtcCommunityTheme

/**
 * Network-free rendering smoke coverage for the resident primary surfaces and navigation chrome.
 *
 * These tests deliberately use deterministic local state and never construct a production
 * repository, Supabase client, or backend session. Community uses a small in-memory repository
 * so the real scoped CommunityViewModel ownership path is compiled and can render safely.
 */
class ResidentSurfaceSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeSurfaceRendersWithLocalState() {
        composeRule.setContent {
            RtcCommunityTheme(ThemePreference.LIGHT) {
                HomeScreen(
                    HomeRouteContract(
                        state = HomeRouteState(
                            layout = HomeLayout.default(),
                            displayName = "Smoke Resident",
                            cases = emptyList(),
                            notices = emptyList(),
                            readingMode = false,
                            draft = null,
                            pendingSyncCount = 0,
                            publicReports = HomePublicReportState(),
                            isRefreshing = false,
                        ),
                        actions = HomeRouteActions(
                            onRefresh = {},
                            onNavigate = {},
                            onOpenDirectory = {},
                            onOpenNotice = {},
                            onResumeDraft = {},
                            onDiscardDraft = {},
                            onHelp = {},
                            onOpenPublicReportScope = {},
                            onCommunityPostDraft = {},
                            onPublicReportDraft = {},
                        ),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Welcome back, Smoke Resident").assertIsDisplayed()
    }

    @Test
    fun communitySurfaceRendersWithLocalState() {
        val communityViewModel = CommunityViewModel(SmokeCommunityRepository)
        composeRule.setContent {
            RtcCommunityTheme(ThemePreference.LIGHT) {
                CommunityScreen(
                    readingMode = false,
                    useSyntheticPortraits = false,
                    draft = null,
                    communityActionUi = CommunityActionUiState(),
                    guidelinesAccepted = true,
                    onAcceptGuidelines = {},
                    onDismissCommunityMessage = {},
                    onCreatePost = { _, _ -> },
                    onSaveDraft = {},
                    onDiscardDraft = {},
                    onOpenPost = {},
                    onSharePost = {},
                    communityViewModel = communityViewModel,
                )
            }
        }
        composeRule.onNodeWithText("Community").assertIsDisplayed()
    }

    @Test
    fun exploreSurfaceRendersWithLocalState() {
        composeRule.setContent {
            RtcCommunityTheme(ThemePreference.LIGHT) {
                ExploreScreen(
                    projects = emptyList(),
                    opportunities = emptyList(),
                    notices = emptyList(),
                    isRefreshing = false,
                    onRefresh = {},
                    onOpenDirectory = {},
                )
            }
        }
        composeRule.onNodeWithText("Explore").assertIsDisplayed()
    }

    @Test
    fun supportSurfaceRendersWithLocalState() {
        composeRule.setContent {
            RtcCommunityTheme(ThemePreference.LIGHT) {
                SupportScreen(
                    cases = emptyList(),
                    draft = null,
                    isRefreshing = false,
                    onRefresh = {},
                    onSubmit = { _, _ -> },
                    onSaveDraft = { _, _ -> },
                    onDiscardDraft = {},
                    onOpenCase = {},
                    onOpenCentres = {},
                )
            }
        }
        composeRule.onNodeWithText("How can we help?").assertIsDisplayed()
    }

    @Test
    fun fiveDestinationResidentChromeRendersWithoutLegacySupportTab() {
        composeRule.setContent {
            RtcCommunityTheme(ThemePreference.LIGHT) {
                RtcResidentBottomNavigation(
                    selected = MainDestination.HOME,
                    items = navItems.map { RtcResidentNavItem(it.destination, it.label, it.inactiveIcon) },
                    onNavigate = {},
                )
            }
        }

        listOf("Home", "Community", "Explore", "Market", "Account").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Support").assertDoesNotExist()
    }

    private object SmokeCommunityRepository : CommunityRepository {
        override suspend fun loadFeedPage(cursor: CommunityCursor?, limit: Int): Result<CommunityFeedPage> =
            Result.success(CommunityFeedPage(items = emptyList(), nextCursor = null, hasMore = false))

        override suspend fun loadPost(postId: String): Result<CommunityPost?> = Result.success(null)

        override suspend fun loadComments(postId: String): Result<List<CommunityComment>> = Result.success(emptyList())

        override suspend fun createComment(postId: String, body: String, parentId: String?): Result<Unit> = Result.success(Unit)

        override suspend fun updateComment(commentId: String, body: String): Result<Unit> = Result.success(Unit)

        override suspend fun deleteComment(commentId: String): Result<Unit> = Result.success(Unit)

        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)

        override suspend fun moderateComment(commentId: String, reason: String): Result<Unit> = Result.success(Unit)

        override suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome> =
            Result.success(CommunityLikeOutcome(liked = true, reactionCount = 1))

        override suspend fun toggleReaction(postId: String, emoji: String): Result<CommunityLikeOutcome> =
            Result.success(CommunityLikeOutcome(liked = true, reactionCount = 1))

        override suspend fun repostPost(postId: String): Result<Pair<Boolean, Int>> = Result.success(true to 1)

        override suspend fun toggleBookmark(postId: String): Result<Pair<Boolean, Int>> = Result.success(true to 1)

        override suspend fun searchPosts(query: String, lastRank: Float?, lastId: String?, limit: Int): Result<List<CommunityPost>> = Result.success(emptyList())

        override suspend fun getHashtagAutocomplete(prefix: String): Result<List<String>> = Result.success(emptyList())

        override suspend fun getMentionAutocomplete(prefix: String): Result<List<String>> = Result.success(emptyList())

        override suspend fun refreshMediaUrl(mediaId: String): Result<String?> = Result.success(null)

        override fun observeNotificationEvents(userId: String): kotlinx.coroutines.flow.Flow<za.org.rtc.community.core.CommunityRealtimeNotification> =
            kotlinx.coroutines.flow.emptyFlow()

        override fun observeCommunityFeedRealtime(): kotlinx.coroutines.flow.Flow<String> =
            kotlinx.coroutines.flow.emptyFlow()

        override fun observeCachedPosts(): kotlinx.coroutines.flow.Flow<List<CommunityPost>> =
            kotlinx.coroutines.flow.emptyFlow()
    }
}
