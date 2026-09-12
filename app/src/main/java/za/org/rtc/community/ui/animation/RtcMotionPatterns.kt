package za.org.rtc.community.ui.animation

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal indicating whether reduced motion is requested.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Material Design 3 Motion Tokens and Specifications:
 * Reference: https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 *
 * 1. Easing Tokens:
 *    - Linear: CubicBezier(0.0, 0.0, 1.0, 1.0)
 *    - Standard: CubicBezier(0.2, 0.0, 0.0, 1.0)
 *    - Standard Decelerate: CubicBezier(0.0, 0.0, 0.2, 1.0)
 *    - Standard Accelerate: CubicBezier(0.4, 0.0, 1.0, 1.0)
 *    - Emphasized: CubicBezier(0.2, 0.0, 0.0, 1.0)
 *    - Emphasized Decelerate: CubicBezier(0.05, 0.7, 0.1, 1.0)
 *    - Emphasized Accelerate: CubicBezier(0.3, 0.0, 0.8, 0.15)
 *    - Legacy: CubicBezier(0.4, 0.0, 0.2, 1.0)
 *    - Legacy Decelerate: CubicBezier(0.0, 0.0, 0.2, 1.0)
 *    - Legacy Accelerate: CubicBezier(0.4, 0.0, 1.0, 1.0)
 *
 * 2. Duration Tokens:
 *    - Short: 1 (50ms), 2 (100ms), 3 (150ms), 4 (200ms)
 *    - Medium: 1 (250ms), 2 (300ms), 3 (350ms), 4 (400ms)
 *    - Long: 1 (450ms), 2 (500ms), 3 (550ms), 4 (600ms)
 *    - Extra Long: 1 (700ms), 2 (800ms), 3 (900ms), 4 (1000ms)
 */
object RtcMotionPatterns {
    // -------------------------------------------------------------
    // 1. Material 3 Duration Tokens (tokens-specs)
    // -------------------------------------------------------------
    const val DurationShort1: Int = 50
    const val DurationShort2: Int = 100
    const val DurationShort3: Int = 150
    const val DurationShort4: Int = 200

    const val DurationMedium1: Int = 250
    const val DurationMedium2: Int = 300
    const val DurationMedium3: Int = 350
    const val DurationMedium4: Int = 400

    const val DurationLong1: Int = 450
    const val DurationLong2: Int = 500
    const val DurationLong3: Int = 550
    const val DurationLong4: Int = 600

    const val DurationExtraLong1: Int = 700
    const val DurationExtraLong2: Int = 800
    const val DurationExtraLong3: Int = 900
    const val DurationExtraLong4: Int = 1000

    // Semantic Material 3 aliases
    const val DurationEmphasized: Int = DurationLong2              // 500ms (Begin & end on screen)
    const val DurationEmphasizedDecelerate: Int = DurationMedium4  // 400ms (Enter the screen)
    const val DurationEmphasizedAccelerate: Int = DurationShort4   // 200ms (Exit the screen)

    const val DurationStandard: Int = DurationMedium2              // 300ms (Begin & end on screen)
    const val DurationStandardDecelerate: Int = DurationMedium1    // 250ms (Enter the screen)
    const val DurationStandardAccelerate: Int = DurationShort4     // 200ms (Exit the screen)

    // Legacy and semantic aliases
    const val ContainerTransformDurationMs: Int = DurationEmphasized
    const val LateralDurationMs: Int = DurationStandard
    const val EnterDurationMs: Int = DurationEmphasizedDecelerate
    const val ExitDurationMs: Int = DurationEmphasizedAccelerate
    const val ReducedMotionDurationMs: Int = DurationStandardAccelerate

    // -------------------------------------------------------------
    // 2. Material 3 Easing Curves (tokens-specs)
    // -------------------------------------------------------------
    // Linear
    val LinearEasing: Easing = androidx.compose.animation.core.LinearEasing

    // Emphasized
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Standard
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val StandardAccelerateEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // Legacy M2 Curves
    val LegacyEasing: Easing = FastOutSlowInEasing
    val LegacyDecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val LegacyAccelerateEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // Default general motion curve alias
    val MotionEasing: Easing = StandardEasing

