package za.org.rtc.community.core.media

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedMediaPrimitivesTest {
    private val now = Instant.parse("2026-08-29T06:50:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `shared signed url cache reuses a valid entry`() = runTest {
        var issues = 0
        val cache = SignedUrlCache(capacity = 4, refreshSkew = Duration.ofSeconds(30), clock = clock)

        val first = cache.getOrIssue("media:a") {
            issues += 1
            SignedUrlValue("https://signed/a", now.plusSeconds(300))
        }
        val second = cache.getOrIssue("media:a") {
            issues += 1
            SignedUrlValue("https://signed/a2", now.plusSeconds(300))
        }

        assertEquals("https://signed/a", first)
        assertEquals("https://signed/a", second)
        assertEquals(1, issues)
    }

    @Test
    fun `media preparation is exposed through the shared core boundary`() {
        assertEquals(
            "za.org.rtc.community.core.media.MediaPreparation",
            MediaPreparation::class.qualifiedName,
        )
    }
}
