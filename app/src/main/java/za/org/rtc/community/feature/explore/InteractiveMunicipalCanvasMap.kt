
package za.org.rtc.community.feature.explore

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun InteractiveMunicipalCanvasMap(
    reports: List<CivicReport>,
    onMarkerClick: (CivicReport) -> Unit
) {
    // Custom TomTom Map Style Configuration
    val mapStyle = mapOf(
        "backgroundColor" to RtcDesignSystem.BackgroundDark,
        "roadColor" to RtcDesignSystem.AccentBorder,
        "waterColor" to Color(0xFF001A33),
        "poiColor" to RtcDesignSystem.PrimaryBrand,
        "labelColor" to RtcDesignSystem.TextSecondary
    )

    // Implementation of the MapView with the custom style
    // We add "Heatmap" layers for civic reports
    Box(modifier = Modifier.fillMaxSize()) {
        TomTomMapView(
            style = mapStyle,
            markers = reports,
            onMarkerClick = onMarkerClick,
            showHeatmap = true // Enabled for "Civic Intelligence"
        )
        
        // Floating Search Overlay (Modern Floating UI)
        MapSearchOverlay(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
        )
    }
}
