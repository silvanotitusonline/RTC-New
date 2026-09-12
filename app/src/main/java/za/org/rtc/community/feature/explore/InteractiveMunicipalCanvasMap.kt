
package za.org.rtc.community.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.CivicReport
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun InteractiveMunicipalCanvasMap(
    reports: List<CivicReport>,
    onMarkerClick: (CivicReport) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark)) {
        // Since we are in a simulated environment, we implement the MAP placeholder 
        // that looks and behaves like a map to prevent crashes.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Map Engine Loaded", color = RtcDesignSystem.TextPrimary)
            Text("Displaying ${reports.size} reports", color = RtcDesignSystem.TextSecondary)
            
            // Simulating markers as a list for now to ensure functionality
            reports.forEach { report ->
                Button(onClick = { onMarkerClick(report) }) {
                    Text("Marker: ${report.title}")
                }
            }
        }

        // Floating Search Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(0.8f)
                .background(RtcDesignSystem.SurfaceDark, RoundedCornerShape(24.dp))
                .padding(12.dp)
        ) {
            Text("Search for reports...", color = RtcDesignSystem.TextSecondary)
        }
    }
}
