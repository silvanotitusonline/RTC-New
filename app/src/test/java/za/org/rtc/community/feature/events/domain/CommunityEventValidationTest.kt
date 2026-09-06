package za.org.rtc.community.feature.events.domain

import java.time.Instant
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityEventValidationTest {
    private val start = Instant.parse("2026-09-01T10:00:00Z")
    private val end = Instant.parse("2026-09-01T11:00:00Z")

    @Test
    fun draftValidationBoundsUserInputAndKeepsTimezoneCorrectness() {
        assertNotNull(CommunityEventValidation.draft("", "A neighbourhood meeting", start, end, "Africa/Johannesburg", null, "Community Hall"))
        assertNotNull(CommunityEventValidation.draft("Community meeting", " ", start, end, "Africa/Johannesburg", null, "Community Hall"))
        assertNotNull(CommunityEventValidation.draft("Community meeting", "A neighbourhood meeting", end, start, "Africa/Johannesburg", null, "Community Hall"))
        assertNotNull(CommunityEventValidation.draft("Community meeting", "A neighbourhood meeting", start, end, "Not/A-Timezone", null, "Community Hall"))
        assertNotNull(CommunityEventValidation.draft("Community meeting", "A neighbourhood meeting", start, end, "Africa/Johannesburg", "x", "Community Hall"))
        assertNotNull(CommunityEventValidation.draft("Community meeting", "A neighbourhood meeting", start, end, "Africa/Johannesburg", null, "x"))

        assertNull(CommunityEventValidation.draft("Community meeting", "A neighbourhood meeting", start, end, "Africa/Johannesburg", "Postmasburg", "Community Hall"))
    }

    @Test
    fun cancellationReasonIsBounded() {
        assertNotNull(CommunityEventValidation.cancellationReason("  "))
        assertNotNull(CommunityEventValidation.cancellationReason("x".repeat(501)))
        assertNull(CommunityEventValidation.cancellationReason("Venue is unavailable."))
    }
}
