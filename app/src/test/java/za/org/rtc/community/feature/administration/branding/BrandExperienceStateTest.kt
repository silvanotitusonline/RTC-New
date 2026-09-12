package za.org.rtc.community.feature.administration.branding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.TypographyScalePreset

class BrandExperienceStateTest {
    @Test
    fun editsRemainLocalUntilPreviewIsExplicitlyRequested() {
        val initial = BrandExperienceEditorState.initial(GlobalUiConfiguration.default())
        val colourEdited = reduceBrandExperienceState(
            initial,
            BrandExperienceEditorAction.SetPrimarySeed("#D4AF37"),
        )
        val typeEdited = reduceBrandExperienceState(
            colourEdited,
            BrandExperienceEditorAction.SetTypographyScale(TypographyScalePreset.LARGE),
        )

        assertEquals("#D4AF37", typeEdited.working.appearance.primarySeed)
        assertEquals(TypographyScalePreset.LARGE, typeEdited.working.typography.scalePreset)
        assertNotEquals(typeEdited.working, typeEdited.preview)
        assertEquals(BrandExperiencePhase.EDITING, typeEdited.phase)

        val previewed = reduceBrandExperienceState(typeEdited, BrandExperienceEditorAction.Preview)
        assertEquals(previewed.working, previewed.preview)
        assertEquals(BrandExperiencePhase.PREVIEWING, previewed.phase)
    }

    @Test
    fun draftAndPublicationAreSeparateExplicitStates() {
        val initial = BrandExperienceEditorState.initial(GlobalUiConfiguration.default())
        val saving = reduceBrandExperienceState(initial, BrandExperienceEditorAction.SaveDraftStarted)
        val saved = reduceBrandExperienceState(saving, BrandExperienceEditorAction.DraftSaved("draft-1", initial.working))
        val publishing = reduceBrandExperienceState(saved, BrandExperienceEditorAction.PublishStarted)
        val published = reduceBrandExperienceState(
            publishing,
            BrandExperienceEditorAction.Published("version-2", refreshPending = false),
        )

        assertEquals(BrandExperiencePhase.DRAFT_SAVED, saved.phase)
        assertEquals("draft-1", saved.draftId)
        assertEquals(BrandExperiencePhase.PUBLISHING, publishing.phase)
        assertEquals(BrandExperiencePhase.PUBLISHED, published.phase)
        assertEquals("version-2", published.publishedVersionId)
        assertNull(published.draftId)
    }

    @Test
    fun editingAfterSavingDraftInvalidatesItsPublishHandle() {
        val saved = reduceBrandExperienceState(
            reduceBrandExperienceState(
                BrandExperienceEditorState.initial(GlobalUiConfiguration.default()),
                BrandExperienceEditorAction.SaveDraftStarted,
            ),
            BrandExperienceEditorAction.DraftSaved("draft-1", GlobalUiConfiguration.default()),
        )

        val edited = reduceBrandExperienceState(saved, BrandExperienceEditorAction.SetPrimarySeed("#112233"))

        assertEquals("#112233", edited.working.appearance.primarySeed)
        assertNull(edited.draftId)
        assertEquals(BrandExperiencePhase.EDITING, edited.phase)
    }

    @Test
    fun failureKeepsUnsavedWorkingConfigurationVisibleForCorrection() {
        val edited = reduceBrandExperienceState(
            BrandExperienceEditorState.initial(GlobalUiConfiguration.default()),
            BrandExperienceEditorAction.SetPrimarySeed("#112233"),
        )
        val failed = reduceBrandExperienceState(
            edited,
            BrandExperienceEditorAction.Failed("Publication rejected"),
        )

        assertEquals("#112233", failed.working.appearance.primarySeed)
        assertEquals(BrandExperiencePhase.ERROR, failed.phase)
        assertEquals("Publication rejected", failed.message)
    }

    @Test
    fun staleDraftCompletionDoesNotAttachToNewerWorkingConfiguration() {
        val beforeSave = BrandExperienceEditorState.initial(GlobalUiConfiguration.default())
        val saving = reduceBrandExperienceState(beforeSave, BrandExperienceEditorAction.SaveDraftStarted)
        val edited = reduceBrandExperienceState(saving, BrandExperienceEditorAction.SetPrimarySeed("#112233"))

        val completed = reduceBrandExperienceState(
            edited,
            BrandExperienceEditorAction.DraftSaved("draft-1", beforeSave.working),
        )

        assertNull(completed.draftId)
        assertNull(completed.savedDraftConfiguration)
        assertEquals("#112233", completed.working.appearance.primarySeed)
    }

    @Test
    fun resumedDraftIsImmediatelyPublishableFromItsSavedSnapshot() {
        val draft = GlobalUiConfiguration.default()
        val resumed = reduceBrandExperienceState(
            BrandExperienceEditorState.initial(GlobalUiConfiguration.default()),
            BrandExperienceEditorAction.ResumeDraft("draft-1", draft),
        )

        assertEquals("draft-1", resumed.draftId)
        assertEquals(draft, resumed.savedDraftConfiguration)
        assertEquals(BrandExperiencePhase.DRAFT_SAVED, resumed.phase)
    }

    @Test
    fun rejectedPreviewPreservesTheLastSafePreview() {
        val initial = BrandExperienceEditorState.initial(GlobalUiConfiguration.default())
        val rejected = reduceBrandExperienceState(
            initial,
            BrandExperienceEditorAction.PreviewRejected("Correct colours first."),
        )

        assertEquals(initial.preview, rejected.preview)
        assertEquals(BrandExperiencePhase.EDITING, rejected.phase)
        assertEquals("Correct colours first.", rejected.message)
    }
}
