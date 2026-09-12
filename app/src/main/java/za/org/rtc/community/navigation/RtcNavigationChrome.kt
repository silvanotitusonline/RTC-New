package za.org.rtc.community.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.ui.theme.RtcDesignSystem

sealed class NavTab(val route: String, val icon: ImageVector, val label: String) {
    object Home : NavTab("home", Icons.Default.Home, "Home")
    object Explore : NavTab("explore", Icons.Default.Search, "Explore")
    object Create : NavTab("create", Icons.Default.AddCircle, "Post")
    object Notifications : NavTab("notifications", Icons.Default.Notifications, "Alerts")
    object Profile : NavTab("profile", Icons.Default.Person, "Profile")
}

@Composable
fun RtcNavigationChrome(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = RtcDesignSystem.BackgroundDark,
        contentColor = RtcDesignSystem.TextPrimary,
        tonalElevation = 0.dp,
    ) {
        val tabs = listOf(NavTab.Home, NavTab.Explore, NavTab.Create, NavTab.Notifications, NavTab.Profile)
        tabs.forEach { tab ->
            NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 10.sp) },
                selected = currentRoute == tab.route,
                onClick = { onNavigate(tab.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RtcDesignSystem.PrimaryBrand,
                    unselectedIconColor = RtcDesignSystem.TextSecondary,
                    indicatorColor = RtcDesignSystem.SurfaceDark,
                ),
            )
        }
    }
}
