package za.org.rtc.community.core.maps

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

internal data class RtcMapCamera(val center: GeoPoint, val zoom: Double)

/** Fits route geometry using Web Mercator extents, including routes crossing the date line. */
internal fun fitRtcMapCamera(points: List<GeoPoint>, widthDp: Float, heightDp: Float): RtcMapCamera? {
    val valid = points.filter { it.isValid }
    if (valid.isEmpty() || widthDp <= 0f || heightDp <= 0f) return null
    val longitudes = valid.map { ((it.longitude % 360) + 360) % 360 }.sorted()
    // Remove the largest longitude gap to obtain the shortest interval covering the points.
    val gapIndex = longitudes.indices.maxBy { index ->
        val next = if (index == longitudes.lastIndex) longitudes[0] + 360 else longitudes[index + 1]
        next - longitudes[index]
    }
    val west = longitudes[(gapIndex + 1) % longitudes.size]
    val spanLongitude = 360 - ((if (gapIndex == longitudes.lastIndex) longitudes[0] + 360
        else longitudes[gapIndex + 1]) - longitudes[gapIndex])
    val centerLongitude = ((west + spanLongitude / 2 + 180) % 360) - 180
    val ys = valid.map { point ->
        val latitudeRadians = point.latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180
        (1 - ln(tan(PI / 4 + latitudeRadians / 2)) / PI) / 2
    }
    val top = ys.min()
    val bottom = ys.max()
    val centerLatitude = (2 * atan(exp(PI * (1 - (top + bottom)))) - PI / 2) * 180 / PI
    // Keep labels, buttons, pins and attribution inside the visible viewport.
    val availableWidth = max(widthDp - 144, 32f).toDouble()
    val availableHeight = max(heightDp - 112, 32f).toDouble()
    val longitudeFraction = max(spanLongitude / 360, 0.000001)
    val latitudeFraction = max(bottom - top, 0.000001)
    val zoom = min(log2(availableWidth / (512 * longitudeFraction)),
        log2(availableHeight / (512 * latitudeFraction))).coerceIn(1.0, 16.0)
    return RtcMapCamera(GeoPoint(centerLatitude, centerLongitude), zoom)
}
