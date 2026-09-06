package za.org.rtc.community.feature.community

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignedUrlCacheTest {
    private val now = Instant.parse("2026-08-28T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `valid signed URL is reused without reissuing`() = runTest {
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
    fun `entry inside refresh skew is reissued`() = runTest {
        var issues = 0
        val cache = SignedUrlCache(capacity = 4, refreshSkew = Duration.ofSeconds(30), clock = clock)
        cache.getOrIssue("media:a") {
            issues += 1
            SignedUrlValue("https://signed/old", now.plusSeconds(20))
        }
        val refreshed = cache.getOrIssue("media:a") {
            issues += 1
            SignedUrlValue("https://signed/new", now.plusSeconds(300))
        }

        assertEquals("https://signed/new", refreshed)
        assertEquals(2, issues)
    }

    @Test
    fun `least recently used entry is evicted when capacity is exceeded`() = runTest {
        var issues = 0
        val cache = SignedUrlCache(capacity = 2, refreshSkew = Duration.ZERO, clock = clock)
        suspend fun issue(key: String): String? = cache.getOrIssue(key) {
            issues += 1
            SignedUrlValue("https://signed/$key/$issues", now.plusSeconds(300))
        }

        issue("a")
        issue("b")
        issue("a")
        issue("c")
        issue("b")

        assertEquals(4, issues)
    }

    @Test
    fun `concurrent requests for the same key share one issuer`() = runTest {
        var issues = 0
        val cache = SignedUrlCache(capacity = 4, refreshSkew = Duration.ZERO, clock = clock)

        val urls = List(8) {
            async {
                cache.getOrIssue("media:a") {
                    issues += 1
                    SignedUrlValue("https://signed/coalesced", now.plusSeconds(300))
                }
            }
        }.awaitAll()

        assertEquals(List(8) { "https://signed/coalesced" }, urls)
        assertEquals(1, issues)
    }
}
