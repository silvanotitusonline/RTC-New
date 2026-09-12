package za.org.rtc.community.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Enhanced SwipeRefreshIndicator component for pull-to-refresh feeds.
 * Connects directly to PullToRefreshState with RTC brand breathing animation styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptr_breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath_scale",
    )
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath_alpha",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.testTag("swipe_refresh_indicator"),
    ) {
        if (isRefreshing) {
            // Outer breathing aura ring
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(breathScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFBBF24).copy(alpha = breathAlpha),
                                Color(0xFFF59E0B).copy(alpha = breathAlpha * 0.5f),
                                Color.Transparent,
                            )
                        ),
                        shape = CircleShape,
                    )
            )
        }
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            containerColor = containerColor,
            color = color,
        )
    }
}

/**
 * Standalone SwipeRefreshIndicator supporting custom state or direct boolean visibility with breathing effect.
 */
@Composable
fun SwipeRefreshIndicator(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = CircleShape,
    elevation: Dp = 6.dp,
    indicatorSize: Dp = 40.dp,
    strokeWidth: Dp = 3.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "standalone_ptr_breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "standalone_breath_scale",
    )

    AnimatedVisibility(
        visible = isRefreshing,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(indicatorSize + 12.dp)
                    .scale(breathScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                contentColor.copy(alpha = 0.4f),
                                Color.Transparent,
                            )
                        ),
                        shape = CircleShape,
                    )
            )
            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = elevation,
                shadowElevation = elevation,
                modifier = Modifier
                    .size(indicatorSize)
                    .testTag("swipe_refresh_indicator_standalone"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(8.dp),
                ) {
                    CircularProgressIndicator(
                        strokeWidth = strokeWidth,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
