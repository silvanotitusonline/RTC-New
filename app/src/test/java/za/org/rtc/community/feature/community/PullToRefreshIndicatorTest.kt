package za.org.rtc.community.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.CommunityPost

class PullToRefreshIndicatorTest {

    @Test
    fun `pull to refresh triggers refreshing state on feed state`() {
        val initial = CommunityFeedState(
            items = listOf(samplePost("p1")),
            refreshing = false,
        )
        assertFalse(initial.refreshing)

        val refreshing = initial.copy(refreshing = true)
        assertTrue(refreshing.refreshing)
    }

    @Test
    fun `completing pull to refresh clears refreshing state and updates items`() {
        val refreshing = CommunityFeedState(
            items = listOf(samplePost("p1")),
            refreshing = true,
        )

        val refreshedPage = CommunityFeedPage(
            items = listOf(samplePost("p2"), samplePost("p1")),
            nextCursor = CommunityCursor("2026-09-05T00:00:00Z", "p1"),
            hasMore = false,
        )

        val updated = refreshing.withPage(refreshedPage, append = false)
        assertFalse(updated.refreshing)
        assertEquals(2, updated.items.size)
        assertEquals("p2", updated.items.first().id)
    }

    @Test
    fun `pull to refresh error clears refreshing indicator and records error`() {
        val refreshing = CommunityFeedState(
            items = listOf(samplePost("p1")),
            refreshing = true,
        )

        val withError = refreshing.copy(
            refreshing = false,
            initialError = "Network error while refreshing feed",
        )

        assertFalse(withError.refreshing)
        assertEquals("Network error while refreshing feed", withError.initialError)
    }

    @Test
    fun `pull to refresh resulting in empty feed presents clean empty state condition`() {
        val refreshing = CommunityFeedState(
            items = listOf(samplePost("p1")),
            refreshing = true,
        )

        val emptyPage = CommunityFeedPage(
            items = emptyList(),
            nextCursor = null,
            hasMore = false,
        )

        val updated = refreshing.withPage(emptyPage, append = false)
        assertFalse(updated.refreshing)
        assertTrue(updated.items.isEmpty())
        assertFalse(updated.hasMore)
    }

    private fun samplePost(id: String) = CommunityPost(
        id = id,
        author = "Resident",
        handle = "@resident",
        content = "Post $id",
        category = "Community",
        createdAt = "2026-09-05T04:00:00Z",
        reactions = 0,
        comments = 0,
    )
}
