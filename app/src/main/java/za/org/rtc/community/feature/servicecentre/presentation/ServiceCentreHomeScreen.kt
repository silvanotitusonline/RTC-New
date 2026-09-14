package za.org.rtc.community.feature.servicecentre.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreDiscoveryViewModel
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ServiceCentreHomeRoute(
    onNavigate: (String) -> Unit,
    viewModel: ServiceCentreDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.useMyLocation()
        }
    }
    val useMyLocation = {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarse || fine) viewModel.useMyLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) { viewModel.load() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(RtcSpacing.pageGutter),
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text("Service Centre", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Find a local provider and send a booking request in a few steps.")
                Button(onClick = { onNavigate(RtcRoute.SERVICE_CENTRE_BOOKINGS) }, modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.size(RtcSpacing.compact))
                    Text("Bookings")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                    OutlinedTextField(
                        value = state.locality,
                        onValueChange = viewModel::setLocality,
                        label = { Text("Town or suburb") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = viewModel::applyLocality, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Apply") }
                }
                OutlinedButton(onClick = useMyLocation, modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.size(RtcSpacing.compact))
                    Text("Use my location")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                ) {
                    FilterChip(selected = state.selectedCategoryId == null, onClick = { viewModel.selectCategory(null) }, label = { Text("All") })
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }
                ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Local Radar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (!state.loading && state.providers.isEmpty()) Text("No active providers match this area yet.")
            }
        }
        items(state.providers, key = { it.providerUserId }) { provider ->
            ServiceCentreProviderCard(
                provider = provider,
                onRequest = { onNavigate(RtcRoute.serviceCentreRequest(provider.providerUserId)) },
            )
        }
    }
}
