package za.org.rtc.community.feature.publicreports.domain

import za.org.rtc.community.core.maps.GeoPoint
import za.org.rtc.community.core.maps.RtcMapMarker

/** Never infer coordinates from an address or access the report's private details. */
fun PublicReport.publicMapMarker(): RtcMapMarker? {
    if (!verified) return null
    val position = GeoPoint(publicLatitude ?: return null, publicLongitude ?: return null)
    if (!position.isValid) return null
    return RtcMapMarker(id, title, position, "$publicLocationLabel · Approximate public location")
}
