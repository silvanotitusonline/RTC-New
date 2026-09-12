
package za.org.rtc.community.feature.administration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.ui.theme.RtcDesignSystem

sealed class AdminSection {
    object OVERVIEW : AdminSection()
    object MODERATION : AdminSection()
    object CIVIC_ANALYTICS : AdminSection()
    object USER_MANAGEMENT : AdminSection()
    object SYSTEM_HEALTH : AdminSection()
}

@Composable
fun AdministrationHubScreen(viewModel: AdministrationDashboardViewModel) {
    val section by viewModel.currentSection.collectAsState()

    Row(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark)) {
        NavigationRail(
            containerColor = RtcDesignSystem.SurfaceDark,
            header = {
                Icon(Icons.Default.Settings, contentDescription = "Admin", tint = RtcDesignSystem.PrimaryBrand, modifier = Modifier.padding(12.dp))
            }
        ) {
            AdminNavMenuItem(AdminSection.OVERVIEW, "Overview", Icons.Default.Home, section, viewModel)
            AdminNavMenuItem(AdminSection.MODERATION, "Moderation", Icons.Default.CheckCircle, section, viewModel)
            AdminNavMenuItem(AdminSection.CIVIC_ANALYTICS, "Analytics", Icons.Default.List, section, viewModel)
            AdminNavMenuItem(AdminSection.USER_MANAGEMENT, "Users", Icons.Default.Person, section, viewModel)
            AdminNavMenuItem(AdminSection.SYSTEM_HEALTH, "Health", Icons.Default.Build, section, viewModel)
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when (section) {
                AdminSection.OVERVIEW -> Text("Overview", color = Color.White, modifier = Modifier.align(Alignment.Center))
                AdminSection.MODERATION -> ModerationCenterScreen()
                AdminSection.CIVIC_ANALYTICS -> AdminAnalyticsScreen()
                AdminSection.USER_MANAGEMENT -> Text("User Management", color = Color.White, modifier = Modifier.align(Alignment.Center))
                AdminSection.SYSTEM_HEALTH -> Text("System Health", color = Color.White, modifier = Modifier.align(Alignment.Center))
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
