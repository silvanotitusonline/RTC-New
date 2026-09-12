package za.org.rtc.community.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityComposerStateTest {
    @Test
    fun `body and attachments make composer dirty without losing either`() {
        val state = CommunityComposerState()
            .withBody("Neighbourhood update")
            .addAttachments(
                listOf(
                    CommunityComposerAttachment("a", "content://image/a"),
                    CommunityComposerAttachment("b", "content://image/b"),
                )
            )

        assertTrue(state.isDirty)
        assertEquals("Neighbourhood update", state.body)
        assertEquals(listOf("a", "b"), state.attachments.map { it.id })
    }

    @Test
    fun `attachment removal is stable and does not clear draft text`() {
        val state = CommunityComposerState(body = "Keep this text")
            .addAttachments(
                listOf(
                    CommunityComposerAttachment("a", "content://image/a"),
                    CommunityComposerAttachment("b", "content://image/b"),
                )
            )
            .removeAttachment("a")

        assertEquals("Keep this text", state.body)
        assertEquals(listOf("b"), state.attachments.map { it.id })
    }

    @Test
    fun `failed upload keeps draft and exposes recoverable retry state`() {
        val failed = CommunityComposerState(body = "Do not lose me")
            .addAttachments(listOf(CommunityComposerAttachment("a", "content://video/a")))
            .beginUpload(totalItems = 2)
            .withUploadedItems(1)
            .withUploadFailure("Upload interrupted")

        assertEquals("Do not lose me", failed.body)
        assertEquals(CommunityUploadPhase.FAILED, failed.upload.phase)
        assertEquals(0.5f, failed.upload.fraction)
        assertTrue(failed.upload.canRetry)

        val retrying = failed.retryUpload()
        assertEquals(CommunityUploadPhase.PREPARING, retrying.upload.phase)
        assertFalse(retrying.upload.canRetry)
        assertEquals("Do not lose me", retrying.body)
    }
}
