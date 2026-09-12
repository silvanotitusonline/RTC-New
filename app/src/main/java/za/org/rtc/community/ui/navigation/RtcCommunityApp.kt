package za.org.rtc.community.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import za.org.rtc.community.ui.animation.LocalReducedMotion
import za.org.rtc.community.ui.animation.LocalSnackbarHostState
import za.org.rtc.community.ui.animation.RtcMotionAlertDialog
import za.org.rtc.community.ui.animation.RtcMotionPatterns
import za.org.rtc.community.ui.animation.RtcMotionSnackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.ResidentModernisationFeature
import za.org.rtc.community.core.ResidentModernisationFeatureFlags
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.account.NotificationPanel
import za.org.rtc.community.feature.onboarding.InteractiveOnboardingTutorial
import za.org.rtc.community.navigation.RouteAccessPolicy
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.navigatePrimary
import za.org.rtc.community.navigation.residentPrimaryRoutes
import za.org.rtc.community.navigation.returnToSafeWorkspace
import za.org.rtc.community.ui.components.RtcSplashScreen
import za.org.rtc.community.ui.components.RtcResidentBottomNavigation
import za.org.rtc.community.ui.components.RtcResidentNavItem
import za.org.rtc.community.ui.config.LocalRtcUiConfiguration
import za.org.rtc.community.ui.config.presentationForRoute
import za.org.rtc.community.ui.config.rtcContentDensity
import za.org.rtc.community.ui.theme.LocalRtcContentDensity
import za.org.rtc.community.ui.theme.RtcWindowWidth
import za.org.rtc.community.ui.theme.classifyRtcWindowWidth

private data class ServiceCentreDeepLinkContext(
    val bookingId: String? = null,
    val onConsumed: () -> Unit = {},
)

private val LocalServiceCentreDeepLinkContext = staticCompositionLocalOf { ServiceCentreDeepLinkContext() }

@Composable
internal fun ServiceCentreDeepLinkScope(
    bookingId: String?,
    onConsumed: () -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalServiceCentreDeepLinkContext provides ServiceCentreDeepLinkContext(bookingId, onConsumed),
        content = content,
    )
}

