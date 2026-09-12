package za.org.rtc.community.feature.marketplace.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursEvaluator
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOpeningStatus

class MarketplaceJsonMappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun locationMapperDecodesWeeklyHoursAndDateExceptions() {
        val row = json.parseToJsonElement(
            """
            {
              "id": "loc-1",
              "label": "Main branch",
              "locality": "Postmasburg",
              "municipality": "Tsantsabane",
              "province": "Northern Cape",
              "address": "1 Main Road",
              "visibility": "EXACT",
              "latitude": -28.33,
              "longitude": 23.07,
              "timezone": "Africa/Johannesburg",
              "hours": [
                {
                  "dayOfWeek": 5,
                  "intervalOrder": 1,
                  "state": "OPEN",
                  "opensAt": "08:00:00",
                  "closesAt": "17:00:00"
                }
              ],
              "hourExceptions": [
                {
                  "date": "2026-08-28",
                  "state": "CLOSED",
                  "opensAt": null,
                  "closesAt": null,
                  "note": "Public holiday"
                }
              ],
              "accessibilityFeatures": ["WHEELCHAIR_ACCESS"],
              "parkingNote": "Street parking"
            }
            """.trimIndent(),
        ).jsonObject

        val location = row.toMarketplaceLocation()

        assertEquals("loc-1", location.id)
        assertEquals(1, location.hours.size)
        assertEquals(5, location.hours.single().dayOfWeek)
        assertEquals("08:00:00", location.hours.single().opensAt)
        assertEquals(1, location.hourExceptions.size)
        assertEquals("2026-08-28", location.hourExceptions.single().date)
        assertEquals("Public holiday", location.hourExceptions.single().note)
        assertEquals(
            MarketplaceOpeningStatus.Closed,
            MarketplaceHoursEvaluator().evaluate(
                location,
                java.time.Instant.parse("2026-08-28T10:00:00Z"),
            ),
        )
    }

    @Test
    fun absentAdditiveHoursFieldsRemainBackwardCompatible() {
        val row = json.parseToJsonElement(
            """
            {
              "id": "loc-2",
              "label": "Service area",
              "locality": "Postmasburg",
              "municipality": null,
              "province": null,
              "address": null,
              "visibility": "AREA",
              "latitude": null,
              "longitude": null,
              "timezone": "Africa/Johannesburg",
              "accessibilityFeatures": [],
              "parkingNote": null
            }
            """.trimIndent(),
        ).jsonObject

        val location = row.toMarketplaceLocation()

        assertEquals(emptyList<Any>(), location.hours)
        assertEquals(emptyList<Any>(), location.hourExceptions)
        assertNull(location.address)
    }
}
