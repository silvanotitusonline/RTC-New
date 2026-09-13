package za.org.rtc.community.feature.publicreports.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import za.org.rtc.community.feature.publicreports.domain.publicMapMarker

class PublicReportMapPrivacyTest {
    private fun report(verified: Boolean = true, latitude: Double? = -28.327, longitude: Double? = 23.072) =
        PublicReportJsonMappers.report(buildJsonObject {
            put("id", "report-a")
            put("title", "Road repair")
            put("public_location_label", "Town centre")
            if (verified) put("verified_at", "2026-09-13T10:00:00Z")
            latitude?.let { put("public_latitude", it) }
            longitude?.let { put("public_longitude", it) }
        })

    @Test fun mapsOnlyServerSanitizedCoordinates() {
        val report = report()
        assertEquals(-28.327, report.publicLatitude!!, 0.0)
        assertEquals(23.072, report.publicLongitude!!, 0.0)
        assertEquals(-28.327, report.publicMapMarker()!!.position.latitude, 0.0)
    }

    @Test fun missingOrInvalidPublicCoordinatesAreOmitted() {
        assertNull(report(latitude = null).publicMapMarker())
        assertNull(report(longitude = null).publicMapMarker())
        assertNull(report(latitude = 91.0).publicMapMarker())
        assertNull(report(longitude = 181.0).publicMapMarker())
    }

    @Test fun unverifiedReportsDoNotBecomePublicMapMarkers() {
        assertNull(report(verified = false).publicMapMarker())
    }

    @Test(expected = IllegalStateException::class)
    fun exactReporterCoordinatesRemainBlockedFromPublicModel() {
        PublicReportJsonMappers.report(buildJsonObject {
            put("id", "report-a")
            put("title", "Road repair")
            put("latitude", -28.3271234)
            put("longitude", 23.0721234)
        })
    }
}
