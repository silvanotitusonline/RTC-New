package za.org.rtc.community.ui.navigation

import android.content.Context
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.awaitCancellation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.ui.animation.LocalReducedMotion
import za.org.rtc.community.ui.animation.RtcMotionPatterns
import za.org.rtc.community.core.DraftArea
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.account.AccountScreen
import za.org.rtc.community.feature.account.NotificationsScreen
import za.org.rtc.community.feature.administration.AccessManagementScreen
import za.org.rtc.community.feature.administration.AdminActivityScreen
import za.org.rtc.community.feature.administration.AdminWorkspace
import za.org.rtc.community.feature.administration.AdministratorMfaVerificationScreen
import za.org.rtc.community.feature.administration.AdministratorPrivacyAnalyticsScreen
import za.org.rtc.community.feature.administration.AiAssistantScreen
import za.org.rtc.community.feature.administration.ContentManagementScreen
import za.org.rtc.community.feature.administration.ModerationDashboard
import za.org.rtc.community.feature.administration.MyWorkProfileScreen
import za.org.rtc.community.feature.administration.OperationalControlsScreen
import za.org.rtc.community.feature.administration.SystemHealthScreen
import za.org.rtc.community.feature.administration.branding.BrandExperienceScreen
import za.org.rtc.community.feature.alerts.CommunityAlertDetailScreen
import za.org.rtc.community.feature.alerts.CommunityAlertsScreen
import za.org.rtc.community.feature.alerts.StaffCommunityAlertsScreen
import za.org.rtc.community.feature.community.CommunityPostDetailScreen
import za.org.rtc.community.feature.community.CommunityScreen
import za.org.rtc.community.feature.communityhub.CommunityHubScreen
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.ui.DailyPostDetailScreen
import za.org.rtc.community.feature.dailypost.ui.DailyPostStudioScreen
import za.org.rtc.community.feature.dailypost.ui.DailyPostViewModel
import za.org.rtc.community.feature.explore.DirectoryRecordDetailScreen
import za.org.rtc.community.feature.explore.ExploreDirectoryScreen
import za.org.rtc.community.feature.explore.ExploreScreen
import za.org.rtc.community.feature.explore.NoticeDetailScreen
import za.org.rtc.community.feature.explore.SearchResultDetailScreen
import za.org.rtc.community.feature.explore.SearchScreen
import za.org.rtc.community.feature.home.HomeScreen
import za.org.rtc.community.feature.home.HomePublicReportViewModel
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.marketplace.presentation.*
import za.org.rtc.community.feature.servicecentre.presentation.*
import za.org.rtc.community.feature.support.HelpCentreScreen
import za.org.rtc.community.feature.support.SupportCaseDetailScreen
import za.org.rtc.community.feature.support.SupportScreen
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.CommunitySection
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.navigatePrimary
import za.org.rtc.community.navigation.returnToSafeWorkspace
import za.org.rtc.community.ui.home.HomeRouteActions
import za.org.rtc.community.ui.home.HomeRouteContract
import za.org.rtc.community.ui.home.HomeRouteState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RtcCommunityNavGraph(
    modifier: Modifier,
    navController: NavHostController,
    viewModel: RtcViewModel,
    session: RtcSession,
    context: Context,
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val cases by viewModel.cases.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val helpArticles by viewModel.helpArticles.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val communityGuidelinesAccepted by viewModel.communityGuidelinesAccepted.collectAsStateWithLifecycle()
    val communityAlerts by viewModel.communityAlerts.collectAsStateWithLifecycle()
    val communityAlertDetail by viewModel.communityAlertDetail.collectAsStateWithLifecycle()
    val communityAlertDashboard by viewModel.communityAlertDashboard.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val projectsPage by viewModel.projectsPage.collectAsStateWithLifecycle()
    val centresPage by viewModel.centresPage.collectAsStateWithLifecycle()
    val opportunitiesPage by viewModel.opportunitiesPage.collectAsStateWithLifecycle()
    val publicSearchResults by viewModel.publicSearchResults.collectAsStateWithLifecycle()
    val isLiveContentLoading by viewModel.isLiveContentLoading.collectAsStateWithLifecycle()
    val communityActionUi by viewModel.communityActionUi.collectAsStateWithLifecycle()
    val dailyPostViewModel: DailyPostViewModel = hiltViewModel()
    val dailyPosts by dailyPostViewModel.publishedArticles.collectAsStateWithLifecycle()
    val dailyPostComments by dailyPostViewModel.comments.collectAsStateWithLifecycle()
    val dailyPostCommentsLoading by dailyPostViewModel.commentsLoading.collectAsStateWithLifecycle()
    val dailyPostCommentPendingId by dailyPostViewModel.commentPendingId.collectAsStateWithLifecycle()
    val dailyPostCommentsHasMore by dailyPostViewModel.commentsHasMore.collectAsStateWithLifecycle()

    val reducedMotion = LocalReducedMotion.current

    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this
        ) {
            NavHost(
                modifier = Modifier,
                navController = navController,
                startDestination = if (session.role.isStaff) RtcRoute.OPERATIONS_HUB else RtcRoute.HOME,
                enterTransition = { RtcMotionPatterns.navigationEnterTransition(reducedMotion) },
                exitTransition = { RtcMotionPatterns.navigationExitTransition(reducedMotion) },
                popEnterTransition = { RtcMotionPatterns.navigationPopEnterTransition(reducedMotion) },
                popExitTransition = { RtcMotionPatterns.navigationPopExitTransition(reducedMotion) },
            ) {
        composable(RtcRoute.HOME) {
            val homePublicReportViewModel: HomePublicReportViewModel = hiltViewModel()
            val homePublicReports by homePublicReportViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { homePublicReportViewModel.load() }
            HomeScreen(
                HomeRouteContract(
                    state = HomeRouteState(
                        layout = HomeLayout.default(),
                        displayName = session.displayName,
                        cases = cases,
                        notices = notices,
                        readingMode = session.readingMode,
                        draft = drafts.firstOrNull(),
                        pendingSyncCount = pendingSyncCount,
                        publicReports = homePublicReports,
                        isRefreshing = isLiveContentLoading,
                        events = events,
                    ),
                    actions = HomeRouteActions(
                        onRefresh = {
                            viewModel.refreshLiveContent()
                            homePublicReportViewModel.refresh()
                        },
                        onNavigate = { navController.navigatePrimary(it.route()) },
                        onOpenDirectory = { directory -> navController.navigateOverlay(exploreDirectoryRoute(directory)) },
                        onOpenNotice = { notice -> navController.navigateOverlay(noticeRoute(notice.id)) },
                        onResumeDraft = { draft ->
                            navController.navigatePrimary(
                                when (draft.area) {
                                    DraftArea.COMMUNITY, DraftArea.COMMUNITY_COMMENT -> RtcRoute.COMMUNITY
                                    DraftArea.NOTICE -> RtcRoute.EXPLORE
                                    DraftArea.SUPPORT -> RtcRoute.SUPPORT
                                    DraftArea.STAFF_CONTENT, DraftArea.STAFF_MODERATION -> RtcRoute.WORK_QUEUE
                                }
                            )
                        },
                        onDiscardDraft = { viewModel.discardDraft(it.area) },
                        onHelp = { navController.navigateOverlay(RtcRoute.HELP) },
                        onOpenPublicReportScope = navController::openCommunityReports,
                        onCommunityPostDraft = { body ->
                            viewModel.saveDraft(DraftArea.COMMUNITY, body = body)
                            navController.openCommunityFeed()
                        },
                        onPublicReportDraft = navController::openPublicReportComposer,
                        onToggleEventRsvp = { eventId -> viewModel.toggleEventRsvp(eventId) },
                    ),
                ),
            )
        }
        composable(RtcRoute.COMMUNITY) { entry ->
            val initialSection = remember(entry) {
                when (entry.savedStateHandle.remove<String>(ResidentComposerPrefill.COMMUNITY_INITIAL_SECTION)) {
                    "reports" -> CommunitySection.REPORTS
                    else -> CommunitySection.DISCUSSIONS
                }
            }
            val initialReportScope = remember(entry) {
                runCatching {
                    PublicReportScope.valueOf(
                        entry.savedStateHandle
                            .remove<String>(ResidentComposerPrefill.COMMUNITY_REPORT_SCOPE)
                            .orEmpty(),
                    )
                }.getOrDefault(PublicReportScope.VERIFIED)
            }
            val openCommunityComposer = remember(entry) {
                entry.savedStateHandle.remove<Boolean>(ResidentComposerPrefill.COMMUNITY_OPEN_COMPOSER) == true
            }
            val isAdmin = session.role.isStaff || session.role in setOf(
                za.org.rtc.community.core.UserRole.MODERATOR,
                za.org.rtc.community.core.UserRole.SYSTEM_ADMIN,
                za.org.rtc.community.core.UserRole.CASE_STAFF,
                za.org.rtc.community.core.UserRole.EVIDENCE_REVIEWER,
            )
            CommunityHubScreen(
                initialSection = initialSection,
                initialReportScope = initialReportScope,
                isAdmin = isAdmin,
                discussions = { onNavigateToReports ->
                    CommunityScreen(
                        readingMode = session.readingMode,
                        useSyntheticPortraits = session.authority == SessionAuthority.DEVELOPMENT_ADAPTER,
                        draft = drafts.firstOrNull { it.area == DraftArea.COMMUNITY },
                        communityActionUi = communityActionUi,
                        guidelinesAccepted = communityGuidelinesAccepted,
                        onAcceptGuidelines = viewModel::acceptCommunityGuidelines,
                        onDismissCommunityMessage = viewModel::dismissCommunityActionUi,
                        onCreatePost = viewModel::createPost,
                        onSaveDraft = { viewModel.saveDraft(DraftArea.COMMUNITY, body = it) },
                        onDiscardDraft = { viewModel.discardDraft(DraftArea.COMMUNITY) },
                        onOpenPost = { post -> navController.navigateOverlay(communityPostRoute(post.id)) },
                        onSharePost = { sharedPost -> shareCommunityPost(context, sharedPost.id) },
                        onOpenReport = { reportId -> navController.navigateOverlay(RtcRoute.publicReportDetail(reportId)) },
                        onNavigateToReports = onNavigateToReports,
                        openComposerOnEntry = openCommunityComposer,
                    )
                },
                onOpenReport = { reportId -> navController.navigateOverlay(RtcRoute.publicReportDetail(reportId)) },
                onComposeReport = { navController.navigateOverlay(RtcRoute.PUBLIC_REPORT_NEW) },
                onOpenAdminWorkspace = { navController.navigateOverlay(RtcRoute.PUBLIC_REPORTS_ADMIN) },
            )
        }
        composable(RtcRoute.COMMUNITY_FEED) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                CommunityScreen(
                    readingMode = session.readingMode,
                    useSyntheticPortraits = session.authority == SessionAuthority.DEVELOPMENT_ADAPTER,
                    draft = drafts.firstOrNull { it.area == DraftArea.COMMUNITY },
                    communityActionUi = communityActionUi,
                    guidelinesAccepted = communityGuidelinesAccepted,
                    onAcceptGuidelines = viewModel::acceptCommunityGuidelines,
                    onDismissCommunityMessage = viewModel::dismissCommunityActionUi,
                    onCreatePost = viewModel::createPost,
                    onSaveDraft = { viewModel.saveDraft(DraftArea.COMMUNITY, body = it) },
                    onDiscardDraft = { viewModel.discardDraft(DraftArea.COMMUNITY) },
                    onOpenPost = { post -> navController.navigateOverlay(communityPostRoute(post.id)) },
                    onSharePost = { sharedPost -> shareCommunityPost(context, sharedPost.id) },
                    onOpenReport = { reportId -> navController.navigateOverlay(RtcRoute.publicReportDetail(reportId)) },
                    onNavigateToReports = { navController.navigateOverlay(RtcRoute.PUBLIC_REPORTS) },
                )
            }
        }
        composable(RtcRoute.MARKETPLACE_HOME) {
            MarketplaceHomeRoute(
                onNavigate = { navController.navigateOverlay(it) },
                onSwitchToServices = { navController.navigateOverlay(RtcRoute.SERVICE_CENTRE_HOME) },
            )
        }
        publicReportRoutes(navController, session)
        residentModernisationBindings(navController, session)
        composable(RtcRoute.MARKETPLACE_SEARCH) { MarketplaceSearchRoute(onNavigate = { navController.navigateOverlay(it) }) }
        composable(RtcRoute.MARKETPLACE_MAP) {
            MarketplaceMapRoute(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateOverlay(route) },
            )
        }
        composable(route = RtcRoute.MARKETPLACE_BUSINESS, arguments = listOf(navArgument("businessIdOrSlug") { type = NavType.StringType })) { entry ->
            MarketplaceDetailRoute(id = entry.arguments?.getString("businessIdOrSlug").orEmpty(), onNavigate = { navController.navigateOverlay(it) })
        }
        composable(RtcRoute.SERVICE_CENTRE_HOME) { ServiceCentreHomeRoute(onNavigate = { navController.navigateOverlay(it) }) }
        composable(
            route = RtcRoute.SERVICE_CENTRE_REQUEST_PATTERN,
            arguments = listOf(
                navArgument("providerId") { type = NavType.StringType },
                navArgument("businessId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("offeringId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            ServiceCentreRequestBookingRoute(
                providerReference = entry.arguments?.getString("providerId").orEmpty(),
                marketplaceBusinessId = entry.arguments?.getString("businessId"),
                marketplaceOfferingId = entry.arguments?.getString("offeringId"),
                onNavigate = { navController.navigateOverlay(it) },
            )
        }
        composable(RtcRoute.SERVICE_CENTRE_PROVIDER) { ServiceCentreProviderProfileRoute() }
        composable(RtcRoute.SERVICE_CENTRE_BOOKINGS) { ServiceCentreBookingHubRoute(onNavigate = { navController.navigateOverlay(it) }) }
        composable(route = RtcRoute.SERVICE_CENTRE_BOOKING, arguments = listOf(navArgument("bookingId") { type = NavType.StringType })) { entry ->
            ServiceCentreBookingDetailRoute(bookingId = entry.arguments?.getString("bookingId").orEmpty(), onNavigate = { navController.navigateOverlay(it) })
        }
        composable(
            route = RtcRoute.SERVICE_CENTRE_CHAT,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) { entry -> ServiceCentreChatRoute(entry.arguments?.getString("bookingId").orEmpty()) }
        composable(route = RtcRoute.MARKETPLACE_REVIEWS, arguments = listOf(navArgument("businessId") { type = NavType.StringType })) { entry ->
            MarketplaceReviewsRoute(businessId = entry.arguments?.getString("businessId").orEmpty(), onBack = { navController.popBackStack() })
        }
        composable(route = RtcRoute.MARKETPLACE_DIRECTIONS, arguments = listOf(navArgument("businessId") { type = NavType.StringType }, navArgument("locationId") { type = NavType.StringType })) { MarketplaceMapRoute(onBack = { navController.popBackStack() }) }
        composable(route = RtcRoute.MARKETPLACE_NAVIGATION, arguments = listOf(navArgument("locationId") { type = NavType.StringType })) { MarketplaceMapRoute(onBack = { navController.popBackStack() }) }
        composable(RtcRoute.MARKETPLACE_MY_BUSINESSES) { MarketplaceOwnerRoute(onNavigate = { navController.navigateOverlay(it) }) }
        composable(RtcRoute.MARKETPLACE_SAVED) { MarketplaceSavedRoute(onNavigate = { route -> navController.navigateOverlay(route) }) }
        composable(RtcRoute.MARKETPLACE_INVITATIONS) { MarketplaceInvitationsRoute() }
        composable(RtcRoute.MARKETPLACE_MY_REVIEWS) { MarketplaceMyReviewsRoute() }
        composable(RtcRoute.MARKETPLACE_BUSINESS_NEW) {
            MarketplaceOwnerWizardRoute(
                businessId = null,
                onNavigate = { navController.navigateOverlay(it) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(route = RtcRoute.MARKETPLACE_BUSINESS_EDIT, arguments = listOf(navArgument("businessId") { type = NavType.StringType })) { entry ->
            MarketplaceOwnerWizardRoute(businessId = entry.arguments?.getString("businessId"), onNavigate = { navController.navigateOverlay(it) }, onBack = { navController.popBackStack() })
        }
        composable(route = RtcRoute.MARKETPLACE_BUSINESS_PREVIEW, arguments = listOf(navArgument("businessId") { type = NavType.StringType })) { entry -> MarketplaceOwnerPreviewRoute(entry.arguments?.getString("businessId").orEmpty()) }
        composable(route = RtcRoute.MARKETPLACE_BUSINESS_STATUS, arguments = listOf(navArgument("businessId") { type = NavType.StringType })) { entry -> MarketplaceStatusRoute(entry.arguments?.getString("businessId").orEmpty()) }
        composable(RtcRoute.ADMIN_MARKETPLACE) {
            ProtectedRoute(RtcRoute.ADMIN_MARKETPLACE, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                MarketplaceAdminRoute(onNavigate = { navController.navigateOverlay(it) })
            }
        }
        composable(
            route = RtcRoute.ADMIN_MARKETPLACE_BUSINESS,
            arguments = listOf(navArgument("submissionId") { type = NavType.StringType }),
        ) { entry ->
            ProtectedRoute(RtcRoute.ADMIN_MARKETPLACE_BUSINESS, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                MarketplaceAdminSubmissionRoute(
                    submissionId = entry.arguments?.getString("submissionId").orEmpty(),
                    canModerateLifecycle = session.role == UserRole.SYSTEM_ADMIN,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RtcRoute.ADMIN_MARKETPLACE_REVIEWS) {
            ProtectedRoute(RtcRoute.ADMIN_MARKETPLACE_REVIEWS, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                MarketplaceAdminReviewsRoute(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateOverlay(route) },
                )
            }
        }
        composable(RtcRoute.ADMIN_MARKETPLACE_CATEGORIES) {
            ProtectedRoute(RtcRoute.ADMIN_MARKETPLACE_CATEGORIES, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                MarketplaceAdminCategoriesRoute(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RtcRoute.ADMIN_MARKETPLACE_FEATURED) {
            ProtectedRoute(RtcRoute.ADMIN_MARKETPLACE_FEATURED, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                MarketplaceAdminFeaturedRoute(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateOverlay(route) },
                )
            }
        }
        composable(RtcRoute.ADMIN_MARKETPLACE_ANALYTICS) {
            ProtectedRoute(RtcRoute.ADMIN_MARKETPLACE_ANALYTICS, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                MarketplaceAdminAnalyticsRoute(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RtcRoute.EXPLORE) {
            ExploreScreen(
                projects = projectsPage.items,
                opportunities = opportunitiesPage.items,
                notices = notices,
                dailyPosts = dailyPosts,
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onOpenDirectory = { directory -> navController.navigateOverlay(exploreDirectoryRoute(directory)) },
                onOpenDailyPost = { article ->
                    dailyPostViewModel.selectArticle(article)
                    navController.navigateOverlay(RtcRoute.dailyPostDetail(article.id))
                },
                onToggleDailyPostLike = { articleId -> dailyPostViewModel.toggleLike(articleId) },
                onOpenDailyPostStudio = { navController.navigateOverlay(RtcRoute.DAILY_POST_STUDIO) },
                onOpenReport = { reportId -> navController.navigateOverlay(RtcRoute.publicReportDetail(reportId)) },
                canManageDailyPosts = session.role.isStaff || session.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN),
            )
        }
        composable(
            route = "explore_directory/{directory}",
            arguments = listOf(navArgument("directory") { type = NavType.StringType }),
        ) { directoryEntry ->
            ExploreDirectoryScreen(
                directory = directoryEntry.arguments?.getString("directory").orEmpty(),
                notices = notices,
                projects = projectsPage.items,
                centres = centresPage.items,
                opportunities = opportunitiesPage.items,
                helpArticles = helpArticles,
                communityPosts = posts,
                readingMode = session.readingMode,
                draft = drafts.firstOrNull { it.area == DraftArea.NOTICE },
                onSubmit = viewModel::submitNotice,
                onSaveDraft = { title, body -> viewModel.saveDraft(DraftArea.NOTICE, title, body) },
                onDiscardDraft = { viewModel.discardDraft(DraftArea.NOTICE) },
                canLoadMoreProjects = projectsPage.canLoadMore,
                canLoadMoreCentres = centresPage.canLoadMore,
                canLoadMoreOpportunities = opportunitiesPage.canLoadMore,
                onLoadMoreProjects = viewModel::loadMoreProjects,
                onLoadMoreCentres = viewModel::loadMoreCentres,
                onLoadMoreOpportunities = viewModel::loadMoreOpportunities,
                onHelp = { navController.navigateOverlay(RtcRoute.HELP) },
                onOpenPost = { post -> navController.navigateOverlay(communityPostRoute(post.id)) },
                onOpenDirectoryItem = { type, id -> navController.navigateOverlay(directoryItemRoute(type, id)) },
                onOpenNotice = { notice -> navController.navigateOverlay(noticeRoute(notice.id)) },
            )
        }
        composable(
            route = "community_post/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { postEntry ->
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                val postId = postEntry.arguments?.getString("postId").orEmpty()
                CommunityPostDetailScreen(
                    postId = postId,
                    session = session,
                    communityActionUi = communityActionUi,
                    guidelinesAccepted = communityGuidelinesAccepted,
                    onAcceptGuidelines = viewModel::acceptCommunityGuidelines,
                    onDismissCommunityMessage = viewModel::dismissCommunityActionUi,
                    onSharePost = { sharedPost -> shareCommunityPost(context, sharedPost.id) },
                    onReportPost = viewModel::reportCommunityPost,
                    commentDraft = drafts.firstOrNull { it.area == DraftArea.COMMUNITY_COMMENT && it.title == postId },
                    onSaveCommentDraft = { body -> viewModel.saveDraft(DraftArea.COMMUNITY_COMMENT, title = postId, body = body) },
                    onDiscardCommentDraft = { viewModel.discardDraft(DraftArea.COMMUNITY_COMMENT) },
                )
            }
        }
        composable(
            route = "directory_item/{type}/{id}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { detailEntry ->
            val type = detailEntry.arguments?.getString("type").orEmpty()
            val id = detailEntry.arguments?.getString("id").orEmpty()
            DirectoryRecordDetailScreen(
                type = type,
                project = projectsPage.items.firstOrNull { type == "PROJECT" && it.id == id },
                centre = centresPage.items.firstOrNull { type == "CENTRE" && it.id == id },
                opportunity = opportunitiesPage.items.firstOrNull { type == "OPPORTUNITY" && it.id == id },
            )
        }
        composable(
            route = "notice/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { noticeEntry ->
            val id = noticeEntry.arguments?.getString("id").orEmpty()
            NoticeDetailScreen(notices.firstOrNull { it.id == id && it.status == NoticeStatus.PUBLISHED })
        }
        composable(RtcRoute.SUPPORT) {
            SupportScreen(
                cases = cases,
                draft = drafts.firstOrNull { it.area == DraftArea.SUPPORT },
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onSubmit = viewModel::submitSupportRequest,
                onSaveDraft = { title, body -> viewModel.saveDraft(DraftArea.SUPPORT, title, body) },
                onDiscardDraft = { viewModel.discardDraft(DraftArea.SUPPORT) },
                onOpenCase = { case -> navController.navigateOverlay(RtcRoute.supportCaseDetail(case.id)) },
                onOpenCentres = { navController.navigateOverlay(exploreDirectoryRoute("centres")) },
            )
        }
        composable(
            route = RtcRoute.SUPPORT_CASE_DETAIL,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
        ) { caseEntry ->
            val caseId = caseEntry.arguments?.getString("caseId").orEmpty()
            val selectedCase = cases.firstOrNull { it.id == caseId }
            LaunchedEffect(caseId) { if (caseId.isNotBlank()) viewModel.loadSupportCaseMessages(caseId) }
            SupportCaseDetailScreen(viewModel = viewModel, supportCase = selectedCase)
        }
        composable(RtcRoute.ACCOUNT) {
            AccountScreen(
                viewModel = viewModel,
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onHelp = { navController.navigateOverlay(RtcRoute.HELP) },
                onMarketplace = { navController.navigateOverlay(it) },
                onOpenProfile = { navController.navigateOverlay(RtcRoute.MY_WORK) },
                onOpenNotifications = { navController.navigateOverlay(RtcRoute.NOTIFICATIONS) },
            )
        }
        composable(RtcRoute.NOTIFICATIONS) {
            NotificationsScreen(
                notifications = notifications,
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onMarkRead = viewModel::markNotificationsRead,
                onOpen = { notification ->
                    viewModel.markNotificationsRead()
                    notification.alertId?.let { navController.navigateOverlay(RtcRoute.alertDetail(it)) }
                        ?: navController.navigateOverlay(notification.route.route())
                },
            )
        }
        composable(RtcRoute.ALERTS) {
            CommunityAlertsScreen(
                alerts = communityAlerts,
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onOpen = { alert -> navController.navigateOverlay(RtcRoute.alertDetail(alert.id)) },
                onMarkAllRead = viewModel::markNotificationsRead,
            )
        }
        composable(
            route = RtcRoute.ALERT_DETAIL,
            arguments = listOf(navArgument("alertId") { type = NavType.StringType }),
        ) { alertEntry ->
            val alertId = alertEntry.arguments?.getString("alertId").orEmpty()
            LaunchedEffect(alertId) { if (alertId.isNotBlank()) viewModel.loadCommunityAlertDetail(alertId) }
            CommunityAlertDetailScreen(
                alert = communityAlertDetail?.takeIf { it.id == alertId },
                onOpenLinkedNotice = { noticeId -> navController.navigateOverlay(noticeRoute(noticeId)) },
            )
        }
        composable(RtcRoute.SEARCH) {
            SearchScreen(viewModel = viewModel, onOpenResult = { result -> navController.navigateOverlay(publicSearchResultRoute(result)) })
        }
        composable(
            route = "search_result/{type}/{id}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entry ->
            val type = entry.arguments?.getString("type")
            val id = entry.arguments?.getString("id")
            SearchResultDetailScreen(
                result = publicSearchResults.firstOrNull { it.resultType == type && it.resultId == id },
                onBack = { navController.popBackStack() },
            )
        }
        composable(RtcRoute.HELP) { HelpCentreScreen() }
        composable(RtcRoute.WORK_QUEUE) {
            ProtectedRoute(RtcRoute.WORK_QUEUE, session, onDenied = { navController.navigatePrimary(RtcRoute.HOME) }) {
                AdminWorkspace(
                    viewModel = viewModel,
                    onOpenAi = { navController.navigateOverlay(RtcRoute.AI) },
                    onOpenTool = { toolRoute -> navController.navigateOverlay(toolRoute) },
                    draft = drafts.firstOrNull { it.area in setOf(DraftArea.STAFF_CONTENT, DraftArea.STAFF_MODERATION) },
                    onDiscardDraft = { viewModel.discardDraft(it.area) },
                    pendingSyncCount = pendingSyncCount,
                )
            }
        }
        composable(RtcRoute.AI) {
            ProtectedRoute(RtcRoute.AI, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { AiAssistantScreen(viewModel) }
        }
        composable(RtcRoute.STAFF_ALERTS) {
            ProtectedRoute(RtcRoute.STAFF_ALERTS, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { StaffCommunityAlertsScreen(viewModel = viewModel, alerts = communityAlertDashboard) }
        }
        composable(RtcRoute.CONTENT) {
            ProtectedRoute(RtcRoute.CONTENT, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                ContentManagementScreen(
                    viewModel,
                    onOpenAi = { navController.navigateOverlay(RtcRoute.AI) },
                    draft = drafts.firstOrNull { it.area == DraftArea.STAFF_CONTENT },
                    onSaveDraft = { title, body -> viewModel.saveDraft(DraftArea.STAFF_CONTENT, title, body) },
                    onDiscardDraft = { viewModel.discardDraft(DraftArea.STAFF_CONTENT) },
                )
            }
        }
        composable(RtcRoute.MODERATION) {
            ProtectedRoute(RtcRoute.MODERATION, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                ModerationDashboard(
                    viewModel,
                    onOpenAi = { navController.navigateOverlay(RtcRoute.AI) },
                    draft = drafts.firstOrNull { it.area == DraftArea.STAFF_MODERATION },
                    onDiscardDraft = { viewModel.discardDraft(DraftArea.STAFF_MODERATION) },
                )
            }
        }
        composable(RtcRoute.ADMIN_MFA) {
            ProtectedRoute(RtcRoute.ADMIN_MFA, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { AdministratorMfaVerificationScreen(viewModel = viewModel, onReturnToWorkQueue = { navController.popBackStack() }) }
        }
        composable(RtcRoute.ACCESS_MANAGEMENT) {
            ProtectedRoute(RtcRoute.ACCESS_MANAGEMENT, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { AccessManagementScreen(viewModel = viewModel) }
        }
        composable(RtcRoute.ANALYTICS_DASHBOARD) {
            ProtectedRoute(RtcRoute.ANALYTICS_DASHBOARD, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { AdministratorPrivacyAnalyticsScreen(viewModel = viewModel) }
        }
        composable(RtcRoute.OPERATIONAL_CONTROLS) {
            ProtectedRoute(RtcRoute.OPERATIONAL_CONTROLS, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { OperationalControlsScreen(viewModel = viewModel, onExit = { navController.popBackStack() }) }
        }
        composable(RtcRoute.SYSTEM_HEALTH) {
            ProtectedRoute(RtcRoute.SYSTEM_HEALTH, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { SystemHealthScreen(viewModel) }
        }
        composable(RtcRoute.ADMIN_ACTIVITY) {
            ProtectedRoute(RtcRoute.ADMIN_ACTIVITY, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { AdminActivityScreen(viewModel) }
        }
        composable(RtcRoute.ADMIN_BRANDING) {
            ProtectedRoute(RtcRoute.ADMIN_BRANDING, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { BrandExperienceScreen(onBack = { navController.popBackStack() }) }
        }
        composable(RtcRoute.MY_WORK) {
            ProtectedRoute(RtcRoute.MY_WORK, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) { MyWorkProfileScreen(viewModel = viewModel, onOpenAccount = { navController.navigateOverlay(RtcRoute.ACCOUNT) }) }
        }
        composable(RtcRoute.DAILY_POST_STUDIO) {
            ProtectedRoute(RtcRoute.DAILY_POST_STUDIO, session, onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) }) {
                DailyPostStudioScreen(
                    viewModel = dailyPostViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPublishedPost = { article: DailyPostArticle ->
                        dailyPostViewModel.selectArticle(article)
                        navController.navigateOverlay(RtcRoute.dailyPostDetail(article.id))
                    },
                    adminAuthorName = session.displayName.ifBlank { "Office of the Administrator" },
                    adminRoleName = session.role.name.replace('_', ' '),
                )
            }
        }
        composable(
            route = RtcRoute.DAILY_POST_DETAIL,
            arguments = listOf(navArgument("articleId") { type = NavType.StringType }),
        ) { entry ->
            val articleId = entry.arguments?.getString("articleId").orEmpty()
            androidx.compose.runtime.LaunchedEffect(articleId) {
                try {
                    dailyPostViewModel.loadArticleById(articleId)
                    dailyPostViewModel.observeComments(articleId)
                    awaitCancellation()
                } finally {
                    dailyPostViewModel.stopObservingComments()
                }
            }
            val selectedArticle by dailyPostViewModel.selectedArticle.collectAsStateWithLifecycle()
            DailyPostDetailScreen(
                article = selectedArticle,
                onBack = { navController.popBackStack() },
                onToggleLike = { id -> dailyPostViewModel.toggleLike(id) },
                onShare = { article ->
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "${article.title}\n\n${article.content}")
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Daily Post")
                    context.startActivity(shareIntent)
                },
                comments = dailyPostComments,
                commentsLoading = dailyPostCommentsLoading,
                commentsHasMore = dailyPostCommentsHasMore,
                pendingCommentId = dailyPostCommentPendingId,
                currentUserId = session.id,
                canModerateComments = session.role == UserRole.MODERATOR || session.role == UserRole.SYSTEM_ADMIN,
                onRefreshComments = { dailyPostViewModel.refreshComments(articleId) },
                onLoadOlderComments = { dailyPostViewModel.loadOlderComments(articleId) },
                onCreateComment = { body, parentId -> dailyPostViewModel.submitComment(articleId, body, parentId) },
                onUpdateComment = { commentId, body -> dailyPostViewModel.updateComment(articleId, commentId, body) },
                onDeleteComment = { commentId -> dailyPostViewModel.deleteComment(articleId, commentId) },
                onModerateComment = { commentId, reason -> dailyPostViewModel.moderateComment(articleId, commentId, reason) },
                onReportComment = { commentId, reasonCode, detail -> dailyPostViewModel.reportComment(articleId, commentId, reasonCode, detail) },
            )
        }
    }
    }
}
}
