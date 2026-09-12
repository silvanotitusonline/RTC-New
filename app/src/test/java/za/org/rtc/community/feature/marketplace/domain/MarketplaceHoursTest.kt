package za.org.rtc.community.feature.marketplace.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketplaceHoursTest {
    private val evaluator = MarketplaceHoursEvaluator()

    @Test
    fun specialDateClosedOverridesWeeklyHours() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = "Northern Cape",
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(
                MarketplaceHoursInterval(
                    dayOfWeek = 5,
                    intervalOrder = 1,
                    state = "OPEN",
                    opensAt = "08:00",
                    closesAt = "17:00",
                ),
            ),
            hourExceptions = listOf(
                MarketplaceHoursException(
                    date = "2026-08-28",
                    state = "CLOSED",
                    opensAt = null,
                    closesAt = null,
                    note = "Public holiday",
                ),
            ),
        )

        assertEquals(
            MarketplaceOpeningStatus.Closed,
            evaluator.evaluate(location, Instant.parse("2026-08-28T10:00:00Z")),
        )
    }

    @Test
    fun specialDateOpenOverridesWeeklyClosedDay() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(
                MarketplaceHoursInterval(
                    dayOfWeek = 5,
                    intervalOrder = 1,
                    state = "CLOSED",
                    opensAt = null,
                    closesAt = null,
                ),
            ),
            hourExceptions = listOf(
                MarketplaceHoursException(
                    date = "2026-08-28",
                    state = "OPEN",
                    opensAt = "09:00",
                    closesAt = "13:00",
                    note = null,
                ),
            ),
        )

        assertEquals(
            MarketplaceOpeningStatus.OpenNow,
            evaluator.evaluate(location, Instant.parse("2026-08-28T10:00:00Z")),
        )
    }

    @Test
    fun overnightIntervalCarriesIntoNextDay() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(
                MarketplaceHoursInterval(
                    dayOfWeek = 5,
                    intervalOrder = 1,
                    state = "OPEN",
                    opensAt = "20:00",
                    closesAt = "02:00",
                ),
            ),
            hourExceptions = emptyList(),
        )

        assertEquals(
            MarketplaceOpeningStatus.OpenNow,
            evaluator.evaluate(location, Instant.parse("2026-08-28T23:30:00Z")),
        )
    }

    @Test
    fun previousDateClosedExceptionSuppressesRecurringOvernightCarry() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(
                MarketplaceHoursInterval(
                    dayOfWeek = 5,
                    intervalOrder = 1,
                    state = "OPEN",
                    opensAt = "20:00",
                    closesAt = "02:00",
                ),
                MarketplaceHoursInterval(
                    dayOfWeek = 6,
                    intervalOrder = 1,
                    state = "CLOSED",
                    opensAt = null,
                    closesAt = null,
                ),
            ),
            hourExceptions = listOf(
                MarketplaceHoursException(
                    date = "2026-08-28",
                    state = "CLOSED",
                    opensAt = null,
                    closesAt = null,
                    note = "Special closure",
                ),
            ),
        )

        assertEquals(
            MarketplaceOpeningStatus.Closed,
            evaluator.evaluate(location, Instant.parse("2026-08-28T23:30:00Z")),
        )
    }

    @Test
    fun previousDateOpenExceptionCanCarryOvernightIntoNextDate() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(
                MarketplaceHoursInterval(5, 1, "CLOSED", null, null),
                MarketplaceHoursInterval(6, 1, "CLOSED", null, null),
            ),
            hourExceptions = listOf(
                MarketplaceHoursException(
                    date = "2026-08-28",
                    state = "OPEN",
                    opensAt = "20:00",
                    closesAt = "02:00",
                    note = "Special event",
                ),
            ),
        )

        assertEquals(
            MarketplaceOpeningStatus.OpenNow,
            evaluator.evaluate(location, Instant.parse("2026-08-28T23:30:00Z")),
        )
    }

    @Test
    fun appointmentOnlyDayIsNotReportedAsOpen() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(
                MarketplaceHoursInterval(
                    dayOfWeek = 5,
                    intervalOrder = 1,
                    state = "APPOINTMENT_ONLY",
                    opensAt = null,
                    closesAt = null,
                ),
            ),
            hourExceptions = emptyList(),
        )

        assertEquals(
            MarketplaceOpeningStatus.ByAppointment,
            evaluator.evaluate(location, Instant.parse("2026-08-28T10:00:00Z")),
        )
    }

    @Test
    fun missingScheduleIsReportedAsUnavailable() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = emptyList(),
            hourExceptions = emptyList(),
        )

        assertEquals(
            MarketplaceOpeningStatus.Unavailable,
            evaluator.evaluate(location, Instant.parse("2026-08-28T10:00:00Z")),
        )
    }

    @Test
    fun invalidTimezoneFailsClosed() {
        val location = MarketplaceLocation(
            id = "loc",
            label = "Main",
            locality = "Postmasburg",
            municipality = null,
            province = null,
            address = null,
            visibility = "AREA",
            latitude = null,
            longitude = null,
            timezone = "not/a-zone",
            accessibilityFeatures = emptyList(),
            parkingNote = null,
            hours = listOf(MarketplaceHoursInterval(5, 1, "OPEN_24_HOURS", null, null)),
            hourExceptions = emptyList(),
        )

        assertEquals(
            MarketplaceOpeningStatus.Unavailable,
            evaluator.evaluate(location, Instant.parse("2026-08-28T10:00:00Z")),
        )
    }
}
