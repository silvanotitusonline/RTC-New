
package za.org.rtc.community.feature.administration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun AdministrationHubScreen(viewModel: AdministrationDashboardViewModel) {
    val section by viewModel.currentSection.collectAsState()

    Row(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark)) {
        // Left Navigation Rail (Industry Standard for Admin Panels)
        NavigationRail(
            containerColor = RtcDesignSystem.SurfaceDark,
            header = {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", tint = RtcDesignSystem.PrimaryBrand, modifier = Modifier.padding(12.dp))
            }
        ) {
            AdminNavMenuItem(AdminSection.OVERVIEW, "Overview", Icons.Default.Dashboard, section, viewModel)
            AdminNavMenuItem(AdminSection.MODERATION, "Moderation", Icons.Default.Gavel, section, viewModel)
            AdminNavMenuItem(AdminSection.CIVIC_ANALYTICS, "Analytics", Icons.Default.Analytics, section, viewModel)
            AdminNavMenuItem(AdminSection.USER_MANAGEMENT, "Users", Icons.Default.People, section, viewModel)
            AdminNavMenuItem(AdminSection.SYSTEM_HEALTH, "Health", Icons.Default.Settings, section, viewModel)
        }

        // Main Content Area
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when (section) {
                AdminSection.OVERVIEW -> AdminOverviewScreen()
                AdminSection.MODERATION -> ModerationCenterScreen()
                AdminSection.CIVIC_ANALYTICS -> AdminAnalyticsScreen()
                AdminSection.USER_MANAGEMENT -> UserManagementScreen()
                AdminSection.SYSTEM_HEALTH -> SystemHealthScreen()
            }
        }
    }
}

@Composable
fun AdminNavMenuItem(section: AdminSection, label: String, icon: ImageVector, current: AdminSection, viewModel: AdministrationDashboardViewModel) {
    NavigationRailItem(
        selected = current == section,
        onClick = { viewModel.setSection(section) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 10.sp) },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = RtcDesignSystem.PrimaryBrand,
            unselectedIconColor = RtcDesignSystem.TextSecondary,
            indicatorColor = RtcDesignSystem.AccentBorder
        )
    )
}
