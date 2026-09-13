package za.org.rtc.community.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraTest {
    @Test fun emptyOrUnmeasuredMapDoesNotInventAViewport() {
        assertNull(fitRtcMapCamera(emptyList(), 400f, 300f))
        assertNull(fitRtcMapCamera(listOf(GeoPoint(-28.33, 23.07)), 0f, 300f))
        assertNull(fitRtcMapCamera(listOf(GeoPoint(Double.NaN, 23.07)), 400f, 300f))
    }

    @Test fun singleLocationStaysCenteredAtUsableZoom() {
        val result = requireNotNull(fitRtcMapCamera(listOf(GeoPoint(-28.33, 23.07)), 400f, 300f))
        assertEquals(-28.33, result.center.latitude, 0.000001)
        assertEquals(23.07, result.center.longitude, 0.000001)
        assertEquals(16.0, result.zoom, 0.001)
    }

    @Test fun crossingDateLineUsesShortArc() {
        val result = requireNotNull(fitRtcMapCamera(
            listOf(GeoPoint(10.0, 179.0), GeoPoint(10.0, -179.0)), 400f, 300f,
        ))
        assertEquals(-180.0, result.center.longitude, 0.000001)
        assertTrue(result.zoom > 5.0)
    }

    @Test fun largerJourneyAndSmallerViewportZoomOut() {
        val nearby = listOf(GeoPoint(-28.33, 23.07), GeoPoint(-28.34, 23.08))
        val distant = nearby + GeoPoint(-26.2, 28.04)
        val nearbyCamera = requireNotNull(fitRtcMapCamera(nearby, 400f, 300f))
        val distantCamera = requireNotNull(fitRtcMapCamera(distant, 400f, 300f))
        val narrowCamera = requireNotNull(fitRtcMapCamera(distant, 220f, 200f))
        assertTrue(nearbyCamera.zoom > distantCamera.zoom)
        assertTrue(distantCamera.zoom > narrowCamera.zoom)
        assertTrue(distantCamera.center.isValid)
    }
}
