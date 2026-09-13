package za.org.rtc.community.core.maps

import java.text.DecimalFormatSymbols
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.location.locationFreshnessRemainingMillis

class LiveMapRulesTest {
    @Test fun coordinatesRejectNonFiniteOrOutOfRangeValues() {
        assertFalse(GeoPoint(Double.NaN, 0.0).isValid)
        assertFalse(GeoPoint(-91.0, 0.0).isValid)
        assertFalse(GeoPoint(0.0, 181.0).isValid)
        assertTrue(GeoPoint(-28.3333, 23.0667).isValid)
    }

    @Test fun locationFixExpiresAfterOneMinuteOfMonotonicTime() {
        assertEquals(60_000L, locationFreshnessRemainingMillis(1_000_000L, 1_000_000L))
        assertEquals(30_000L, locationFreshnessRemainingMillis(1_000_000L, 30_001_000_000L))
        assertEquals(0L, locationFreshnessRemainingMillis(1_000_000L, 60_001_000_000L))
        assertEquals(0L, locationFreshnessRemainingMillis(2_000_000L, 1_000_000L))
        assertEquals(0L, locationFreshnessRemainingMillis(0L, 1_000_000L))
    }

    @Test fun roadDistanceUsesMetresOrLocalisedKilometres() {
        val decimal = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("en-ZA")).decimalSeparator
        assertEquals("999 m", formatRouteDistance(999))
        assertEquals("1${decimal}5 km", formatRouteDistance(1_500))
        assertEquals("Unavailable", formatRouteDistance(-1))
    }

    @Test fun durationRoundsUpAndDoesNotOverflow() {
        assertEquals("1 min", formatRouteDuration(1))
        assertEquals("2 min", formatRouteDuration(61))
        assertEquals("1 hr 1 min", formatRouteDuration(3_601))
        assertEquals("Unavailable", formatRouteDuration(-1))
        assertTrue(formatRouteDuration(Int.MAX_VALUE).contains("hr"))
    }

    @Test fun movementThrottleHandlesCoincidentAndAntipodalCoordinates() {
        assertEquals(0.0, geographicSeparationMeters(GeoPoint(-28.3, 23.0), GeoPoint(-28.3, 23.0)), 0.01)
        assertTrue(geographicSeparationMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)).isFinite())
    }

    @Test fun serviceErrorsDistinguishSignInRouteAndRateLimitsWithoutRawProviderMessages() {
        assertTrue(mapServiceErrorMessage(401).contains("sign-in"))
        assertTrue(mapServiceErrorMessage(404).contains("No route"))
        assertTrue(mapServiceErrorMessage(429).contains("limited"))
        assertTrue(mapServiceErrorMessage(503).contains("unavailable"))
    }
}
