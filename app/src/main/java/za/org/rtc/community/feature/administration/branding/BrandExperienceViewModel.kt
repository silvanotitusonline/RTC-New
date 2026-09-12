package za.org.rtc.community.feature.administration.branding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.AppearanceConfiguration
import za.org.rtc.community.core.BrandDensityPreset
import za.org.rtc.community.core.BrandFontFamily
import za.org.rtc.community.core.BrandShapePreset
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.HomeImageAspectPreset
import za.org.rtc.community.core.HomeImageContentScale
import za.org.rtc.community.core.HomeImagePlacement
import za.org.rtc.community.core.HomeImageWidget
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.HomeSection
import za.org.rtc.community.core.LaunchTreatment
import za.org.rtc.community.core.LauncherIconId
import za.org.rtc.community.core.ScreenDensity
import za.org.rtc.community.core.ScreenHeaderStyle
import za.org.rtc.community.core.ScreenPresentation
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.TypographyScalePreset
import za.org.rtc.community.core.TypographyWeightPreset
import za.org.rtc.community.core.UiConfigurationValidator
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.data.RtcRepository
import za.org.rtc.community.data.ui_config.UiConfigurationRepository
import za.org.rtc.community.data.ui_config.UiConfigurationVersionSummary
import za.org.rtc.community.ui.theme.BrandPaletteEngine

enum class CustomizableScreen {
    WELCOME,
    HOME,
    COMMUNITY,
    EXPLORE,
    SUPPORT,
    ACCOUNT,
    NOTIFICATIONS,
    SEARCH,
    HELP,
    MARKETPLACE,
    MARKETPLACE_SEARCH,
    MARKETPLACE_MAP,
    MARKETPLACE_ACCOUNT,
}

data class BrandExperienceUiState(
    val editor: BrandExperienceEditorState = BrandExperienceEditorState.initial(GlobalUiConfiguration.default()),
    val authorized: Boolean = false,
    val authorizationMessage: String = "Verifying System Administrator access…",
    val selectedScreen: CustomizableScreen = CustomizableScreen.HOME,
    val draftReason: String = "",
    val publishReason: String = "",
    val publishConfirmation: String = "",
    val history: List<UiConfigurationVersionSummary> = emptyList(),
    val historyLoading: Boolean = false,
    val mutationInFlight: Boolean = false,
    val homeImagePreviewUrl: String? = null,
    val imageUploadWorking: Boolean = false,
    val imageMessage: String? = null,
) {
    val configuration: GlobalUiConfiguration get() = editor.working
    val preview: GlobalUiConfiguration get() = editor.preview
    val validatorPasses: Boolean get() = UiConfigurationValidator.isValid(configuration)
    val contrastPasses: Boolean get() = runCatching {
        BrandPaletteEngine.hasPublishableContrast(configuration.appearance)
    }.getOrDefault(false)
    val canSaveDraft: Boolean get() = authorized && validatorPasses && contrastPasses &&
        draftReason.trim().length in 3..500 &&
        !imageUploadWorking && !mutationInFlight &&
            editor.phase !in setOf(BrandExperiencePhase.SAVING_DRAFT, BrandExperiencePhase.PUBLISHING)
    val canPublish: Boolean get() = authorized && editor.draftId != null &&
        editor.savedDraftConfiguration == configuration && validatorPasses && contrastPasses &&
        publishReason.trim().length in 3..500 &&
        publishConfirmation == UiConfigurationRepository.PUBLISH_CONFIRMATION &&
        !imageUploadWorking && !mutationInFlight &&
            editor.phase !in setOf(BrandExperiencePhase.SAVING_DRAFT, BrandExperiencePhase.PUBLISHING)
}

