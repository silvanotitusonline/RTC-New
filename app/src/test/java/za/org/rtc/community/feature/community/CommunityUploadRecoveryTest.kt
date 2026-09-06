package za.org.rtc.community.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityUploadRecoveryTest {
    @Test
    fun `recovery checkpoint is restricted to the authenticated owner and draft`() {
        val checkpoint = CommunityUploadRecovery(
            ownerId = "resident-a",
            draftId = "draft-1",
            uploadedItems = 2,
            totalItems = 4,
        )

        assertTrue(checkpoint.belongsTo("resident-a", "draft-1"))
        assertFalse(checkpoint.belongsTo("resident-b", "draft-1"))
        assertFalse(checkpoint.belongsTo("resident-a", "draft-2"))
        assertEquals(0.5f, checkpoint.fraction)
    }

    @Test
    fun `invalid progress is clamped rather than producing impossible UI state`() {
        val checkpoint = CommunityUploadRecovery(
            ownerId = "resident-a",
            draftId = "draft-1",
            uploadedItems = 8,
            totalItems = 3,
        )

        assertEquals(1f, checkpoint.fraction)
    }
}
