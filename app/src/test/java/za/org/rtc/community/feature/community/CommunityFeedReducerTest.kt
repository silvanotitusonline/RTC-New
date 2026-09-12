package za.org.rtc.community.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.CommunityPost

class CommunityFeedReducerTest {
    @Test
    fun `refresh replaces existing feed and installs the server cursor`() {
        val old = CommunityFeedState(items = listOf(post("old", "2026-08-27T10:00:00Z")))
        val page = CommunityFeedPage(
            items = listOf(post("new", "2026-08-28T10:00:00Z")),
            nextCursor = CommunityCursor("2026-08-28T10:00:00Z", "new"),
            hasMore = true,
        )

        val result = old.withPage(page, append = false)

        assertEquals(listOf("new"), result.items.map { it.id })
        assertEquals(page.nextCursor, result.nextCursor)
        assertTrue(result.hasMore)
        assertFalse(result.initialLoading)
        assertFalse(result.refreshing)
    }

    @Test
    fun `append keeps stable order while removing duplicate post ids`() {
        val state = CommunityFeedState(
            items = listOf(
                post("a", "2026-08-28T12:00:00Z"),
                post("b", "2026-08-28T11:00:00Z"),
            ),
        )
        val page = CommunityFeedPage(
            items = listOf(
                post("b", "2026-08-28T11:00:00Z"),
                post("c", "2026-08-28T10:00:00Z"),
            ),
            nextCursor = CommunityCursor("2026-08-28T10:00:00Z", "c"),
            hasMore = true,
        )

        val result = state.withPage(page, append = true)

        assertEquals(listOf("a", "b", "c"), result.items.map { it.id })
        assertEquals(3, result.items.map { it.id }.distinct().size)
    }

    @Test
    fun `empty terminal append marks end of feed without losing loaded posts`() {
        val state = CommunityFeedState(
            items = listOf(post("a", "2026-08-28T12:00:00Z")),
            nextCursor = CommunityCursor("2026-08-28T12:00:00Z", "a"),
            hasMore = true,
        )

        val result = state.withPage(
            CommunityFeedPage(items = emptyList(), nextCursor = null, hasMore = false),
            append = true,
        )

        assertEquals(listOf("a"), result.items.map { it.id })
        assertFalse(result.hasMore)
        assertEquals(null, result.nextCursor)
        assertFalse(result.appendLoading)
    }

    @Test
    fun `cursor includes id so equal timestamps retain deterministic continuation`() {
        val first = CommunityCursor("2026-08-28T12:00:00Z", "post-b")
        val second = CommunityCursor("2026-08-28T12:00:00Z", "post-a")

        assertEquals(first.createdAt, second.createdAt)
        assertTrue(first.id > second.id)
    }

    private fun post(id: String, createdAt: String) = CommunityPost(
        id = id,
        author = "Resident",
        handle = "@resident",
        content = "Post $id",
        category = "Community",
        createdAt = createdAt,
        reactions = 0,
        comments = 0,
    )
}