@HiltViewModel
class BrandExperienceViewModel @Inject constructor(
    private val uiRepository: UiConfigurationRepository,
    rtcRepository: RtcRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrandExperienceUiState())
    val uiState: StateFlow<BrandExperienceUiState> = _uiState.asStateFlow()
    private val pendingPublishedAssetCleanup = linkedSetOf<String>()

    init {
        viewModelScope.launch {
            rtcRepository.session.collect { session ->
                val liveAdmin = session.role == UserRole.SYSTEM_ADMIN &&
                    session.authority == SessionAuthority.SUPABASE_AUTH &&
                    session.administratorMfaStatus == AdministratorMfaStatus.VERIFIED
                _uiState.update {
                    it.copy(
                        authorized = liveAdmin,
                        authorizationMessage = when {
                            session.role != UserRole.SYSTEM_ADMIN -> "System Administrator access is required."
                            session.authority != SessionAuthority.SUPABASE_AUTH -> "Sign in with the live System Administrator account to edit published branding."
                            session.administratorMfaStatus != AdministratorMfaStatus.VERIFIED -> "Verify administrator MFA before editing or publishing application branding."
                            else -> "Protected System Administrator branding workspace."
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            uiRepository.refreshEffectiveConfiguration().onSuccess { configuration ->
                _uiState.update { it.copy(editor = BrandExperienceEditorState.initial(configuration)) }
            }
            refreshHistory()
        }
    }

    fun selectScreen(screen: CustomizableScreen) = _uiState.update { it.copy(selectedScreen = screen) }
    fun setDraftReason(value: String) = _uiState.update { it.copy(draftReason = value.take(500)) }
    fun setPublishReason(value: String) = _uiState.update { it.copy(publishReason = value.take(500)) }
    fun setPublishConfirmation(value: String) = _uiState.update { it.copy(publishConfirmation = value.take(80)) }

    fun updateAppearance(transform: (AppearanceConfiguration) -> AppearanceConfiguration) = edit { config ->
        config.copy(appearance = transform(config.appearance))
    }

    fun setShape(value: BrandShapePreset) = updateAppearance { it.copy(shapePreset = value) }
    fun setGlobalDensity(value: BrandDensityPreset) = updateAppearance { it.copy(densityPreset = value) }
    fun setFontFamily(value: BrandFontFamily) = edit { it.copy(typography = it.typography.copy(fontFamily = value)) }
    fun setTypographyScale(value: TypographyScalePreset) = edit { it.copy(typography = it.typography.copy(scalePreset = value)) }
    fun setTypographyWeight(value: TypographyWeightPreset) = edit { it.copy(typography = it.typography.copy(weightPreset = value)) }

    fun setWelcomeHeadline(value: String) = edit { it.copy(welcome = it.welcome.copy(headline = value.take(100))) }
    fun setWelcomeSupportingText(value: String) = edit { it.copy(welcome = it.welcome.copy(supportingText = value.take(280))) }
    fun setWelcomePrimaryAction(value: String) = edit { it.copy(welcome = it.welcome.copy(primaryActionLabel = value.take(40))) }
    fun setWelcomeSecondaryAction(value: String) = edit { it.copy(welcome = it.welcome.copy(secondaryActionLabel = value.take(40))) }
    fun setLaunchTreatment(value: LaunchTreatment) = edit { it.copy(welcome = it.welcome.copy(launchTreatment = value)) }
    fun setRecommendedLauncherIcon(value: LauncherIconId) = edit {
        it.copy(launcher = it.launcher.copy(recommendedIconId = value))
    }

    fun moveHomeSection(section: HomeSection, direction: Int) = edit { config ->
        val sections = config.home.sections.toMutableList()
        val index = sections.indexOf(section)
        if (index < 0) return@edit config
        val target = (index + direction).coerceIn(0, sections.lastIndex)
        if (target != index) {
            sections.removeAt(index)
            sections.add(target, section)
        }
        config.copy(home = config.home.copy(sections = sections))
    }

    fun setHomeSectionVisible(section: HomeSection, visible: Boolean) = edit { config ->
        if (section in HomeLayout.mandatorySections && !visible) return@edit config
        val sections = config.home.sections.toMutableList()
        if (visible && section !in sections) sections.add(section)
        if (!visible) sections.remove(section)
        if (sections.size < 3) return@edit config
        val image = config.home.imageWidget?.takeIf { it.anchorSection in sections }
        config.copy(home = config.home.copy(sections = sections, imageWidget = image))
    }

    fun setHomeImageMetadata(
        anchor: HomeSection? = null,
        placement: HomeImagePlacement? = null,
        aspect: HomeImageAspectPreset? = null,
        contentScale: HomeImageContentScale? = null,
        altText: String? = null,
        caption: String? = null,
    ) = edit { config ->
        val current = config.home.imageWidget ?: return@edit config
        config.copy(
            home = config.home.copy(
                imageWidget = current.copy(
                    anchorSection = anchor ?: current.anchorSection,
                    placement = placement ?: current.placement,
                    aspectPreset = aspect ?: current.aspectPreset,
                    contentScale = contentScale ?: current.contentScale,
                    altText = altText?.take(180) ?: current.altText,
                    caption = caption?.take(240) ?: current.caption,
                ),
            ),
        )
    }

    fun removeHomeImage() {
        val state = _uiState.value
        if (state.mutationInFlight) return
        val retiredAssetId = state.configuration.home.imageWidget?.assetId
        edit { config -> config.copy(home = config.home.copy(imageWidget = null)) }
        _uiState.update {
            it.copy(homeImagePreviewUrl = null, imageMessage = "Home image removed from this draft.")
        }
        retireHomeImageAsset(retiredAssetId)
    }

    fun uploadHomeImage(bytes: ByteArray, mimeType: String, width: Int, height: Int) {
        val state = _uiState.value
        if (!state.authorized || state.mutationInFlight) return
        _uiState.update { it.copy(imageUploadWorking = true, imageMessage = null) }
        viewModelScope.launch {
            uiRepository.uploadUiImage(bytes, mimeType, width, height)
                .onSuccess { assetId ->
                    val retiredAssetId = _uiState.value.configuration.home.imageWidget?.assetId
                    val anchor = _uiState.value.configuration.home.sections.firstOrNull { it == HomeSection.QUICK_ACCESS }
                        ?: _uiState.value.configuration.home.sections.first()
                    edit { config ->
                        config.copy(
                            home = config.home.copy(
                                imageWidget = HomeImageWidget(
                                    assetId = assetId,
                                    anchorSection = anchor,
                                    placement = HomeImagePlacement.AFTER,
                                    aspectPreset = HomeImageAspectPreset.WIDE,
                                    contentScale = HomeImageContentScale.CROP,
                                    altText = "RTC Home dashboard image",
                                ),
                            ),
                        )
                    }
                    retireHomeImageAsset(retiredAssetId?.takeIf { it != assetId })
                    val url = uiRepository.signedUiImageUrl(assetId).getOrNull()
                    _uiState.update {
                        it.copy(
                            imageUploadWorking = false,
                            homeImagePreviewUrl = url,
                            imageMessage = "Image uploaded. Set placement and accessible alt text, then preview.",
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(imageUploadWorking = false, imageMessage = "Image upload could not be completed.")
                    }
                }
        }
    }

    fun setScreenPresentation(
        screen: CustomizableScreen,
        header: ScreenHeaderStyle? = null,
        density: ScreenDensity? = null,
    ) = edit { config ->
        fun changed(current: ScreenPresentation) = current.copy(
            headerStyle = header ?: current.headerStyle,
            density = density ?: current.density,
        )
        val screens = config.screens
        config.copy(
            screens = when (screen) {
                CustomizableScreen.COMMUNITY -> screens.copy(community = changed(screens.community))
                CustomizableScreen.EXPLORE -> screens.copy(explore = changed(screens.explore))
                CustomizableScreen.SUPPORT -> screens.copy(support = changed(screens.support))
                CustomizableScreen.ACCOUNT -> screens.copy(account = changed(screens.account))
                CustomizableScreen.NOTIFICATIONS -> screens.copy(notifications = changed(screens.notifications))
                CustomizableScreen.SEARCH -> screens.copy(search = changed(screens.search))
                CustomizableScreen.HELP -> screens.copy(help = changed(screens.help))
                CustomizableScreen.MARKETPLACE -> screens.copy(marketplace = changed(screens.marketplace))
                CustomizableScreen.MARKETPLACE_SEARCH -> screens.copy(marketplaceSearch = changed(screens.marketplaceSearch))
                CustomizableScreen.MARKETPLACE_MAP -> screens.copy(marketplaceMap = changed(screens.marketplaceMap))
                CustomizableScreen.MARKETPLACE_ACCOUNT -> screens.copy(marketplaceAccount = changed(screens.marketplaceAccount))
                CustomizableScreen.WELCOME, CustomizableScreen.HOME -> screens
            },
        )
    }

    fun preview() {
        val state = _uiState.value
        if (state.mutationInFlight) return
        val action = if (!state.validatorPasses || !state.contrastPasses) {
            BrandExperienceEditorAction.PreviewRejected(
                "Correct blocking validation or contrast errors before previewing.",
            )
        } else {
            BrandExperienceEditorAction.Preview
        }
        _uiState.update { current ->
            current.copy(editor = reduceBrandExperienceState(current.editor, action))
        }
    }

    fun reportImageSelectionFailure(message: String) {
        _uiState.update { state -> state.copy(imageMessage = message.take(180)) }
    }

    fun saveDraft() {
        val state = _uiState.value
        if (!state.canSaveDraft) return
        if (!beginMutation()) return
        val snapshot = state.configuration
        val reason = state.draftReason
        _uiState.update {
            it.copy(editor = reduceBrandExperienceState(it.editor, BrandExperienceEditorAction.SaveDraftStarted))
        }
        viewModelScope.launch {
            uiRepository.createDraft(snapshot, reason)
                .onSuccess { id ->
                    _uiState.update {
                        it.copy(
                            mutationInFlight = false,
                            editor = reduceBrandExperienceState(
                                it.editor,
                                BrandExperienceEditorAction.DraftSaved(id, snapshot),
                            ),
                        )
                    }
                }
                .onFailure(::fail)
        }
    }

    fun publish() {
        val state = _uiState.value
        val draftId = state.editor.draftId ?: return
        if (!state.canPublish) return
        val snapshot = state.editor.savedDraftConfiguration ?: return
        if (!beginMutation()) return
        val reason = state.publishReason
        val confirmation = state.publishConfirmation
        _uiState.update {
            it.copy(editor = reduceBrandExperienceState(it.editor, BrandExperienceEditorAction.PublishStarted))
        }
        viewModelScope.launch {
            uiRepository.publishDraft(draftId, reason, confirmation)
                .onSuccess { commit ->
                    val published = if (commit.effectiveConfigurationRefreshed) {
                        uiRepository.effectiveConfiguration.value
                    } else {
                        snapshot
                    }
                    _uiState.update { current ->
                        current.copy(
                            mutationInFlight = false,
                            editor = reduceBrandExperienceState(
                                current.editor.copy(working = published),
                                BrandExperienceEditorAction.Published(
                                    versionId = commit.versionId,
                                    refreshPending = !commit.effectiveConfigurationRefreshed,
                                ),
                            ),
                            publishConfirmation = "",
                            draftReason = "",
                            publishReason = "",
                        )
                    }
                    cleanupRetiredAssets(published.home.imageWidget?.assetId)
                    refreshHistory()
                }
                .onFailure(::fail)
        }
    }

    fun restore(versionId: String, reason: String, confirmation: String) {
        val state = _uiState.value
        if (!state.authorized || state.mutationInFlight) return
        if (!beginMutation()) return
        _uiState.update {
            it.copy(editor = reduceBrandExperienceState(it.editor, BrandExperienceEditorAction.PublishStarted))
        }
        viewModelScope.launch {
            uiRepository.revert(versionId, reason, confirmation)
                .onSuccess { commit ->
                    val published = if (commit.effectiveConfigurationRefreshed) {
                        uiRepository.effectiveConfiguration.value
                    } else {
                        _uiState.value.configuration
                    }
                    _uiState.update { state ->
                        state.copy(
                            mutationInFlight = false,
                            editor = if (commit.effectiveConfigurationRefreshed) {
                                BrandExperienceEditorState.initial(published)
                            } else {
                                reduceBrandExperienceState(
                                    state.editor.copy(working = published),
                                    BrandExperienceEditorAction.Published(
                                        versionId = commit.versionId,
                                        refreshPending = true,
                                    ),
                                )
                            },
                        )
                    }
                    refreshHistory()
                }
                .onFailure(::fail)
        }
    }

    fun resumeDraft(version: UiConfigurationVersionSummary) {
        val state = _uiState.value
        if (
            !state.authorized ||
            state.mutationInFlight ||
            version.state != "DRAFT"
        ) return
        val configuration = GlobalUiConfiguration.decodeOrNull(version.configuration.toString()) ?: return
        _uiState.update { state ->
            state.copy(
                editor = reduceBrandExperienceState(
                    state.editor,
                    BrandExperienceEditorAction.ResumeDraft(version.versionId, configuration),
                ),
            )
        }
    }

    fun refreshHistory() {
        if (!_uiState.value.authorized) return
        _uiState.update { it.copy(historyLoading = true) }
        viewModelScope.launch {
            uiRepository.history()
                .onSuccess { history -> _uiState.update { it.copy(history = history, historyLoading = false) } }
                .onFailure { _uiState.update { it.copy(historyLoading = false) } }
        }
    }

    private fun edit(transform: (GlobalUiConfiguration) -> GlobalUiConfiguration) {
        _uiState.update { state ->
            if (state.mutationInFlight) return@update state
            val next = transform(state.editor.working)
            val changed = next != state.editor.working
            state.copy(
                editor = state.editor.copy(
                    working = next,
                    phase = if (changed) BrandExperiencePhase.EDITING else state.editor.phase,
                    draftId = if (changed) null else state.editor.draftId,
                    savedDraftConfiguration = if (changed) null else state.editor.savedDraftConfiguration,
                    message = if (changed && state.editor.draftId != null) {
                        "Edits changed after the draft was saved. Save a new draft before publishing."
                    } else if (changed) null else state.editor.message,
                ),
            )
        }
    }

    private fun retireHomeImageAsset(assetId: String?) {
        if (assetId.isNullOrBlank()) return
        val publishedAssetId = uiRepository.effectiveConfiguration.value.home.imageWidget?.assetId
        if (assetId == publishedAssetId) {
            pendingPublishedAssetCleanup += assetId
            return
        }
        viewModelScope.launch { uiRepository.deleteDraftAsset(assetId) }
    }

    private suspend fun cleanupRetiredAssets(currentPublishedAssetId: String?) {
        val retired = pendingPublishedAssetCleanup.toList()
        pendingPublishedAssetCleanup.clear()
        retired.filter { it != currentPublishedAssetId }.forEach { assetId ->
            uiRepository.deleteDraftAsset(assetId)
        }
    }

    private fun fail(failure: Throwable) {
        // Keep technical Supabase/SQL details out of the administrator UI. Server-side logs and
        // immutable configuration events remain the diagnostic source for protected operations.
        val safeMessage = when (failure) {
            is IllegalArgumentException -> failure.message ?: "Check the configuration and try again."
            else -> "The protected branding operation could not be completed. Review access, MFA and the configuration, then try again."
        }
        _uiState.update {
            it.copy(
                mutationInFlight = false,
                editor = reduceBrandExperienceState(it.editor, BrandExperienceEditorAction.Failed(safeMessage)),
            )
        }
    }

    private fun beginMutation(): Boolean {
        val state = _uiState.value
        if (!state.authorized || state.mutationInFlight) return false
        _uiState.value = state.copy(mutationInFlight = true)
        return true
    }
}
