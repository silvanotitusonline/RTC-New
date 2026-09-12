
package za.org.rtc.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import za.org.rtc.community.core.media.RtcMediaEngine
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun RtcEnterpriseImage(
    url: String?,
    resolution: RtcMediaEngine.Resolution = RtcMediaEngine.Resolution.FEED,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val optimizedUrl = remember(url) {
        url?.let { RtcMediaEngine.optimizeUrl(it, resolution) }
    }

    if (optimizedUrl == null) {
        Box(
            modifier = modifier.background(RtcDesignSystem.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text("No Image", color = RtcDesignSystem.TextSecondary, fontSize = 12.sp)
        }
    } else {
        SubcomposeAsyncImage(
            model = optimizedUrl,
            contentDescription = "Optimized Media",
            modifier = modifier,
            contentScale = contentScale,
            loading = {
                Box(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.SurfaceDark), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = RtcDesignSystem.PrimaryBrand)
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.SurfaceDark), contentAlignment = Alignment.Center) {
                    Text("!", color = RtcDesignSystem.TextSecondary)
                }
            }
        )
    }
}
