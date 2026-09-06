package za.org.rtc.community.feature.marketplace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MarketplaceMutationTest {
    @Test
    fun failedRetryReusesSameOperationKey() {
        val keys = ArrayDeque(listOf("key-1", "key-2"))
        val tracker = MarketplaceMutationTracker { keys.removeFirst() }

        val first = tracker.begin("SAVE_LOCATION", "business-1")!!
        tracker.failed(first)
        val retry = tracker.begin("SAVE_LOCATION", "business-1")!!

        assertEquals("key-1", first.key)
        assertSame(first, retry)
    }

    @Test
    fun successfulOperationRotatesKeyForNextMutation() {
        val keys = ArrayDeque(listOf("key-1", "key-2"))
        val tracker = MarketplaceMutationTracker { keys.removeFirst() }

        val first = tracker.begin("SAVE_IDENTITY", "business-1")!!
        tracker.succeeded(first)
        val next = tracker.begin("SAVE_IDENTITY", "business-1")!!

        assertNotEquals(first.key, next.key)
        assertEquals("key-2", next.key)
    }

    @Test
    fun duplicateInFlightMutationIsSuppressed() {
        val tracker = MarketplaceMutationTracker { "key-1" }

        val first = tracker.begin("SUBMIT", "business-1")
        val duplicate = tracker.begin("SUBMIT", "business-1")

        assertEquals("key-1", first?.key)
        assertNull(duplicate)
    }
}