    // -------------------------------------------------------------
    // 3. Material 3 Standard Tween Helpers
    // -------------------------------------------------------------
    fun <T> emphasizedTween(delayMillis: Int = 0) = tween<T>(
        durationMillis = DurationEmphasized,
        delayMillis = delayMillis,
        easing = EmphasizedEasing,
    )

    fun <T> emphasizedDecelerateTween(delayMillis: Int = 0) = tween<T>(
        durationMillis = DurationEmphasizedDecelerate,
        delayMillis = delayMillis,
        easing = EmphasizedDecelerateEasing,
    )

    fun <T> emphasizedAccelerateTween(delayMillis: Int = 0) = tween<T>(
        durationMillis = DurationEmphasizedAccelerate,
        delayMillis = delayMillis,
        easing = EmphasizedAccelerateEasing,
    )

    fun <T> standardTween(delayMillis: Int = 0) = tween<T>(
        durationMillis = DurationStandard,
        delayMillis = delayMillis,
        easing = StandardEasing,
    )

    fun <T> standardDecelerateTween(delayMillis: Int = 0) = tween<T>(
        durationMillis = DurationStandardDecelerate,
        delayMillis = delayMillis,
        easing = StandardDecelerateEasing,
    )

    fun <T> standardAccelerateTween(delayMillis: Int = 0) = tween<T>(
        durationMillis = DurationStandardAccelerate,
        delayMillis = delayMillis,
        easing = StandardAccelerateEasing,
    )

    /**
     * Checks if the device has reduced animations/motion enabled.
     */
    fun isReducedMotion(context: Context): Boolean {
        return try {
            val resolver = context.contentResolver
            val transitionScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            val animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            transitionScale == 0f || animatorScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    // ==========================================
    // 1. CONTAINER TRANSFORM (MAJOR SCREEN TRANSITIONS)
    // ==========================================

    /**
     * Container Transform enter transition for major screen navigation:
     * Material 3 Emphasized Decelerate (400ms) with Emphasized Decelerate easing.
     * Content scales smoothly into place from 0.94f to 1.0f with an incoming fade.
     * When reduced motion is active, uses a subtle, non-disorienting fade.
     */
    fun navigationEnterTransition(reducedMotion: Boolean = false): EnterTransition {
        return if (reducedMotion) {
            fadeIn(animationSpec = tween(ReducedMotionDurationMs, easing = StandardDecelerateEasing))
        } else {
            scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(DurationEmphasizedDecelerate, easing = EmphasizedDecelerateEasing),
            ) + fadeIn(
                animationSpec = tween(150, easing = StandardDecelerateEasing),
            )
        }
    }

    /**
     * Container Transform exit transition for major screen navigation:
     * Material 3 Emphasized Accelerate (200ms) with Emphasized Accelerate easing.
     * Outgoing content scales down slightly to 0.96f with a short exit fade.
     */
    fun navigationExitTransition(reducedMotion: Boolean = false): ExitTransition {
        return if (reducedMotion) {
            fadeOut(animationSpec = tween(ReducedMotionDurationMs, easing = StandardAccelerateEasing))
        } else {
            scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(DurationEmphasizedAccelerate, easing = EmphasizedAccelerateEasing),
            ) + fadeOut(
                animationSpec = tween(DurationEmphasizedAccelerate, easing = EmphasizedAccelerateEasing),
            )
        }
    }

