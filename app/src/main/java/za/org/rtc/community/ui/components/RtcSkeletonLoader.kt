package za.org.rtc.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RtcSkeletonLoader() {
    var alpha by remember { mutableStateOf(0.3f) }
    LaunchedEffect(Unit) {
        while (true) {
            alpha = 0.7f
            delay(800)
            alpha = 0.3f
            delay(800)
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.LightGray.copy(alpha = alpha)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
            Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
        }
    }
}

@Composable
fun PostCardSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        RtcSkeletonLoader()
    }
}

@Composable
fun BookingCardSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        RtcSkeletonLoader()
    }
}

@Composable
fun SkeletonBox(
    height: Dp,
    width: Dp = Dp.Unspecified,
    shape: Shape = RoundedCornerShape(4.dp),
    modifier: Modifier = Modifier,
) {
    val mod = if (width != Dp.Unspecified) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = mod
            .height(height)
            .skeletonPulse(shape = shape)
    )
}

fun Modifier.skeletonPulse(
    shape: Shape = RoundedCornerShape(4.dp),
): Modifier = composed {
    var alpha by remember { mutableStateOf(0.3f) }
    LaunchedEffect(Unit) {
        while (true) {
            alpha = 0.7f
            delay(800)
            alpha = 0.3f
            delay(800)
        }
    }
    this.clip(shape).background(Color.LightGray.copy(alpha = alpha))
}
