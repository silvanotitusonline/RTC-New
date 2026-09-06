package za.org.rtc.community.ui.theme

/**
 * Mathematical relationships used by Concept 6.
 *
 * These constants describe proportional relationships only. Ordinary layout
 * code consumes semantic tokens from [RtcSpacing], [RtcRadius] and [RtcSize].
 */
object RtcMath {
    const val Phi = 1.61803398875f
    const val GoldenMajor = 0.61803398875f
    const val GoldenMinor = 0.38196601125f
    const val Pi = 3.141592653589793

    fun goldenLandscapeHeight(width: Float): Float = width / Phi

    fun circleCircumference(radius: Float): Float = (2.0 * Pi * radius).toFloat()

    fun circleArea(radius: Float): Float = (Pi * radius * radius).toFloat()
}

object RtcBreakpoints {
    // Android adaptive-layout exceptions: these are platform breakpoints, not
    // Fibonacci spacing values.
    const val Medium = 600
    const val Expanded = 840
}

enum class RtcWindowWidth {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

fun classifyRtcWindowWidth(widthDp: Int): RtcWindowWidth = when {
    widthDp < RtcBreakpoints.Medium -> RtcWindowWidth.COMPACT
    widthDp < RtcBreakpoints.Expanded -> RtcWindowWidth.MEDIUM
    else -> RtcWindowWidth.EXPANDED
}
