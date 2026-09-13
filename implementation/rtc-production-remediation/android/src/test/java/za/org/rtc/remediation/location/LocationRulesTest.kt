package za.org.rtc.remediation.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRulesTest {
    @Test fun oldCachedFixAndFutureFixAreNeverCurrent() {
        val now = 100_000_000_000L
        assertTrue(LiveLocationSource.isFresh(now - 5_000_000_000L, now))
        assertFalse(LiveLocationSource.isFresh(now - 61_000_000_000L, now))
        assertFalse(LiveLocationSource.isFresh(now + 1, now))
        assertFalse(LiveLocationSource.isFresh(0, now))
    }

    @Test fun distanceHasUnitAndDoesNotExposeRawFloat() {
        assertEquals("350 m", formatRoadDistance(350))
        assertEquals("3.5 km", formatRoadDistance(3_500))
        assertEquals("0 m", formatRoadDistance(0)) // Only a real zero route may display zero.
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCoordinatesCannotReachMapOrRouting() { Coordinates(Double.NaN, 24.0) }
}
