package za.org.rtc.community.feature.marketplace.presentation

import za.org.rtc.community.core.maps.GeoPoint
import za.org.rtc.community.core.maps.RtcMapMarker
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation

data class MarketplaceMapListing(
    val business: MarketplaceBusinessCard,
    val marker: RtcMapMarker,
)

data class MarketplaceMapData(
    val listings: List<MarketplaceMapListing> = emptyList(),
    val missingLocationCount: Int = 0,
    val failedCount: Int = 0,
    val omittedCount: Int = 0,
)

/** Locality-only and private addresses must never become public pins or routing destinations. */
internal fun MarketplaceLocation.publicMapMarker(business: MarketplaceBusinessCard): RtcMapMarker? {
    if (visibility != "PUBLIC") return null
    val position = GeoPoint(latitude ?: return null, longitude ?: return null)
    if (!position.isValid) return null
    return RtcMapMarker(
        id = "${business.id}/$id",
        title = business.displayName,
        position = position,
        subtitle = listOf(label, locality).filter(String::isNotBlank).joinToString(" · "),
    )
}

internal fun MarketplaceBusinessDetail.toMarketplaceMapListings(): List<MarketplaceMapListing> =
    locations.mapNotNull { location ->
        location.publicMapMarker(card)?.let { MarketplaceMapListing(card, it) }
    }.distinctBy { it.marker.id }
