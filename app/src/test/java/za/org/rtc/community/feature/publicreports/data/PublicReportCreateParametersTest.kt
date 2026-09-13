package za.org.rtc.community.feature.publicreports.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import za.org.rtc.community.feature.publicreports.domain.PublicReportDraft
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportLocationMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency

class PublicReportCreateParametersTest {
    private val manual = PublicReportDraft(
        clientRequestId = "85f705a4-caf5-4ad8-a5d0-fa144dd3d887",
        title = "Water leak", description = "Water is leaking next to the public clinic entrance.",
        startedAt = null, categoryId = "c3d0a61d-afcc-45f2-b31d-43e645c48c21",
        urgency = PublicReportUrgency.NORMAL, identityMode = PublicReportIdentityMode.NAMED,
        locationMode = PublicReportLocationMode.MANUAL, publicLocationLabel = "Clinic entrance",
        latitude = null, longitude = null, exactAddress = "Clinic entrance",
        noEvidenceReason = null, contactPermission = false, guidelinesVersion = "1",
    )

    @Test fun nullableSqlArgumentsArePresentSoPostgrestFindsTheCreateOverload() {
        val params = publicReportCreateParameters(manual)
        assertEquals(15, params.size)
        for (name in listOf("p_started_at", "p_latitude", "p_longitude", "p_no_evidence_reason")) {
            assertEquals(JsonNull, params[name])
        }
    }

    @Test fun selectedCoordinatesRemainExactForServerPrivacyProjection() {
        val params = publicReportCreateParameters(manual.copy(
            locationMode = PublicReportLocationMode.MAP,
            latitude = -28.332649, longitude = 23.062371, exactAddress = null,
        ))
        assertEquals("MAP", params.getValue("p_location_mode").jsonPrimitive.content)
        assertEquals(-28.332649, params.getValue("p_latitude").jsonPrimitive.double, 0.0000001)
        assertEquals(23.062371, params.getValue("p_longitude").jsonPrimitive.double, 0.0000001)
        assertEquals("Clinic entrance", params.getValue("p_public_location_label").jsonPrimitive.content)
        assertEquals(JsonNull, params["p_exact_address"])
    }
}
