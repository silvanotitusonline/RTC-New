package za.org.rtc.community.core.maps

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class RtcMapMarker(val id: String, val title: String, val position: GeoPoint, val subtitle: String = "")

@Serializable
data class TomTomMapConfig(val mapKey: String, val defaultCenter: GeoPoint, val attribution: String)

@Serializable
data class TomTomPlace(val id: String, val title: String, val address: String, val position: GeoPoint)

@Serializable
data class TomTomSearchResult(val results: List<TomTomPlace>)

@Serializable
data class TomTomInstruction(val message: String, val routeOffsetInMeters: Int)

@Serializable
data class TomTomRoute(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val trafficDelaySeconds: Int,
    val points: List<GeoPoint>,
    val instructions: List<TomTomInstruction>,
    val calculatedAt: String,
    val travelMode: String,
)
