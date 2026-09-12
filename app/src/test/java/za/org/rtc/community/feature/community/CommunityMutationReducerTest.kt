package za.org.rtc.community.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.CommunityPost

class CommunityMutationReducerTest {
    @Test
    fun `optimistic like updates selection and count immediately`() {
        val original = post(id = "p1", liked = false, reactions = 4)

        val optimistic = original.optimisticLikeToggle()

        assertTrue(optimistic.viewerHasLiked)
        assertEquals(5, optimistic.reactions)
    }

    @Test
    fun `optimistic unlike never produces a negative reaction count`() {
        val original = post(id = "p1", liked = true, reactions = 0)

        val optimistic = original.optimisticLikeToggle()

        assertFalse(optimistic.viewerHasLiked)
        assertEquals(0, optimistic.reactions)
    }

    @Test
    fun `authoritative outcome replaces optimistic reaction state`() {
        val optimistic = post(id = "p1", liked = true, reactions = 5)

        val reconciled = optimistic.withLikeOutcome(CommunityLikeOutcome(liked = true, reactionCount = 7))

        assertTrue(reconciled.viewerHasLiked)
        assertEquals(7, reconciled.reactions)
    }

    private fun post(id: String, liked: Boolean, reactions: Int) = CommunityPost(
        id = id,
        author = "Resident",
        handle = "@resident",
        content = "Post",
        category = "Community",
        createdAt = "2026-08-28T10:00:00Z",
        reactions = reactions,
        comments = 0,
        viewerHasLiked = liked,
    )
}
