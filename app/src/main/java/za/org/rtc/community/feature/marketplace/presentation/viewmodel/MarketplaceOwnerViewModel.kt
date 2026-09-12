package za.org.rtc.community.feature.marketplace.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import za.org.rtc.community.feature.marketplace.data.local.BusinessCreationDraft
import za.org.rtc.community.feature.marketplace.data.local.MarketplaceDraftCheckpointStore
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDiscoveryRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDraftEditor
import za.org.rtc.community.feature.marketplace.domain.MarketplaceInvitation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMediaOperation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMediaStage
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMutationTracker
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerBusiness
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSubmissionStatus
import javax.inject.Inject

@HiltViewModel
class MarketplaceOwnerViewModel @Inject constructor(
    private val repository: MarketplaceOwnerRepository,
    private val discoveryRepository: MarketplaceDiscoveryRepository,
    private val checkpoints: MarketplaceDraftCheckpointStore,
    private val state: SavedStateHandle,
) : ViewModel() {
    private val mutations = MarketplaceMutationTracker()
    private val _businesses = MutableStateFlow<MarketplaceLoadState<List<MarketplaceOwnerBusiness>>>(MarketplaceLoadState.Idle)
    val businesses = _businesses.asStateFlow()
    private val _editor = MutableStateFlow<MarketplaceLoadState<MarketplaceDraftEditor>>(MarketplaceLoadState.Idle)
    val editor = _editor.asStateFlow()
    private val _status = MutableStateFlow<MarketplaceLoadState<MarketplaceSubmissionStatus>>(MarketplaceLoadState.Idle)
    val status = _status.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _categories = MutableStateFlow<List<MarketplaceCategory>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _invitations = MutableStateFlow<MarketplaceLoadState<List<MarketplaceInvitation>>>(MarketplaceLoadState.Idle)
    val invitations = _invitations.asStateFlow()
    private val _currentStep = MutableStateFlow(state["marketplace_editor_step"] ?: 1)
    val currentStep = _currentStep.asStateFlow()
    private val _mediaOperations = MutableStateFlow<Map<String, MarketplaceMediaOperation>>(emptyMap())
    val mediaOperations = _mediaOperations.asStateFlow()
    private val _submissionState = MutableStateFlow<MarketplaceLoadState<Unit>>(MarketplaceLoadState.Idle)
    val submissionState = _submissionState.asStateFlow()
    val creationDraft = checkpoints.creationDraft

    init {
        loadBusinesses()
        loadCategories()
        viewModelScope.launch {
            val savedBusinessId: String? = state["marketplace_editor_business_id"]
            val savedStep: Int? = state["marketplace_editor_step"]
            val checkpoint = checkpoints.read()
            val businessId = savedBusinessId ?: checkpoint?.businessId
            val step = (savedStep ?: checkpoint?.step ?: 1).coerceIn(1, 9)
            if (businessId != null) {
                state["marketplace_editor_business_id"] = businessId
                state["marketplace_editor_step"] = step
                _currentStep.value = step
                loadEditor(businessId)
            }
        }
    }

    fun dismissNotice() { _notice.value = null }

    private fun loadCategories() = viewModelScope.launch {
        discoveryRepository.home(null, null).onSuccess { _categories.value = it.categories }
    }

    fun loadInvitations() = viewModelScope.launch {
        _invitations.value = MarketplaceLoadState.Loading
        repository.invitations().fold(
            { _invitations.value = MarketplaceLoadState.Data(it) },
            { _invitations.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun loadBusinesses() = viewModelScope.launch {
        _businesses.value = MarketplaceLoadState.Loading
        repository.myBusinesses().fold(
            { _businesses.value = MarketplaceLoadState.Data(it) },
            { _businesses.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun autoSaveCreationDraft(draft: BusinessCreationDraft) = viewModelScope.launch {
        checkpoints.saveCreationDraft(draft)
    }

    fun clearCreationDraft() = viewModelScope.launch {
        checkpoints.clearCreationDraft()
    }

    fun createDraft(name: String) = viewModelScope.launch {
        val command = mutations.begin("CREATE_DRAFT") ?: return@launch
        _editor.value = MarketplaceLoadState.Loading
        repository.createDraft(name, command.key).fold(
            onSuccess = { editor ->
                mutations.succeeded(command)
                _editor.value = MarketplaceLoadState.Data(editor)
                state["marketplace_editor_business_id"] = editor.businessId
                checkpoint(editor.businessId, 1)
                checkpoints.clearCreationDraft()
                loadBusinesses()
            },
            onFailure = {
                mutations.failed(command)
                _editor.value = MarketplaceLoadState.Failure(it.userMessage())
            },
        )
    }

    fun loadEditor(businessId: String) = viewModelScope.launch {
        _editor.value = MarketplaceLoadState.Loading
        repository.editor(businessId).fold(
            onSuccess = { editor ->
                _editor.value = MarketplaceLoadState.Data(editor)
                state["marketplace_editor_business_id"] = editor.businessId
            },
            onFailure = { _editor.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun saveIdentity(businessId: String, payload: JsonObject) = viewModelScope.launch {
        mutate("SAVE_IDENTITY", businessId) { key -> repository.saveIdentity(businessId, payload, key) }.fold(
            { _editor.value = MarketplaceLoadState.Data(it); _notice.value = "Identity saved to your private draft." },
            { _notice.value = it.userMessage() },
        )
    }

    fun saveLocation(businessId: String, locationId: String?, payload: JsonObject) = viewModelScope.launch {
        mutate("SAVE_LOCATION", "$businessId:${locationId ?: "new"}") { key -> repository.saveLocation(businessId, locationId, payload, key) }.fold(
            { _notice.value = "Location saved."; loadEditor(businessId) },
            { _notice.value = it.userMessage() },
        )
    }

    fun saveHours(businessId: String, locationId: String, hours: List<JsonObject>, exceptions: List<JsonObject>) = viewModelScope.launch {
        mutate("SAVE_HOURS", "$businessId:$locationId") { key -> repository.saveHours(businessId, locationId, hours, exceptions, key) }.fold(
            { _notice.value = "Opening hours saved."; loadEditor(businessId) },
            { _notice.value = it.userMessage() },
        )
    }

    fun saveOffering(businessId: String, offeringId: String?, payload: JsonObject) = viewModelScope.launch {
        mutate("SAVE_OFFERING", "$businessId:${offeringId ?: "new"}") { key -> repository.saveOffering(businessId, offeringId, payload, key) }.fold(
            { _notice.value = "Offering saved."; loadEditor(businessId) },
            { _notice.value = it.userMessage() },
        )
    }

    fun uploadMedia(businessId: String, assetType: String, sourceUri: String, altText: String) = viewModelScope.launch {
        val subject = "$businessId:$sourceUri"
        val command = mutations.begin("UPLOAD_MEDIA", subject) ?: return@launch
        var operation = _mediaOperations.value[sourceUri] ?: MarketplaceMediaOperation.start(sourceUri, command.key)
        _mediaOperations.value = _mediaOperations.value + (sourceUri to operation)
        repository.uploadMedia(
            businessId = businessId,
            assetType = assetType,
            sourceUri = sourceUri,
            altText = altText,
            uploadKey = command.key,
            idempotencyKey = command.key,
            onProgress = { stage, progress ->
                operation = when (stage) {
                    MarketplaceMediaStage.PREPARING -> MarketplaceMediaOperation.start(sourceUri, command.key)
                    MarketplaceMediaStage.UPLOADING -> operation.uploading(progress)
                    MarketplaceMediaStage.FINALIZING -> operation.finalizing()
                    MarketplaceMediaStage.COMPLETED -> operation.copy(stage = MarketplaceMediaStage.COMPLETED, progress = 1f)
                    MarketplaceMediaStage.FAILED -> operation.failed("Upload failed")
                }
                _mediaOperations.value = _mediaOperations.value + (sourceUri to operation)
            },
        ).fold(
            onSuccess = { asset ->
                mutations.succeeded(command)
                _mediaOperations.value = _mediaOperations.value + (sourceUri to operation.completed(asset.id))
                _notice.value = "Media finalized in your private draft."
                loadEditor(businessId)
            },
            onFailure = {
                mutations.failed(command)
                _mediaOperations.value = _mediaOperations.value + (sourceUri to operation.failed(it.userMessage()))
                _notice.value = it.userMessage()
            },
        )
    }

    fun deleteMedia(businessId: String, assetId: String) = viewModelScope.launch {
        mutate("DELETE_MEDIA", "$businessId:$assetId") { key -> repository.deleteMedia(businessId, assetId, key) }.fold(
            { _notice.value = "Draft media removed."; loadEditor(businessId) },
            { _notice.value = it.userMessage() },
        )
    }

    fun resetSubmissionState() {
        _submissionState.value = MarketplaceLoadState.Idle
    }

    fun submit(businessId: String) = viewModelScope.launch {
        _submissionState.value = MarketplaceLoadState.Loading
        mutate("SUBMIT", businessId) { key -> repository.submit(businessId, key) }.fold(
            {
                _submissionState.value = MarketplaceLoadState.Data(Unit)
                _notice.value = "Submitted for Marketplace review."
                loadStatus(businessId)
                loadBusinesses()
            },
            {
                _submissionState.value = MarketplaceLoadState.Failure(it.userMessage())
                _notice.value = it.userMessage()
            },
        )
    }

    fun loadStatus(businessId: String) = viewModelScope.launch {
        _status.value = MarketplaceLoadState.Loading
        repository.status(businessId).fold(
            { _status.value = MarketplaceLoadState.Data(it) },
            { _status.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun archive(businessId: String) = viewModelScope.launch {
        mutate("ARCHIVE", businessId) { key -> repository.archive(businessId, key) }.fold(
            { _notice.value = "Business archived."; checkpoints.clear(businessId); loadBusinesses() },
            { _notice.value = it.userMessage() },
        )
    }

    fun checkpoint(businessId: String, step: Int) = viewModelScope.launch {
        val bounded = step.coerceIn(1, 9)
        state["marketplace_editor_business_id"] = businessId
        state["marketplace_editor_step"] = bounded
        _currentStep.value = bounded
        checkpoints.save(businessId, bounded)
    }

    private suspend fun <T> mutate(
        action: String,
        subjectId: String?,
        block: suspend (String) -> Result<T>,
    ): Result<T> {
        val command = mutations.begin(action, subjectId)
            ?: return Result.failure(IllegalStateException("This Marketplace action is already in progress."))
        val result = block(command.key)
        if (result.isSuccess) mutations.succeeded(command) else mutations.failed(command)
        return result
    }
}