    /**
     * Container Transform pop-enter transition (returning back up the hierarchy):
     * Material 3 Emphasized Decelerate (400ms) with Emphasized Decelerate easing, scaling from 0.96f to 1.0f.
     */
    fun navigationPopEnterTransition(reducedMotion: Boolean = false): EnterTransition {
        return if (reducedMotion) {
            fadeIn(animationSpec = tween(ReducedMotionDurationMs, easing = StandardDecelerateEasing))
        } else {
            scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(DurationEmphasizedDecelerate, easing = EmphasizedDecelerateEasing),
            ) + fadeIn(
                animationSpec = tween(150, easing = StandardDecelerateEasing),
            )
        }
    }

    /**
     * Container Transform pop-exit transition (collapsing child screen):
     * Material 3 Emphasized Accelerate (200ms) with Emphasized Accelerate easing, scaling down from 1.0f to 0.94f.
     */
    fun navigationPopExitTransition(reducedMotion: Boolean = false): ExitTransition {
        return if (reducedMotion) {
            fadeOut(animationSpec = tween(ReducedMotionDurationMs, easing = StandardAccelerateEasing))
        } else {
            scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(DurationEmphasizedAccelerate, easing = EmphasizedAccelerateEasing),
            ) + fadeOut(
                animationSpec = tween(DurationEmphasizedAccelerate, easing = EmphasizedAccelerateEasing),
            )
        }
    }

    // ==========================================
    // 2. LATERAL TRANSITIONS (PEER CONTENT / TABS)
    // ==========================================

    /**
     * Lateral peer transition for navigating between content at the same level of hierarchy (e.g. Tabs).
     * Material 3 Standard easing (300ms, Begin and end on screen).
     * Elements slide in unison with NO fade and NO parallax, creating a strong peer relationship.
     */
    fun lateralTransitionSpec(
        durationMs: Int = DurationStandard,
        easing: Easing = StandardEasing,
    ): AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
        if (targetState > initialState) {
            // Forward (e.g. Tab 0 -> 1): Slide left in unison (100% full width, no parallax, no fade)
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(durationMs, easing = easing),
                initialOffset = { it },
            ) togetherWith slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(durationMs, easing = easing),
                targetOffset = { it },
            )
        } else {
            // Backward (e.g. Tab 1 -> 0): Slide right in unison (100% full width, no parallax, no fade)
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMs, easing = easing),
                initialOffset = { it },
            ) togetherWith slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMs, easing = easing),
                targetOffset = { it },
            )
        }
    }

    // ==========================================
    // 3. ENTER & EXIT (WITHIN SCREEN BOUNDS)
    // ==========================================

    /**
     * FAB enter transition: Material 3 Emphasized Decelerate (400ms).
     * Uniformly scales as it enters, expanding away from the device edge (bottom-right corner origin).
     */
    fun fabEnterTransition(
        durationMs: Int = DurationEmphasizedDecelerate,
        origin: TransformOrigin = TransformOrigin(1f, 1f),
    ): EnterTransition {
        return scaleIn(
            initialScale = 0.75f,
            transformOrigin = origin,
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        ) + fadeIn(
            animationSpec = tween((durationMs * 0.5f).toInt(), easing = StandardDecelerateEasing),
        )
    }

    /**
     * FAB exit transition: Material 3 Emphasized Accelerate (200ms).
     * Fades out to exit with uniform scale reduction.
     */
    fun fabExitTransition(
        durationMs: Int = DurationEmphasizedAccelerate,
        origin: TransformOrigin = TransformOrigin(1f, 1f),
    ): ExitTransition {
        return scaleOut(
            targetScale = 0.75f,
            transformOrigin = origin,
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        ) + fadeOut(
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        )
    }

    /**
     * Snackbar enter transition: Material 3 Emphasized Decelerate (400ms).
     * Located at bottom of screen, expands upwards away from the device bottom edge.
     */
    fun snackbarEnterTransition(
        durationMs: Int = DurationEmphasizedDecelerate,
    ): EnterTransition {
        return slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        ) + scaleIn(
            initialScale = 0.88f,
            transformOrigin = TransformOrigin(0.5f, 1f),
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        ) + fadeIn(
            animationSpec = tween((durationMs * 0.6f).toInt(), easing = StandardDecelerateEasing),
        )
    }

    /**
     * Snackbar exit transition: Material 3 Emphasized Accelerate (200ms).
     * Fades out to exit and slides down toward bottom edge.
     */
    fun snackbarExitTransition(
        durationMs: Int = DurationEmphasizedAccelerate,
    ): ExitTransition {
        return slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        ) + scaleOut(
            targetScale = 0.9f,
            transformOrigin = TransformOrigin(0.5f, 1f),
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        ) + fadeOut(
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        )
    }

    /**
     * Menu enter transition: Material 3 Emphasized Decelerate (400ms).
     * Located near the top of the screen, expands downwards away from the device top edge.
     */
    fun menuEnterTransition(
        durationMs: Int = DurationEmphasizedDecelerate,
    ): EnterTransition {
        return expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        ) + scaleIn(
            initialScale = 0.85f,
            transformOrigin = TransformOrigin(0.5f, 0f),
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        ) + fadeIn(
            animationSpec = tween((durationMs * 0.6f).toInt(), easing = StandardDecelerateEasing),
        )
    }

    /**
     * Menu exit transition: Material 3 Emphasized Accelerate (200ms).
     * Fades out and shrinks upwards toward top edge.
     */
    fun menuExitTransition(
        durationMs: Int = DurationEmphasizedAccelerate,
    ): ExitTransition {
        return shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        ) + fadeOut(
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        )
    }

    /**
     * Dialog enter transition: Material 3 Emphasized Decelerate (400ms).
     * Within screen bounds, uniformly scales as it enters over an app.
     */
    fun dialogEnterTransition(
        durationMs: Int = DurationEmphasizedDecelerate,
    ): EnterTransition {
        return scaleIn(
            initialScale = 0.82f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        ) + fadeIn(
            animationSpec = tween((durationMs * 0.6f).toInt(), easing = StandardDecelerateEasing),
        )
    }

    /**
     * Dialog exit transition: Material 3 Emphasized Accelerate (200ms).
     * Fades out to exit.
     */
    fun dialogExitTransition(
        durationMs: Int = DurationEmphasizedAccelerate,
    ): ExitTransition {
        return scaleOut(
            targetScale = 0.82f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        ) + fadeOut(
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        )
    }

    // ==========================================
    // 4. ENTER & EXIT (CROSSING SCREEN BOUNDS)
    // ==========================================

    /**
     * Bottom sheet enter transition: Material 3 Emphasized Decelerate (400ms).
     * Slides on screen crossing bottom boundary.
     */
    fun sheetEnterTransition(durationMs: Int = DurationEmphasizedDecelerate): EnterTransition {
        return slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        )
    }

    /**
     * Bottom sheet exit transition: Material 3 Emphasized Accelerate (200ms).
     * Slides off screen crossing bottom boundary.
     */
    fun sheetExitTransition(durationMs: Int = DurationEmphasizedAccelerate): ExitTransition {
        return slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        )
    }

    /**
     * Navigation drawer enter transition: Material 3 Emphasized Decelerate (400ms).
     * Slides on screen crossing start/left boundary.
     */
    fun drawerEnterTransition(durationMs: Int = DurationEmphasizedDecelerate): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(durationMs, easing = EmphasizedDecelerateEasing),
        )
    }

    /**
     * Navigation drawer exit transition: Material 3 Emphasized Accelerate (200ms).
     * Slides off screen crossing start/left boundary.
     */
    fun drawerExitTransition(durationMs: Int = DurationEmphasizedAccelerate): ExitTransition {
        return slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(durationMs, easing = EmphasizedAccelerateEasing),
        )
    }
}

