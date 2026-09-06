package za.org.rtc.community.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.compositionLocalOf

/**
 * Material Design 3 Emphasized Easing curve: (0.2, 0.0, 0.0, 1.0).
 * Standardized across the app for card container expansions and primary axis shifts.
 */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

/**
 * Standard 500ms duration for Card expanding to full screen container transformation.
 */
const val CardExpandDurationMs = 500

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
