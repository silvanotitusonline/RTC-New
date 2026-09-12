package za.org.rtc.community.feature.administration.branding

import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.TypographyScalePreset

enum class BrandExperiencePhase {
    EDITING,
    PREVIEWING,
    SAVING_DRAFT,
    DRAFT_SAVED,
    PUBLISHING,
    PUBLISHED,
    ERROR,
}

data class BrandExperienceEditorState(
    val baseline: GlobalUiConfiguration,
    val working: GlobalUiConfiguration,
    val preview: GlobalUiConfiguration,
    val phase: BrandExperiencePhase = BrandExperiencePhase.EDITING,
    val draftId: String? = null,
    val savedDraftConfiguration: GlobalUiConfiguration? = null,
    val publishedVersionId: String? = null,
    val message: String? = null,
) {
    val hasUnsavedChanges: Boolean get() = working != baseline

    companion object {
        fun initial(configuration: GlobalUiConfiguration) = BrandExperienceEditorState(
            baseline = configuration,
            working = configuration,
            preview = configuration,
        )
    }
}

sealed interface BrandExperienceEditorAction {
    data class SetPrimarySeed(val value: String) : BrandExperienceEditorAction
    data class SetAccentSeed(val value: String) : BrandExperienceEditorAction
    data class SetBackgroundSeed(val value: String) : BrandExperienceEditorAction
    data class SetTypographyScale(val value: TypographyScalePreset) : BrandExperienceEditorAction
    data object Preview : BrandExperienceEditorAction
    data class PreviewRejected(val message: String) : BrandExperienceEditorAction
    data object SaveDraftStarted : BrandExperienceEditorAction
    data class DraftSaved(val id: String, val configuration: GlobalUiConfiguration) : BrandExperienceEditorAction
    data class ResumeDraft(val id: String, val configuration: GlobalUiConfiguration) : BrandExperienceEditorAction
    data object PublishStarted : BrandExperienceEditorAction
    data class Published(val versionId: String, val refreshPending: Boolean) : BrandExperienceEditorAction
    data class Failed(val message: String) : BrandExperienceEditorAction
    data object ResumeEditing : BrandExperienceEditorAction
}

fun reduceBrandExperienceState(
    state: BrandExperienceEditorState,
    action: BrandExperienceEditorAction,
): BrandExperienceEditorState = when (action) {
    is BrandExperienceEditorAction.SetPrimarySeed -> state.copy(
        working = state.working.copy(
            appearance = state.working.appearance.copy(primarySeed = action.value),
        ),
        phase = BrandExperiencePhase.EDITING,
        draftId = null,
        savedDraftConfiguration = null,
        message = null,
    )
    is BrandExperienceEditorAction.SetAccentSeed -> state.copy(
        working = state.working.copy(
            appearance = state.working.appearance.copy(accentSeed = action.value),
        ),
        phase = BrandExperiencePhase.EDITING,
        draftId = null,
        savedDraftConfiguration = null,
        message = null,
    )
    is BrandExperienceEditorAction.SetBackgroundSeed -> state.copy(
        working = state.working.copy(
            appearance = state.working.appearance.copy(backgroundSeed = action.value),
        ),
        phase = BrandExperiencePhase.EDITING,
        draftId = null,
        savedDraftConfiguration = null,
        message = null,
    )
    is BrandExperienceEditorAction.SetTypographyScale -> state.copy(
        working = state.working.copy(
            typography = state.working.typography.copy(scalePreset = action.value),
        ),
        phase = BrandExperiencePhase.EDITING,
        draftId = null,
        savedDraftConfiguration = null,
        message = null,
    )
    BrandExperienceEditorAction.Preview -> state.copy(
        preview = state.working,
        phase = BrandExperiencePhase.PREVIEWING,
        message = null,
    )
    is BrandExperienceEditorAction.PreviewRejected -> state.copy(
        phase = BrandExperiencePhase.EDITING,
        message = action.message,
    )
    BrandExperienceEditorAction.SaveDraftStarted -> state.copy(
        phase = BrandExperiencePhase.SAVING_DRAFT,
        message = null,
    )
    is BrandExperienceEditorAction.DraftSaved -> if (state.working == action.configuration) {
        state.copy(
            phase = BrandExperiencePhase.DRAFT_SAVED,
            draftId = action.id,
            savedDraftConfiguration = action.configuration,
            message = "Draft saved. Review the preview before publishing.",
        )
    } else {
        state.copy(
            phase = BrandExperiencePhase.EDITING,
            draftId = null,
            savedDraftConfiguration = null,
            message = "Draft saved for earlier edits. Save the current configuration before publishing.",
        )
    }
    is BrandExperienceEditorAction.ResumeDraft -> state.copy(
        baseline = action.configuration,
        working = action.configuration,
        preview = action.configuration,
        phase = BrandExperiencePhase.DRAFT_SAVED,
        draftId = action.id,
        savedDraftConfiguration = action.configuration,
        publishedVersionId = null,
        message = "Draft resumed. Review the preview before publishing.",
    )
    BrandExperienceEditorAction.PublishStarted -> state.copy(
        phase = BrandExperiencePhase.PUBLISHING,
        message = null,
    )
    is BrandExperienceEditorAction.Published -> state.copy(
        baseline = state.working,
        preview = state.working,
        phase = BrandExperiencePhase.PUBLISHED,
        draftId = null,
        savedDraftConfiguration = null,
        publishedVersionId = action.versionId,
        message = if (action.refreshPending) {
            "Published successfully. Refresh is pending; reopen this workspace to load the latest effective configuration."
        } else {
            "Published successfully."
        },
    )
    is BrandExperienceEditorAction.Failed -> state.copy(
        phase = BrandExperiencePhase.ERROR,
        message = action.message,
    )
    BrandExperienceEditorAction.ResumeEditing -> state.copy(
        phase = BrandExperiencePhase.EDITING,
        message = null,
    )
}
