package za.org.rtc.community.feature.explore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.maps.LiveMapPanel
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.publicMapMarker
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

/** Native map of the server's public, rounded civic report projection. */
@Composable
internal fun InteractiveMunicipalCanvasMap(
    verifiedReports: List<PublicReport>,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val eligible = remember(verifiedReports) { verifiedReports.filter { it.verified } }
    val markers = remember(eligible) { eligible.mapNotNull { it.publicMapMarker() }.take(100) }
    val withoutCoordinates = remember(eligible) { eligible.count { it.publicMapMarker() == null } }
    RtcCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Civic map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${markers.size} verified reports with approximate public locations",
                style = MaterialTheme.typography.bodySmall,
            )
            if (withoutCoordinates > 0) {
                Text("$withoutCoordinates reports have no published coordinates.", style = MaterialTheme.typography.bodySmall)
            }
            if (eligible.size - withoutCoordinates > markers.size) {
                Text("Showing the first 100 mapped reports.", style = MaterialTheme.typography.bodySmall)
            }
            LiveMapPanel(
                markers = markers,
                onOpenMarker = onOpenReport,
                modifier = Modifier.fillMaxWidth().height(560.dp),
            )
        }
    }
}
