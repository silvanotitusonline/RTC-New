package za.org.rtc.community.ui.navigation

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import za.org.rtc.community.ui.animation.RtcMotionMenuContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.core.ScreenHeaderStyle
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun ResidentNavigationRail(route: String?, items: List<NavItem>, onNavigate: (NavItem) -> Unit) {
    NavigationRail {
        items.forEach { item ->
            NavigationRailItem(selected = route == item.route, onClick = { onNavigate(item) }, icon = { Icon(if (route == item.route) item.activeIcon else item.inactiveIcon, contentDescription = item.label) }, label = { Text(item.label) }, alwaysShowLabel = true)
        }
    }
}

@Composable
internal fun StaffWorkspaceBottomNavigation(route: String?, role: UserRole, onNavigate: (String) -> Unit) {
    val items = when (role) {
        UserRole.SYSTEM_ADMIN -> listOf(Triple(RtcRoute.WORK_QUEUE, "Queue", Icons.AutoMirrored.Filled.Assignment), Triple(RtcRoute.ACCESS_MANAGEMENT, "Access", Icons.Filled.AdminPanelSettings), Triple(RtcRoute.OPERATIONAL_CONTROLS, "Controls", Icons.Filled.Settings), Triple(RtcRoute.ANALYTICS_DASHBOARD, "Analytics", Icons.Filled.Visibility))
        UserRole.CONTENT_EDITOR -> listOf(Triple(RtcRoute.WORK_QUEUE, "Queue", Icons.AutoMirrored.Filled.Assignment), Triple(RtcRoute.STAFF_ALERTS, "Alerts", Icons.Filled.Notifications), Triple(RtcRoute.CONTENT, "Content", Icons.Filled.Campaign), Triple(RtcRoute.MY_WORK, "Profile", Icons.Filled.Person))
        UserRole.MODERATOR -> listOf(Triple(RtcRoute.WORK_QUEUE, "Queue", Icons.AutoMirrored.Filled.Assignment), Triple(RtcRoute.MODERATION, "Safety", Icons.Filled.Shield), Triple(RtcRoute.AI, "RTC AI", Icons.Filled.Psychology), Triple(RtcRoute.MY_WORK, "Profile", Icons.Filled.Person))
        else -> listOf(Triple(RtcRoute.WORK_QUEUE, "Queue", Icons.AutoMirrored.Filled.Assignment), Triple(RtcRoute.MY_WORK, "Profile", Icons.Filled.Person))
    }
    val selectedRoute = when (route) { RtcRoute.SYSTEM_HEALTH, RtcRoute.ADMIN_ACTIVITY, RtcRoute.ADMIN_BRANDING -> RtcRoute.OPERATIONAL_CONTROLS; else -> route }
    NavigationBar { items.forEach { (itemRoute, label, icon) -> NavigationBarItem(selected = selectedRoute == itemRoute, onClick = { onNavigate(itemRoute) }, icon = { Icon(icon, contentDescription = label) }, label = { Text(label) }, alwaysShowLabel = true) } }
}

