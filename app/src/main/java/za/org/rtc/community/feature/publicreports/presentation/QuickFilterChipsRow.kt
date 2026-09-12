package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcSpacing

val QuickFilterTags = listOf(
    "All",
    "Pothole",
    "Streetlight",
    "Water Main",
    "Sanitation",
    "Graffiti",
    "Sidewalk",
    "Urgent",
    "Traffic",
)

fun getQuickFilterIcon(tag: String): ImageVector {
    return when (tag) {
        "Pothole" -> Icons.Default.Construction
        "Streetlight" -> Icons.Default.Lightbulb
        "Water Main" -> Icons.Default.WaterDrop
        "Sanitation" -> Icons.Default.CleaningServices
        "Graffiti" -> Icons.Default.Brush
        "Sidewalk" -> Icons.Default.DirectionsWalk
        "Urgent" -> Icons.Default.Warning
        "Traffic" -> Icons.Default.Traffic
        else -> Icons.Default.Apps
    }
}

@Composable
fun QuickFilterChipsRow(
    selectedTag: String?,
    onSelectTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
    ) {
        QuickFilterTags.forEach { tag ->
            val isSelected = if (tag == "All") selectedTag == null else selectedTag == tag
            val icon = getQuickFilterIcon(tag)

            FilterChip(
                selected = isSelected,
                onClick = { onSelectTag(tag) },
                label = {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Quick filter by $tag${if (isSelected) ", selected" else ""}"
                },
            )
        }
    }
}
