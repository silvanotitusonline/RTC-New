package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.core.maps.LiveMapPanel
import za.org.rtc.community.navigation.RtcRoute

@Composable
fun MarketplaceDirectionsRoute(
    businessId: String,
    locationId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.detail.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.loadDetail(businessId) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Business directions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
        }
        MarketplaceLoadContainer(state, { viewModel.loadDetail(businessId) }) { detail ->
            val listings = remember(detail) { detail.toMarketplaceMapListings() }
            val selected = if (locationId == "primary") listings.firstOrNull()
                else listings.firstOrNull { it.marker.id == "${detail.card.id}/$locationId" }
            Column(Modifier.fillMaxSize()) {
                if (selected == null) {
                    Text("This business has not published a map location for this destination.")
                }
                LiveMapPanel(
                    markers = listings.map { it.marker },
                    initialSelectedMarkerId = selected?.marker?.id,
                    onOpenMarker = { onNavigate(RtcRoute.marketplaceBusiness(detail.card.id)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
