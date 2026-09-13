package za.org.rtc.remediation.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.remediation.location.LocationControls
import za.org.rtc.remediation.location.LocationState
import za.org.rtc.remediation.location.LocationViewModel
import za.org.rtc.remediation.location.MapTarget
import za.org.rtc.remediation.location.RoadDistanceLabel

/** Pass an incident/provider's actual stored coordinates; null does not invent a destination. */
@Composable
fun LiveMapScreen(apiKey: String, target: MapTarget?, viewModel: LocationViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(target, viewModel) { viewModel.selectTarget(target) }
    val location by viewModel.location.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val fix = (location as? LocationState.Available)?.fix
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(target?.title ?: "Nearby locations")
        LocationControls(location, viewModel::refreshPermissionAndSettings)
        RtcTomTomMap(apiKey, target, fix?.coordinates, Modifier.fillMaxWidth().height(360.dp))
        RoadDistanceLabel(route)
        if (route is za.org.rtc.remediation.location.RouteState.Available) {
            Text("Driving distance includes available traffic information; it is not a straight-line estimate.")
        }
    }
}