@Composable
internal fun RtcCommunityApp(viewModel: RtcViewModel) {
    val serviceCentreDeepLink = LocalServiceCentreDeepLinkContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pendingCommunityAlertId by viewModel.pendingCommunityAlertId.collectAsStateWithLifecycle()
    val pendingCommunityPostId by viewModel.pendingCommunityPostId.collectAsStateWithLifecycle()
    val passwordRecoveryActive by viewModel.passwordRecoveryActive.collectAsStateWithLifecycle()
    val notificationPermissionPrompt by viewModel.notificationPermissionPrompt.collectAsStateWithLifecycle()
    val isSessionRestoring by viewModel.isSessionRestoring.collectAsStateWithLifecycle()
    val liveContentMessage by viewModel.liveContentMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationPermissionRationaleOpen by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var pendingPublicRoute by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(notificationPermissionPrompt, session.authority) {
        if (!notificationPermissionPrompt || session.authority != SessionAuthority.SUPABASE_AUTH) return@LaunchedEffect
        viewModel.consumeNotificationPermissionPrompt()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRationaleOpen = true
        }
    }

    if (isSessionRestoring) {
        RtcSplashScreen(configuration = LocalRtcUiConfiguration.current)
        return
    }

    val navController = rememberNavController()
    val isOffline by rememberIsOffline()
    val configuration = LocalConfiguration.current
    val isOrientationLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val windowWidth = classifyRtcWindowWidth(configuration.screenWidthDp)
    val isLargeScreen = windowWidth == RtcWindowWidth.EXPANDED || (windowWidth == RtcWindowWidth.MEDIUM && isOrientationLandscape)
    val contentFocusRequester = remember { FocusRequester() }
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val operationsWorkQueue by viewModel.operationsWorkQueue.collectAsStateWithLifecycle()
    val isInteractiveTutorialVisible by viewModel.isInteractiveTutorialVisible.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val uiConfiguration = LocalRtcUiConfiguration.current
    val routePresentation = uiConfiguration.presentationForRoute(route)
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    var selectedWorkspace by rememberSaveable { mutableStateOf("Community Operations") }
    val isStaff = session.role.isStaff
    val navigationV2Enabled = ResidentModernisationFeatureFlags.isEnabled(ResidentModernisationFeature.RESIDENT_NAVIGATION_V2)
    val unifiedInboxEnabled = ResidentModernisationFeatureFlags.isEnabled(ResidentModernisationFeature.UNIFIED_INBOX)
    val activeNavItems = residentNavItems(navigationV2Enabled)
    val residentPrimaryRoutes = residentPrimaryRoutes(navigationV2Enabled)

    LaunchedEffect(route, session.role, session.authority, session.administratorMfaStatus) {
        val decision = RouteAccessPolicy.evaluate(route, session.role, session.authority, session.administratorMfaStatus)
        if (!decision.allowed && route in setOf(
                RtcRoute.OPERATIONS_HUB,
                RtcRoute.MY_WORK,
                RtcRoute.STAFF_ALERTS,
                RtcRoute.CONTENT,
                RtcRoute.MODERATION,
                RtcRoute.AI,
                RtcRoute.ACCESS_MANAGEMENT,
                RtcRoute.OPERATIONAL_CONTROLS,
                RtcRoute.ANALYTICS_DASHBOARD,
                RtcRoute.SYSTEM_HEALTH,
                RtcRoute.ADMIN_ACTIVITY,
                RtcRoute.ADMIN_BRANDING,
                RtcRoute.ADMIN_MFA,
            )
        ) {
            navController.returnToSafeWorkspace(session.role.isStaff)
        }
    }

    LaunchedEffect(pendingCommunityAlertId) {
        pendingCommunityAlertId?.let { alertId ->
            navController.navigateOverlay(RtcRoute.alertDetail(alertId))
            viewModel.consumePendingCommunityAlert()
        }
    }
    LaunchedEffect(pendingCommunityPostId) {
        pendingCommunityPostId?.let { postId ->
            navController.navigateOverlay(communityPostRoute(postId))
            viewModel.consumePendingCommunityPost()
        }
    }
    LaunchedEffect(serviceCentreDeepLink.bookingId, session.authority) {
        serviceCentreDeepLink.bookingId?.takeIf { session.authority == SessionAuthority.SUPABASE_AUTH }?.let { bookingId ->
            navController.navigateOverlay(RtcRoute.serviceCentreBooking(bookingId))
            serviceCentreDeepLink.onConsumed()
        }
    }
    LaunchedEffect(session.role, passwordRecoveryActive, route) {
        val intendedRoute = pendingPublicRoute
        if (!isStaff && intendedRoute != null) {
            navController.navigatePrimary(intendedRoute)
            pendingPublicRoute = null
        }
        if (isStaff && route == RtcRoute.STAFF_ACCESS) {
            navController.navigatePrimary(RtcRoute.OPERATIONS_HUB)
        }
        if (passwordRecoveryActive && isStaff) navController.navigateOverlay(RtcRoute.ACCOUNT)
    }

    val isPrimaryResidentRoute = route in residentPrimaryRoutes
    val showBack = route != null && route !in residentPrimaryRoutes && route != RtcRoute.WORK_QUEUE
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            RtcTopBar(
                title = routeTitle(route),
                headerStyle = routePresentation.headerStyle,
                workspaceLabel = if (
                    session.role == UserRole.SYSTEM_ADMIN &&
                    route in setOf(
                        RtcRoute.WORK_QUEUE,
                        RtcRoute.CONTENT,
                        RtcRoute.MODERATION,
                        RtcRoute.ACCESS_MANAGEMENT,
                        RtcRoute.ANALYTICS_DASHBOARD,
                        RtcRoute.OPERATIONAL_CONTROLS,
                        RtcRoute.ADMIN_BRANDING,
                    )
                ) selectedWorkspace else null,
                onSelectWorkspace = { selectedWorkspace = it },
                showBack = showBack,
                unreadCount = notifications.count { it.unread },
                showReadingMode = ResidentNavigationPolicy.showReadingModeAction(navigationV2Enabled, isStaff) &&
                    route in setOf(RtcRoute.HOME, RtcRoute.COMMUNITY, RtcRoute.EXPLORE),
                readingMode = session.readingMode,
                onBack = { navController.popBackStack() },
                onReadingMode = viewModel::toggleReadingMode,
                onNotifications = {
                    ResidentNavigationPolicy.notificationRoute(unifiedInboxEnabled, isStaff)
                        ?.let(navController::navigateOverlay)
                        ?: run { notificationsOpen = true }
                },
                onSearch = if (!isStaff && (navigationV2Enabled && isPrimaryResidentRoute || route in setOf(RtcRoute.HOME, RtcRoute.EXPLORE))) {
                    { navController.navigateOverlay(RtcRoute.SEARCH) }
                } else null,
                showProfile = ResidentNavigationPolicy.showProfileAction(navigationV2Enabled, isStaff),
                onProfile = { navController.navigateOverlay(RtcRoute.ACCOUNT) },
            )
        },
        bottomBar = {
            if (!isStaff && isPrimaryResidentRoute && !isLargeScreen) {
                val selected = activeNavItems.firstOrNull { it.route == route }?.destination ?: MainDestination.HOME
                RtcResidentBottomNavigation(
                    selected = selected,
                    items = activeNavItems.map { RtcResidentNavItem(it.destination, it.label, it.inactiveIcon) },
                    onNavigate = { destination ->
                        activeNavItems.firstOrNull { it.destination == destination }
                            ?.let { navController.navigatePrimary(it.route) }
                    },
                )
            } else if (isStaff && !isLargeScreen && route != RtcRoute.ADMIN_MFA) {
                StaffWorkspaceBottomNavigation(
                    route = route,
                    role = session.role,
                    onNavigate = { navController.navigatePrimary(it) },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
                snackbar = { RtcMotionSnackbar(snackbarData = it) },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isOffline) OfflineBanner()
                if (!isStaff) {
                    liveContentMessage?.let { message ->
                        ResidentRefreshFailureBanner(
                            message = message,
                            onRetry = viewModel::refreshLiveContent,
                            onDismiss = viewModel::dismissLiveContentMessage,
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isLargeScreen) {
                        TextButton(onClick = { contentFocusRequester.requestFocus() }) { Text("Skip to content") }
                    }
                    if (isLargeScreen && !isStaff) {
                        ResidentNavigationRail(
                            route = route,
                            items = activeNavItems,
                            onNavigate = { navController.navigatePrimary(it.route) },
                        )
                    }
                    if (isLargeScreen && isStaff) {
                        StaffWorkspacePane(
                            route = route,
                            role = session.role,
                            workItems = operationsWorkQueue,
                            onNavigate = { navController.navigatePrimary(it) },
                        )
                    }
                    CompositionLocalProvider(
                        LocalReducedMotion provides remember(context) { RtcMotionPatterns.isReducedMotion(context) },
                        LocalRtcContentDensity provides routePresentation.rtcContentDensity(uiConfiguration.appearance.densityPreset),
                        LocalSnackbarHostState provides snackbarHostState,
                    ) {
                        RtcCommunityNavGraph(
                            modifier = Modifier.weight(1f).focusRequester(contentFocusRequester).focusable(),
                            navController = navController,
                            viewModel = viewModel,
                            session = session,
                            context = context,
                        )
                    }
                }
            }
        }
    }

    if (notificationPermissionRationaleOpen) {
        RtcMotionAlertDialog(
            onDismissRequest = { notificationPermissionRationaleOpen = false },
            title = { Text("Stay informed") },
            text = { Text("Allow notifications to receive Community updates and safety alerts. You can change this later from Account > Notification preferences or your device settings.") },
            confirmButton = {
                Button(onClick = {
                    notificationPermissionRationaleOpen = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Allow notifications") }
            },
            dismissButton = {
                TextButton(onClick = { notificationPermissionRationaleOpen = false }) { Text("Not now") }
            },
        )
    }

    if (notificationsOpen) {
        NotificationPanel(
            notifications = notifications,
            onDismiss = { notificationsOpen = false },
            onOpen = { notification ->
                notificationsOpen = false
                notification.alertId?.let { navController.navigateOverlay(RtcRoute.alertDetail(it)) }
                    ?: navController.navigateOverlay(notification.route.route())
            },
            onViewAll = {
                notificationsOpen = false
                navController.navigateOverlay(RtcRoute.NOTIFICATIONS)
            },
        )
    }

    if (isInteractiveTutorialVisible && !isStaff) {
        InteractiveOnboardingTutorial(
            onDismiss = { viewModel.dismissInteractiveTutorial(markCompleted = true) },
            onNavigateToFeature = { targetRoute ->
                viewModel.dismissInteractiveTutorial(markCompleted = true)
                navController.navigatePrimary(targetRoute)
            },
        )
    }
}
