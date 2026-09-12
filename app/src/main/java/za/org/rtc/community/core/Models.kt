
package za.org.rtc.community.core

import androidx.compose.ui.graphics.Color

data class LocationPoint(val lat: Double, val lon: Double, val name: String? = null)
data class RouteInfo(val distanceMeters: Int, val durationSeconds: Int, val path: String)
data class FlaggedPost(val id: String, val author: String, val content: String, val flagReason: String)
data class CivicReport(val id: String, val title: String, val description: String, val lat: Double, val lon: Double)
