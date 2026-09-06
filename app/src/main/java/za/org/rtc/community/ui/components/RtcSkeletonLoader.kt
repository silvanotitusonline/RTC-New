package za.org.rtc.community.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Modifier that applies a subtle pulsing animation to simulate skeleton loading
 * and stabilize the UI layout as dynamic content transitions in.
 */
@Composable
fun Modifier.skeletonPulse(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Modifier {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse_transition")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    return this
        .clip(shape)
        .background(baseColor.copy(alpha = alpha))
}

/**
 * Generic skeleton placeholder box with subtle pulse animation.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(
        modifier = modifier
            .then(widthModifier)
            .height(height)
            .skeletonPulse(shape = shape)
    )
}

/**
 * Skeleton loader designed for Community Post cards & detail views to stabilize the layout.
 */
@Composable
fun PostCardSkeleton(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(RtcSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        ) {
            // Header with Avatar & User details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                Box(
                    modifier = Modifier
                        .size(RtcSize.avatarStandard)
                        .skeletonPulse(shape = CircleShape)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    SkeletonBox(height = 14.dp, width = 120.dp)
                    SkeletonBox(height = 10.dp, width = 80.dp)
                }
            }

            Spacer(Modifier.height(RtcSpacing.compact))

            // Body text lines
            SkeletonBox(height = 14.dp)
            SkeletonBox(height = 14.dp)
            SkeletonBox(height = 14.dp, width = 200.dp)

            Spacer(Modifier.height(RtcSpacing.compact))
            Divider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 1.dp,
            )

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SkeletonBox(height = 24.dp, width = 60.dp)
                SkeletonBox(height = 24.dp, width = 60.dp)
                SkeletonBox(height = 24.dp, width = 60.dp)
            }
        }
    }
}

/**
 * Skeleton loader designed for Service Centre Booking cards & detail views.
 */
@Composable
fun BookingCardSkeleton(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(RtcSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(height = 18.dp, width = 140.dp)
                SkeletonBox(height = 24.dp, width = 70.dp, shape = RoundedCornerShape(12.dp))
            }
            SkeletonBox(height = 14.dp, width = 100.dp)
            SkeletonBox(height = 12.dp, width = 180.dp)
            SkeletonBox(height = 14.dp)
            SkeletonBox(height = 16.dp, width = 110.dp)
            SkeletonBox(height = 36.dp, shape = RoundedCornerShape(8.dp))
        }
    }
}