/**
 * Reusable animated FAB wrapper providing Material 3 enter and exit transitions:
 * Uniformly scales as it enters, expanding away from the bottom-right corner, and fades out on exit.
 */
@Composable
fun RtcMotionFab(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = RtcMotionPatterns.fabEnterTransition(),
        exit = RtcMotionPatterns.fabExitTransition(),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Reusable animated Snackbar wrapper providing Material 3 enter and exit transitions:
 * Positioned at the bottom of the screen, expanding upwards away from the bottom device edge,
 * and fading out to exit.
 */
@Composable
fun RtcMotionSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Snackbar(
            snackbarData = snackbarData,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/**
 * Animated menu container expanding downwards away from the top edge.
 */
@Composable
fun RtcMotionMenuContainer(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = RtcMotionPatterns.menuEnterTransition(),
        exit = RtcMotionPatterns.menuExitTransition(),
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            content()
        }
    }
}

val LocalSnackbarHostState = androidx.compose.runtime.compositionLocalOf<androidx.compose.material3.SnackbarHostState?> { null }

/**
 * Dialog content container that uniformly scales as it enters within screen bounds and fades out to exit.
 */
@Composable
fun RtcMotionDialogContainer(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = RtcMotionPatterns.dialogEnterTransition(),
        exit = RtcMotionPatterns.dialogExitTransition(),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Material Design Alert Dialog with enter & exit motion within screen bounds:
 * Uniformly scales as it enters over an app and fades out to exit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RtcMotionAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        RtcMotionDialogContainer {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            title()
                        }
                    }
                    if (text != null) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            text()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}
