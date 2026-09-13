package za.org.rtc.remediation.location

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun LocationControls(state: LocationState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestRefresh = rememberUpdatedState(onRefresh)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onRefresh()
    }
    // Also refresh after the user changes permission or GPS settings outside the app.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) latestRefresh.value() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            LocationState.PermissionRequired -> {
                Text("Use your location to calculate road distance. Approximate location is supported.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Use my location") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"))) }) { Text("App settings") }
                }
            }
            LocationState.SettingsDisabled -> {
                Text("Device location is switched off.")
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("Open location settings")
                }
            }
            LocationState.Searching -> {
                Text("Finding your current location…")
                CircularProgressIndicator()
            }
            is LocationState.Unavailable -> {
                Text(state.message)
                TextButton(onClick = onRefresh) { Text("Retry location") }
            }
            is LocationState.Available -> Text(if (state.fix.approximate)
                "Using approximate location; route distance is an estimate."
                else "Location accuracy: approximately ${state.fix.accuracyMeters.toInt()} m.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun RoadDistanceLabel(state: RouteState, modifier: Modifier = Modifier) {
    val label = when (state) {
        RouteState.SelectDestination -> "Choose an incident or service provider."
        RouteState.Loading -> "Calculating road distance…"
        is RouteState.Unavailable -> state.message
        is RouteState.Available -> "${if (state.approximateOrigin) "≈ " else ""}${state.distanceLabel} by road"
    }
    Text(label, modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
