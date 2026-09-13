package za.org.rtc.community.feature.marketplace.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation

class MarketplaceMapPrivacyTest {
    private val business = MarketplaceBusinessCard(
        id = "business-a", slug = "public-store", displayName = "Public store", tagline = "",
        category = "Retail", locality = "Postmasburg", ratingAverage = 0.0, reviewCount = 0,
        weightedScore = 0.0, distanceMetres = null, logoPath = null, verified = true, featured = false,
    )
    private fun location(visibility: String = "PUBLIC", latitude: Double? = -28.327, longitude: Double? = 23.072) =
        MarketplaceLocation(
            id = "location-a", label = "Shop", locality = "Postmasburg", municipality = null,
            province = null, address = null, visibility = visibility, latitude = latitude, longitude = longitude,
            timezone = "Africa/Johannesburg", accessibilityFeatures = emptyList(), parkingNote = null,
        )

    @Test fun publicCoordinatesRemainExactPublishedBusinessCoordinates() {
        val marker = location().publicMapMarker(business)
        assertNotNull(marker)
        assertEquals(-28.327, marker!!.position.latitude, 0.0)
        assertEquals(23.072, marker.position.longitude, 0.0)
        assertEquals("business-a/location-a", marker.id)
    }

    @Test fun privateOrLocalityOnlyCoordinatesNeverProducePins() {
        listOf("PRIVATE", "LOCALITY_ONLY", "HIDDEN", "").forEach {
            assertNull(location(visibility = it).publicMapMarker(business))
        }
    }

    @Test fun partialAndOutOfRangeCoordinatesNeverProducePins() {
        assertNull(location(latitude = null).publicMapMarker(business))
        assertNull(location(longitude = null).publicMapMarker(business))
        assertNull(location(latitude = 91.0).publicMapMarker(business))
        assertNull(location(longitude = -181.0).publicMapMarker(business))
        assertNull(location(latitude = Double.NaN).publicMapMarker(business))
        assertNull(location(longitude = Double.POSITIVE_INFINITY).publicMapMarker(business))
    }
}
