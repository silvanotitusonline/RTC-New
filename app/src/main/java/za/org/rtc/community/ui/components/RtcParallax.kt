package za.org.rtc.community.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import za.org.rtc.community.ui.animation.LocalReducedMotion

/**
 * CompositionLocal providing access to the nearest LazyListState for coordinated parallax scrolling.
 */
val LocalLazyListState = compositionLocalOf<LazyListState?> { null }

/**
 * Applies a smooth and deliberate vertical parallax offset to list items based on their distance
 * from the viewport center as the user scrolls.
 *
 * @param index The index of the item within the LazyColumn.
 * @param rate Parallax displacement factor. Default is 0.08f for smooth, authentic physical depth.
 * @param state The active [LazyListState]. Defaults to [LocalLazyListState.current].
 */
@Composable
fun Modifier.parallaxScrollItem(
    index: Int,
    rate: Float = 0.08f,
    state: LazyListState? = LocalLazyListState.current,
): Modifier {
    if (LocalReducedMotion.current || state == null) return this
    return this.graphicsLayer {
        val visibleItems = state.layoutInfo.visibleItemsInfo
        val itemInfo = visibleItems.firstOrNull { it.index == index }
        if (itemInfo != null) {
            val viewportHeight = state.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight > 0f) {
                val viewportCenter = viewportHeight / 2f
                val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                val distanceFromCenter = itemCenter - viewportCenter
                // Smooth translation along primary scroll axis to give pronounced, deliberate depth
                translationY = distanceFromCenter * rate
            }
        }
    }
}

/**
 * Applies a pronounced parallax effect to hero banners and top list headers, causing them
 * to scroll at a slower deliberate pace (e.g. 0.35x speed) relative to the foreground cards.
 *
 * @param rate Speed multiplier for header parallax offset.
 * @param state The active [LazyListState]. Defaults to [LocalLazyListState.current].
 */
@Composable
fun Modifier.parallaxHeader(
    rate: Float = 0.35f,
    state: LazyListState? = LocalLazyListState.current,
): Modifier {
    if (LocalReducedMotion.current || state == null) return this
    return this.graphicsLayer {
        if (state.firstVisibleItemIndex == 0) {
            // Header translates slower than scroll velocity for a pronounced parallax effect
            translationY = state.firstVisibleItemScrollOffset * rate
        }
    }
}

/**
 * Applies an internal parallax depth to media/images inside cards, shifting slightly
 * as the card crosses the center focal region of the screen.
 *
 * @param index The parent card's list index.
 * @param rate Shift factor within media bounds.
 * @param state The active [LazyListState]. Defaults to [LocalLazyListState.current].
 */
@Composable
fun Modifier.parallaxMedia(
    index: Int,
    rate: Float = 0.12f,
    state: LazyListState? = LocalLazyListState.current,
): Modifier {
    if (LocalReducedMotion.current || state == null) return this
    return this.graphicsLayer {
        val visibleItems = state.layoutInfo.visibleItemsInfo
        val itemInfo = visibleItems.firstOrNull { it.index == index }
        if (itemInfo != null) {
            val viewportHeight = state.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight > 0f) {
                val viewportCenter = viewportHeight / 2f
                val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                val distanceFromCenter = itemCenter - viewportCenter
                translationY = (distanceFromCenter * rate).coerceIn(-48f, 48f)
            }
        }
    }
}

