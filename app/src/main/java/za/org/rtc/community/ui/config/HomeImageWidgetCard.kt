package za.org.rtc.community.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import za.org.rtc.community.core.HomeImageAspectPreset
import za.org.rtc.community.core.HomeImageContentScale
import za.org.rtc.community.core.HomeImageWidget
import za.org.rtc.community.ui.theme.RtcAspectRatio
import za.org.rtc.community.ui.theme.RtcSpacing

/** Resident-safe renderer for the single optional, server-authorized Home image widget. */
@Composable
fun HomeImageWidgetCard(widget: HomeImageWidget) {
    val signedUrl = LocalRtcUiAssetUrls.current[widget.assetId]
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(widget.aspectPreset.ratio()),
                contentAlignment = Alignment.Center,
            ) {
                if (signedUrl != null) {
                    AsyncImage(
                        model = signedUrl,
                        contentDescription = widget.altText,
                        modifier = Modifier.fillMaxWidth().aspectRatio(widget.aspectPreset.ratio()),
                        contentScale = if (widget.contentScale == HomeImageContentScale.CROP) ContentScale.Crop else ContentScale.Fit,
                    )
                } else {
                    Text(
                        "Home image is temporarily unavailable.",
                        modifier = Modifier.padding(RtcSpacing.standard),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            widget.caption?.takeIf(String::isNotBlank)?.let { caption ->
                Text(
                    caption,
                    modifier = Modifier.padding(horizontal = RtcSpacing.small, vertical = RtcSpacing.compact),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun HomeImageAspectPreset.ratio(): Float = when (this) {
    HomeImageAspectPreset.WIDE -> RtcAspectRatio.landscape
    HomeImageAspectPreset.STANDARD -> 4f / 3f
    HomeImageAspectPreset.SQUARE -> RtcAspectRatio.square
}
