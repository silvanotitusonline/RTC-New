package za.org.rtc.community.feature.marketplace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.feature.marketplace.data.remote.MarketplaceProductionRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminQueue
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminSubmissionDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMutationCommand
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMutationTracker
import javax.inject.Inject

@HiltViewModel
class MarketplaceAdminViewModel @Inject constructor(
    private val repository: MarketplaceAdminRepository,
    private val productionRepository: MarketplaceProductionRepository,
) : ViewModel() {
    private val mutations = MarketplaceMutationTracker()
    private val _queue = MutableStateFlow<MarketplaceLoadState<MarketplaceAdminQueue>>(MarketplaceLoadState.Idle)
    val queue = _queue.asStateFlow()
    private val _detail = MutableStateFlow<MarketplaceLoadState<MarketplaceAdminSubmissionDetail>>(MarketplaceLoadState.Idle)
    val detail = _detail.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private var activeSubmissionId: String? = null

    fun dismissNotice() { _notice.value = null }

    fun load() = viewModelScope.launch {
        _queue.value = MarketplaceLoadState.Loading
        repository.queue().fold(
            { _queue.value = MarketplaceLoadState.Data(it) },
            { _queue.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun loadDetail(submissionId: String) = viewModelScope.launch {
        activeSubmissionId = submissionId
        _detail.value = MarketplaceLoadState.Loading
        productionRepository.adminSubmissionDetail(submissionId).fold(
            { _detail.value = MarketplaceLoadState.Data(it) },
            { _detail.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun assign(id: String) = viewModelScope.launch {
        val command = mutations.begin("ASSIGN", id) ?: return@launch
        repository.assign(id).fold(
            {
                mutations.succeeded(command)
                _notice.value = "Submission assigned."
                refreshAuthoritativeState()
            },
            {
                mutations.failed(command)
                _notice.value = it.userMessage()
            },
        )
    }

    fun requestChanges(id: String, feedback: String) = viewModelScope.launch {
        adminMutation("REQUEST_CHANGES", id) { key -> repository.requestChanges(id, feedback, key) }
    }

    fun publish(id: String) = viewModelScope.launch {
        adminMutation("PUBLISH", id) { key -> repository.publish(id, key).map { Unit } }
    }

    fun reject(id: String, feedback: String) = viewModelScope.launch {
        adminMutation("REJECT", id) { key -> repository.reject(id, feedback, key) }
    }

    fun suspendBusiness(businessId: String, reason: String) = viewModelScope.launch {
        adminMutation("SUSPEND", businessId) { key -> repository.suspendBusiness(businessId, reason, key) }
    }

    fun reinstateBusiness(businessId: String) = viewModelScope.launch {
        adminMutation("REINSTATE", businessId) { key -> repository.reinstateBusiness(businessId, key) }
    }

    private fun refreshAuthoritativeState() {
        load()
        activeSubmissionId?.let(::loadDetail)
    }

    private suspend fun adminMutation(
        action: String,
        subjectId: String,
        block: suspend (String) -> Result<Unit>,
    ) {
        val command: MarketplaceMutationCommand = mutations.begin(action, subjectId) ?: return
        block(command.key).fold(
            onSuccess = {
                mutations.succeeded(command)
                _notice.value = when (action) {
                    "REQUEST_CHANGES" -> "Changes requested."
                    "PUBLISH" -> "Business published."
                    "REJECT" -> "Submission rejected."
                    "SUSPEND" -> "Business suspended."
                    "REINSTATE" -> "Business reinstated."
                    else -> "Marketplace action completed."
                }
                refreshAuthoritativeState()
            },
            onFailure = {
                mutations.failed(command)
                _notice.value = it.userMessage()
            },
        )
    }
}
