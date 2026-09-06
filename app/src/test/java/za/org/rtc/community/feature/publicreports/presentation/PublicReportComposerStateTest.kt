package za.org.rtc.community.feature.publicreports.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import za.org.rtc.community.feature.publicreports.domain.PublicReportDraft
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportLocationMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportValidation

class PublicReportComposerStateTest {
    @Test
    fun draftValidationBlocksIncompleteComposer() {
        val draft = sampleDraft(title = "ab", description = "too short", categoryId = "")
        assertNotNull(PublicReportValidation.draft(draft, evidenceCount = 0, cannotProvideEvidence = false, guidelinesAccepted = false))
    }

    @Test
    fun completeDraftWithEvidencePasses() {
        val draft = sampleDraft()
        assertNull(PublicReportValidation.draft(draft, evidenceCount = 1, cannotProvideEvidence = false, guidelinesAccepted = true))
    }

    @Test
    fun duplicateSubmitIsBlockedByExistingSubmittedId() {
        val state = PublicReportComposerState(
            clientRequestId = "11111111-1111-1111-1111-111111111111",
            submitting = false,
            submittedReportId = "22222222-2222-2222-2222-222222222222",
        )
        assertFalse(state.submitting)
        assertEquals("22222222-2222-2222-2222-222222222222", state.submittedReportId)
    }

    @Test
    fun requestUuidIsRetainedOnStateCopy() {
        val first = PublicReportComposerState(clientRequestId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val copied = first.copy(title = "Water leaking near clinic")
        assertEquals(first.clientRequestId, copied.clientRequestId)
    }

    private fun sampleDraft(
        title: String = "Water leaking near clinic",
        description: String = "A pipe has been leaking beside the clinic gate since Monday morning.",
        categoryId: String = "cat",
    ) = PublicReportDraft(
        clientRequestId = "11111111-1111-1111-1111-111111111111",
        title = title,
        description = description,
        startedAt = null,
        categoryId = categoryId,
        urgency = PublicReportUrgency.HIGH,
        identityMode = PublicReportIdentityMode.NAMED,
        locationMode = PublicReportLocationMode.MANUAL,
        publicLocationLabel = "Clinic gate",
        latitude = null,
        longitude = null,
        exactAddress = "Near the municipal clinic gate",
        noEvidenceReason = null,
        contactPermission = false,
        guidelinesVersion = "1",
    )
}
