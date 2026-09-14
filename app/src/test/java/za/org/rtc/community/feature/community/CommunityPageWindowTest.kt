package za.org.rtc.community.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.CommunityPost

class CommunityPageWindowTest {
    @Test
    fun `extra server row proves another page without leaking into visible items`() {
        val page = buildCommunityFeedPage(
            posts = listOf(
                post("c", "2026-08-28T12:00:00Z"),
                post("b", "2026-08-28T11:00:00Z"),
                post("a", "2026-08-28T10:00:00Z"),
            ),
            visibleLimit = 2,
        )

        assertEquals(listOf("c", "b"), page.items.map { it.id })
        assertTrue(page.hasMore)
        assertEquals(CommunityCursor("2026-08-28T11:00:00Z", "b"), page.nextCursor)
    }

    @Test
    fun `short server page is terminal and uses no next cursor`() {
        val page = buildCommunityFeedPage(
            posts = listOf(post("a", "2026-08-28T10:00:00Z")),
            visibleLimit = 2,
        )

        assertEquals(listOf("a"), page.items.map { it.id })
        assertFalse(page.hasMore)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `cached fallback keeps only posts strictly after the composite cursor`() {
        val posts = listOf(
            post("d", "2026-08-28T12:00:00Z"),
            post("c", "2026-08-28T11:00:00Z"),
            post("b", "2026-08-28T11:00:00Z"),
            post("a", "2026-08-28T10:00:00Z"),
        )

        val window = postsAfterCommunityCursor(
            posts = posts,
            cursor = CommunityCursor("2026-08-28T11:00:00Z", "c"),
        )

        assertEquals(listOf("b", "a"), window.map { it.id })
    }

    @Test
    fun `cached fallback without a cursor preserves deterministic feed order`() {
        val posts = listOf(
            post("a", "2026-08-28T10:00:00Z"),
            post("c", "2026-08-28T11:00:00Z"),
            post("b", "2026-08-28T11:00:00Z"),
        )

        val window = postsAfterCommunityCursor(posts = posts, cursor = null)

        assertEquals(listOf("c", "b", "a"), window.map { it.id })
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