@Composable
internal fun StaffWorkspacePane(route: String?, role: UserRole, workItems: List<OperationsWorkItem>, onNavigate: (String) -> Unit) {
    val tools = buildList {
        add(RtcRoute.OPERATIONS_HUB to "Operations Hub")
        if (role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) { add(RtcRoute.EDITORIAL_CONTENT to "Content & publication"); add(RtcRoute.ADMIN_MARKETPLACE to "Marketplace Operations"); add(RtcRoute.STAFF_ALERTS to "Alerts") }
        if (role in setOf(UserRole.MODERATOR, UserRole.SYSTEM_ADMIN)) { add(RtcRoute.MODERATOR_CENTRE to "Community Safety"); add(RtcRoute.ADMIN_MARKETPLACE_REVIEWS to "Marketplace Reviews") }
        add(RtcRoute.MY_WORK to "My Work")
        if (role == UserRole.SYSTEM_ADMIN) { add(RtcRoute.ACCESS_MANAGEMENT to "Access"); add(RtcRoute.SYSTEM_CONTROL_CENTRE to "System Control Centre"); add(RtcRoute.ADMIN_BRANDING to "Brand & Experience"); add(RtcRoute.SYSTEM_HEALTH to "System Health"); add(RtcRoute.ADMIN_ACTIVITY to "Activity") }
    }
    Surface(modifier = Modifier.width(RtcSize.staffPaneWidth).fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Operations Hub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("${workItems.size} live item(s) needing attention", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            workItems.take(3).forEach { item -> Text(item.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            HorizontalDivider()
            tools.forEach { (toolRoute, label) -> TextButton(onClick = { onNavigate(toolRoute) }, modifier = Modifier.fillMaxWidth()) { Text(if (route == toolRoute) "• $label" else label, modifier = Modifier.weight(1f)) } }
        }
    }
}

@Composable
internal fun rememberIsOffline(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val offline = remember { mutableStateOf(true) }
    DisposableEffect(connectivityManager) {
        fun updateState() { val capabilities = connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }; offline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true }
        val callback = object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) = updateState(); override fun onLost(network: Network) = updateState(); override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = updateState() }
        updateState(); connectivityManager.registerDefaultNetworkCallback(callback); onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return offline
}

@Composable
internal fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = RtcSpacing.standard, vertical = RtcSpacing.compact), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer); Spacer(Modifier.width(RtcSpacing.compact)); Column { Text("You are offline", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer); Text("You can continue viewing cached information. Actions that need a connection will stay pending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
internal fun ResidentRefreshFailureBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = RtcSpacing.standard, vertical = RtcSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(modifier = Modifier.weight(1f)) {
                Text("Information may be out of date", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRetry) { Text("Try again") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RtcTopBar(
    title: String,
    headerStyle: ScreenHeaderStyle = ScreenHeaderStyle.STANDARD,
    workspaceLabel: String?,
    onSelectWorkspace: (String) -> Unit,
    showBack: Boolean,
    unreadCount: Int,
    showReadingMode: Boolean,
    readingMode: Boolean,
    onBack: () -> Unit,
    onReadingMode: () -> Unit,
    onNotifications: () -> Unit,
    onSearch: (() -> Unit)?,
    showProfile: Boolean,
    onProfile: () -> Unit,
) {
    var workspaceMenuOpen by rememberSaveable { mutableStateOf(false) }
    val titleStyle = when (headerStyle) {
        ScreenHeaderStyle.COMPACT -> MaterialTheme.typography.titleSmall
        ScreenHeaderStyle.STANDARD -> MaterialTheme.typography.titleMedium
        ScreenHeaderStyle.HERO -> MaterialTheme.typography.titleLarge
    }
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = titleStyle, fontWeight = FontWeight.Bold)
                workspaceLabel?.let { activeWorkspace ->
                    Box {
                        TextButton(onClick = { workspaceMenuOpen = true }) {
                            Text("Workspace: $activeWorkspace")
                        }
                        DropdownMenu(
                            expanded = workspaceMenuOpen,
                            onDismissRequest = { workspaceMenuOpen = false },
                        ) {
                            RtcMotionMenuContainer(expanded = workspaceMenuOpen) {
                                Column {
                                    listOf("Community Operations", "Publishing", "Assurance").forEach { workspace ->
                                        DropdownMenuItem(
                                            text = { Text(workspace) },
                                            onClick = {
                                                workspaceMenuOpen = false
                                                onSelectWorkspace(workspace)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = { if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back") } },
        actions = {
            if (showReadingMode) IconButton(onClick = onReadingMode) { Icon(Icons.Filled.AccessibilityNew, contentDescription = if (readingMode) "Turn simplified reading mode off" else "Turn simplified reading mode on") }
            if (!showBack) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.micro),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onSearch?.let { search -> IconButton(onClick = search) { Icon(Icons.Filled.Search, contentDescription = "Search public content") } }
                    IconButton(onClick = onNotifications) { BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.coerceAtMost(99).toString()) } }) { Icon(Icons.Outlined.NotificationsNone, contentDescription = "Notifications, $unreadCount unread") } }
                }
                if (showProfile) IconButton(onClick = onProfile) { Icon(Icons.Outlined.Person, contentDescription = "Open account or work queue") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
