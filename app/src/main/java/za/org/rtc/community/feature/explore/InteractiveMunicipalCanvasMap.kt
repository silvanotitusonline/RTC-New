package za.org.rtc.community.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun InteractiveMunicipalCanvasMap(
    verifiedReports: List<PublicReport>,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    RtcCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Community Activity Map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${verifiedReports.size} verified municipal reports in your area.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
