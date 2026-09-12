package za.org.rtc.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import za.org.rtc.community.core.media.RtcMediaEngine

/**
 * Single rendering path for every remote image in the app.
 *
 * URLs are passed through [RtcMediaEngine] so transformation parameters are
 * applied consistently, and the loading and error slots are filled here rather
 * than re-implemented per screen.
 */
@Composable
fun RtcEnterpriseImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    resolution: RtcMediaEngine.Resolution = RtcMediaEngine.Resolution.FEED,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val optimized = remember(url, resolution) { RtcMediaEngine.optimizeUrl(url, resolution) }

    if (optimized.isNullOrBlank()) {
        RtcImagePlaceholder(modifier = modifier)
        return
    }

    SubcomposeAsyncImage(
        model = optimized,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
            }
        },
        error = {
            RtcImagePlaceholder(modifier = Modifier.fillMaxSize())
        },
    )
}

@Composable
private fun RtcImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Image unavailable",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
