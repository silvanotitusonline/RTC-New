package za.org.rtc.community.feature.community

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityFormattersTest {
    private val now = Instant.parse("2026-08-28T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val zone = ZoneId.of("UTC")

    @Test fun `seconds render as just now`() = assertEquals(
        "Just now",
        relativeTimeLabel("2026-08-28T09:59:30Z", clock, zone),
    )

    @Test fun `minutes render deterministically`() = assertEquals(
        "5m ago",
        relativeTimeLabel("2026-08-28T09:55:00Z", clock, zone),
    )

    @Test fun `hours render deterministically`() = assertEquals(
        "2h ago",
        relativeTimeLabel("2026-08-28T08:00:00Z", clock, zone),
    )

    @Test fun `previous calendar day renders yesterday`() = assertEquals(
        "Yesterday",
        relativeTimeLabel("2026-08-27T22:00:00Z", clock, zone),
    )

    @Test fun `older date within week uses relative days`() = assertEquals(
        "3d ago",
        relativeTimeLabel("2026-08-25T10:00:00Z", clock, zone),
    )

    @Test fun `previous year includes year`() = assertEquals(
        "Dec 31, 2025",
        relativeTimeLabel("2025-12-31T10:00:00Z", clock, zone),
    )

    @Test fun `malformed timestamp remains safe and visible`() = assertEquals(
        "not-a-time",
        relativeTimeLabel("not-a-time", clock, zone),
    )
}
