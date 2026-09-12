package za.org.rtc.community.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared pulse alpha so every placeholder breathes in lockstep. */
@Composable
internal fun skeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "rtc-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rtc-skeleton-alpha",
    )
    return alpha
}

/** A single shimmering placeholder block. */
@Composable
fun SkeletonBox(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    val alpha = skeletonPulse()
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.35f)),
    )
}

/**
 * Placeholder that mirrors the real Community post layout so the feed does not
 * reflow when data lands.
 */
@Composable
fun PostCardSkeleton(modifier: Modifier = Modifier) {
    val alpha = skeletonPulse()
    val block = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.35f)
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(block))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(110.dp).height(12.dp).clip(shape).background(block))
                Box(Modifier.width(60.dp).height(12.dp).clip(shape).background(block))
            }
            Box(Modifier.fillMaxWidth().height(12.dp).clip(shape).background(block))
            Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(shape).background(block))
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(block))
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.width(44.dp).height(12.dp).clip(shape).background(block))
                Box(Modifier.width(44.dp).height(12.dp).clip(shape).background(block))
                Box(Modifier.width(44.dp).height(12.dp).clip(shape).background(block))
            }
        }
    }
}

/** Generic inline loader retained for older call sites. */
@Composable
fun RtcSkeletonLoader() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { PostCardSkeleton() }
    }
}

/** Neutral placeholder shown when a remote image cannot be resolved. */
@Composable
fun RtcImageFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
