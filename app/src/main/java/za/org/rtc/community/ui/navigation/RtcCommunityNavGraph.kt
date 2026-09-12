
package za.org.rtc.community.ui.navigation


fun validateRouteParams(params: Map<String, String?>, required: List<String>): Boolean {
    return required.all { param -> params[param] != null && params[param] != "null" && params[param]!!.isNotEmpty() }
}


import androidx.compose.runtime.*
import androidx.navigation.compose.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import za.org.rtc.community.feature.administration.*
import za.org.rtc.community.feature.community.*
import za.org.rtc.community.feature.marketplace.presentation.*
import za.org.rtc.community.feature.home.*
import za.org.rtc.community.feature.account.*

@Composable
fun RtcCommunityNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("explore") { ExploreScreen() }
        composable("marketplace") { MarketplaceHomeScreen() }
        composable("admin") { AdministrationHubScreen(AdminDashboardViewModel()) }
        composable("account") { AccountScreen(AccountViewModel()) }
    }
}
