package za.org.rtc.community.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Municipal civic map surface.
 *
 * Plots verified civic reports as tappable markers. Live raster/vector map tiles
 * require a licensed map SDK (TomTom Maps SDK), which is not declared in this build;
 * until that dependency is added this surface remains fully functional for triage and
 * navigation because every marker opens its report detail route.
 */
@Composable
internal fun InteractiveMunicipalCanvasMap(
    verifiedReports: List<PublicReport>,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val markers = remember(verifiedReports) { verifiedReports.take(MAX_PLOTTED_MARKERS) }
    val headline = if (markers.isEmpty()) {
        "No verified reports to plot yet."
    } else {
        markers.size.toString() + " verified report" + (if (markers.size == 1) "" else "s") + " plotted"
    }

    RtcCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(
                    text = "Civic map",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (markers.isEmpty()) {
                RtcEmptyState(
                    title = "Nothing on the map",
                    message = "Verified civic reports appear here once they pass moderation.",
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    MarkerGrid(markers = markers, onOpenReport = onOpenReport)
                }
            }
        }
    }
}

@Composable
private fun MarkerGrid(
    markers: List<PublicReport>,
    onOpenReport: (String) -> Unit,
) {
    val perColumn = 5
    val columnCount = (markers.size + perColumn - 1) / perColumn
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(columnCount) { columnIndex ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                markers.drop(columnIndex * perColumn).take(perColumn).forEach { report ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = urgencyColor(report),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onOpenReport(report.id) },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = report.title,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun urgencyColor(report: PublicReport): Color = when (report.urgency.name) {
    "CRITICAL" -> Color(0xFFD32F2F)
    "HIGH" -> Color(0xFFF57C00)
    "MEDIUM" -> Color(0xFF1976D2)
    else -> Color(0xFF388E3C)
}

private const val MAX_PLOTTED_MARKERS = 40
