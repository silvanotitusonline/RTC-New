package za.org.rtc.community.feature.publicreports.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency

class PublicReportJsonMappersTest {
    @Test
    fun mapsNamedReport() {
        val report = PublicReportJsonMappers.report(baseRow(identity = "NAMED", author = "Thabo Molefe", verified = "2026-08-29T10:00:00Z"))
        assertEquals("Thabo Molefe", report.authorDisplayName)
        assertEquals(PublicReportIdentityMode.NAMED, report.identityMode)
        assertTrue(report.verified)
    }

    @Test
    fun mapsAnonymousReportWithoutExposingPrivateIdentity() {
        val report = PublicReportJsonMappers.report(baseRow(identity = "ANONYMOUS", author = "Should not appear", verified = null))
        assertEquals("Anonymous community member", report.authorDisplayName)
        assertFalse(report.verified)
    }

    @Test
    fun missingOptionalFieldsDoNotCrash() {
        val row = buildJsonObject {
            put("id", "11111111-1111-1111-1111-111111111111")
            put("title", "Broken streetlight")
        }
        val report = PublicReportJsonMappers.report(row)
        assertEquals("", report.publicLocationLabel)
        assertEquals(0, report.evidenceCount)
    }

    @Test
    fun unknownEnumsDegradeSafely() {
        assertEquals(PublicReportStatus.UNKNOWN, PublicReportJsonMappers.status("PENDING_REVIEW"))
        assertEquals(PublicReportUrgency.UNKNOWN, PublicReportJsonMappers.urgency("EXTREME"))
        assertEquals(PublicReportIdentityMode.UNKNOWN, PublicReportJsonMappers.identity("ALIAS"))
    }

    @Test(expected = IllegalStateException::class)
    fun privateFieldsCannotEnterPublicModel() {
        val row = buildJsonObject {
            put("id", "11111111-1111-1111-1111-111111111111")
            put("title", "Broken streetlight")
            put("reporter_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        }
        PublicReportJsonMappers.report(row)
    }

    @Test
    fun nullPrivateKeysAreAllowed() {
        val row = buildJsonObject {
            put("id", "11111111-1111-1111-1111-111111111111")
            put("title", "Broken streetlight")
            put("reporter_id", JsonNull)
            put("storage_path", JsonNull)
        }
        PublicReportJsonMappers.report(row)
    }

    private fun baseRow(identity: String, author: String, verified: String?) = buildJsonObject {
        put("id", "11111111-1111-1111-1111-111111111111")
        put("title", "Water leaking near clinic")
        put("description", "A pipe has been leaking beside the clinic gate since Monday.")
        put("category_id", "22222222-2222-2222-2222-222222222222")
        put("category_slug", "water-sanitation")
        put("category_label", "Water & Sanitation")
        put("urgency", "HIGH")
        put("status", "SUBMITTED")
        put("identity_mode", identity)
        put("public_location_label", "Clinic gate")
        put("author_display_name", author)
        verified?.let { put("verified_at", it) }
        put("thumbs_up_count", 4)
        put("thumbs_down_count", 1)
        put("comment_count", 2)
        put("evidence_count", 1)
        put("current_user_vote", JsonPrimitive(1))
        put("created_at", "2026-08-29T08:00:00Z")
        put("updated_at", "2026-08-29T08:00:00Z")
    }
}
