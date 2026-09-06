package za.org.rtc.community.feature.marketplace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceMediaStateTest {
    @Test
    fun uploadProgressMovesThroughExplicitStages() {
        val initial = MarketplaceMediaOperation.start("content://image", "stable-key")
        val uploading = initial.uploading(0.45f)
        val finalizing = uploading.finalizing()
        val complete = finalizing.completed("asset-1")

        assertEquals(MarketplaceMediaStage.PREPARING, initial.stage)
        assertEquals(MarketplaceMediaStage.UPLOADING, uploading.stage)
        assertEquals(0.45f, uploading.progress)
        assertEquals(MarketplaceMediaStage.FINALIZING, finalizing.stage)
        assertEquals(MarketplaceMediaStage.COMPLETED, complete.stage)
        assertEquals("asset-1", complete.assetId)
    }

    @Test
    fun failedUploadRetainsStableKeyForRetry() {
        val failed = MarketplaceMediaOperation.start("content://image", "stable-key")
            .uploading(0.2f)
            .failed("Network unavailable")

        assertEquals(MarketplaceMediaStage.FAILED, failed.stage)
        assertEquals("stable-key", failed.operationKey)
        assertTrue(failed.retryable)
    }

    @Test
    fun signedUrlCacheReturnsOnlyUnexpiredEntries() {
        var now = 1_000L
        val cache = MarketplaceSignedUrlCache(clock = { now })
        cache.put("private/path.jpg", "https://signed.example/path", expiresAtEpochMillis = 2_000L)

        assertEquals("https://signed.example/path", cache.get("private/path.jpg"))
        now = 2_001L
        assertNull(cache.get("private/path.jpg"))
        assertFalse(cache.containsValid("private/path.jpg"))
    }

    @Test
    fun signedUrlCacheEvictsLeastRecentlyUsedEntryAtBound() {
        val cache = MarketplaceSignedUrlCache(clock = { 1_000L }, maxEntries = 2)
        cache.put("a.jpg", "https://signed.example/a", 5_000L)
        cache.put("b.jpg", "https://signed.example/b", 5_000L)
        assertEquals("https://signed.example/a", cache.get("a.jpg"))

        cache.put("c.jpg", "https://signed.example/c", 5_000L)

        assertEquals("https://signed.example/a", cache.get("a.jpg"))
        assertNull(cache.get("b.jpg"))
        assertEquals("https://signed.example/c", cache.get("c.jpg"))
    }
}
