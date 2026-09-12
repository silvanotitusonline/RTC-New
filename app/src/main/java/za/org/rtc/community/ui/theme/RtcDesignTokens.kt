package za.org.rtc.community.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fibonacci-backed spatial rhythm. Use semantic aliases in component code. */
object RtcSpacing {
    val micro = 3.dp
    val tiny = 5.dp
    val compact = 8.dp
    val small = 13.dp
    val standard = 21.dp
    val section = 34.dp
    val largeSection = 55.dp
    val hero = 89.dp

    val opticalCorrection = micro
    val relatedText = tiny
    val iconLabel = compact
    val controlGap = compact
    val denseGroup = compact
    val contentGroup = small
    val listGap = small
    val cardGap = small
    val pageGutter = standard
    val cardPadding = standard
    val dialogPadding = standard
    val sectionGap = section
}

/** Controlled non-circular corner progression. Circular elements use CircleShape. */
object RtcRadius {
    val small = 8.dp
    val medium = 13.dp
    val large = 21.dp
    val hero = 34.dp
}

/**
 * Component sizes. Values outside the Fibonacci family are deliberate Android
 * ergonomic or optical exceptions and are named here rather than scattered.
 */
object RtcSize {
    val minimumTouchTarget = 48.dp
    val statusChipHeight = 34.dp
    val settingsRowHeight = 55.dp
    val fabDiameter = 55.dp
    val inlineIcon = 16.dp
    val actionIcon = 24.dp
    val largeIcon = 34.dp
    val heroIcon = 34.dp
    val avatarCompact = 34.dp
    val avatarStandard = 42.dp
    val avatarLarge = 55.dp
    val avatarProfile = 89.dp
    val mediaThumbnail = 89.dp
    val loadingIndicator = 34.dp
    val mediaAction = 34.dp
    val adaptiveCardMinWidth = 144.dp
    val welcomeHeroHeight = 144.dp
    val staffPaneWidth = 233.dp
    val qrCode = 233.dp
    val readableContentMaxWidth = 987.dp
    val expandedContentMaxWidth = 1597.dp
    val bottomNavigationHeight = 89.dp
}

object RtcStroke {
    val hairline = 1.dp
    val emphasis = 2.dp
}

/** Reference-driven dense resident dashboard measurements. */
object RtcHomeDashboard {
    val pagePadding = 16.dp
    val cardPadding = 16.dp
    val cardRadius = 16.dp
    val groupGap = 12.dp
    val ringDiameter = 76.dp
    val ringStroke = 6.dp
    val metricGap = 4.dp
    val metricInset = 4.dp
    val liveBadgeRadius = 12.dp
    val liveBadgeHorizontalPadding = 8.dp
    val liveBadgeVerticalPadding = 4.dp
}

object RtcElevation {
    val flat = 0.dp
}

object RtcAspectRatio {
    const val square = 1f
    const val goldenLandscape = RtcMath.Phi
    const val landscape = 16f / 9f
    const val portrait = 4f / 5f
}

/** 
 * Material Design 3 Motion Tokens & Specs
 * Reference: https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 */
object RtcMotion {
    // Duration Tokens
    const val durationShort1 = 50
    const val durationShort2 = 100
    const val durationShort3 = 150
    const val durationShort4 = 200

    const val durationMedium1 = 250
    const val durationMedium2 = 300
    const val durationMedium3 = 350
    const val durationMedium4 = 400

    const val durationLong1 = 450
    const val durationLong2 = 500
    const val durationLong3 = 550
    const val durationLong4 = 600

    const val durationExtraLong1 = 700
    const val durationExtraLong2 = 800
    const val durationExtraLong3 = 900
    const val durationExtraLong4 = 1000

    // Material 3 Emphasized Motion
    const val emphasizedDuration = durationLong2               // 500ms
    const val emphasizedDecelerateDuration = durationMedium4   // 400ms
    const val emphasizedAccelerateDuration = durationShort4    // 200ms

    // Material 3 Standard Motion
    const val standardDuration = durationMedium2               // 300ms
    const val standardDecelerateDuration = durationMedium1     // 250ms
    const val standardAccelerateDuration = durationShort4      // 200ms

    // Harmonic Fibonacci timing tokens (docs/MATHEMATICAL_DESIGN_SYSTEM.md)
    const val microFeedback = 89
    const val stateChange = 144
    const val standardTransition = 233
    const val contextualTransition = 377
    const val deliberateEmphasis = 610
}

enum class RtcContentDensity {
    RESIDENT_COMFORTABLE,
    FEED_CONTENT,
    ADMIN_COMPACT,
    ANALYTICAL_DENSE,
}

val LocalRtcContentDensity = staticCompositionLocalOf { RtcContentDensity.RESIDENT_COMFORTABLE }

@Immutable
data class RtcDensityMetrics(
    val outerPadding: Dp,
    val cardPadding: Dp,
    val listGap: Dp,
    val groupGap: Dp,
    val contentMaxWidth: Dp,
)

fun RtcContentDensity.metrics(): RtcDensityMetrics = when (this) {
    RtcContentDensity.RESIDENT_COMFORTABLE -> RtcDensityMetrics(
        outerPadding = RtcSpacing.pageGutter,
        cardPadding = RtcSpacing.cardPadding,
        listGap = RtcSpacing.listGap,
        groupGap = RtcSpacing.contentGroup,
        contentMaxWidth = RtcSize.readableContentMaxWidth,
    )
    RtcContentDensity.FEED_CONTENT -> RtcDensityMetrics(
        outerPadding = RtcSpacing.pageGutter,
        cardPadding = RtcSpacing.small,
        listGap = RtcSpacing.small,
        groupGap = RtcSpacing.compact,
        contentMaxWidth = RtcSize.readableContentMaxWidth,
    )
    RtcContentDensity.ADMIN_COMPACT -> RtcDensityMetrics(
        outerPadding = RtcSpacing.small,
        cardPadding = RtcSpacing.small,
        listGap = RtcSpacing.compact,
        groupGap = RtcSpacing.compact,
        contentMaxWidth = RtcSize.expandedContentMaxWidth,
    )
    RtcContentDensity.ANALYTICAL_DENSE -> RtcDensityMetrics(
        outerPadding = RtcSpacing.small,
        cardPadding = RtcSpacing.small,
        listGap = RtcSpacing.compact,
        groupGap = RtcSpacing.tiny,
        contentMaxWidth = RtcSize.expandedContentMaxWidth,
    )
}
