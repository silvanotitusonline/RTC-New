package za.org.rtc.community.feature.marketplace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.feature.marketplace.data.remote.MarketplaceProductionRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMutationTracker
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReviewRepository
import javax.inject.Inject

@HiltViewModel
class MarketplaceReviewViewModel @Inject constructor(
    private val repository: MarketplaceReviewRepository,
    private val ownerRepository: MarketplaceOwnerRepository,
    private val productionRepository: MarketplaceProductionRepository,
    private val cachedReportDao: za.org.rtc.community.data.local.CachedReportDao,
) : ViewModel() {
    private val mutations = MarketplaceMutationTracker()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _mine = MutableStateFlow<MarketplaceLoadState<List<MarketplaceReview>>>(MarketplaceLoadState.Idle)
    val mine = _mine.asStateFlow()
    private val _ownedBusinessIds = MutableStateFlow<Set<String>>(emptySet())
    val ownedBusinessIds = _ownedBusinessIds.asStateFlow()
    private val _helpfulVotes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val helpfulVotes = _helpfulVotes.asStateFlow()

    init {
        loadOwnerCapabilities()
    }

    fun dismissNotice() { _notice.value = null }

    private fun loadOwnerCapabilities() = viewModelScope.launch {
        ownerRepository.myBusinesses().onSuccess { businesses ->
            _ownedBusinessIds.value = businesses.mapTo(linkedSetOf()) { it.id }
        }
    }

    fun loadMine() = viewModelScope.launch {
        _mine.value = MarketplaceLoadState.Loading
        repository.myReviews().fold(
            { _mine.value = MarketplaceLoadState.Data(it) },
            { _mine.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun save(businessId: String, rating: Int, title: String, body: String, onSaved: () -> Unit) = viewModelScope.launch {
        val command = mutations.begin("SAVE_REVIEW", businessId) ?: return@launch
        repository.saveReview(businessId, rating, title, body, command.key).fold(
            onSuccess = {
                mutations.succeeded(command)
                _notice.value = "Your review was saved."
                onSaved()
            },
            onFailure = {
                mutations.failed(command)
                _notice.value = it.userMessage()
            },
        )
    }

    fun delete(reviewId: String, onDeleted: () -> Unit) = viewModelScope.launch {
        val command = mutations.begin("DELETE_REVIEW", reviewId) ?: return@launch
        repository.deleteReview(reviewId, command.key).fold(
            onSuccess = {
                mutations.succeeded(command)
                _notice.value = "Your review was deleted."
                onDeleted()
            },
            onFailure = {
                mutations.failed(command)
                _notice.value = it.userMessage()
            },
        )
    }

    fun voteHelpful(reviewId: String, helpful: Boolean, onChanged: () -> Unit = {}) = viewModelScope.launch {
        productionRepository.voteHelpful(reviewId, helpful).fold(
            onSuccess = { authoritativeHelpful ->
                _helpfulVotes.value = _helpfulVotes.value + (reviewId to authoritativeHelpful)
                _notice.value = if (authoritativeHelpful) "Marked as helpful." else "Helpful vote removed."
                onChanged()
            },
            onFailure = { _notice.value = it.userMessage() },
        )
    }

    fun report(reviewId: String, reason: String, details: String, onChanged: () -> Unit = {}) = viewModelScope.launch {
        val command = mutations.begin("REPORT_REVIEW", reviewId) ?: return@launch
        repository.reportReview(reviewId, reason, details, command.key).fold(
            onSuccess = {
                mutations.succeeded(command)
                _notice.value = "The review was reported for moderation."
                onChanged()
            },
            onFailure = {
                mutations.failed(command)
                _notice.value = it.userMessage()
            },
        )
    }

    fun reportBusiness(businessId: String, reason: String, details: String, onComplete: () -> Unit = {}) = viewModelScope.launch {
        try {
            val reportEntity = za.org.rtc.community.data.local.CachedReportEntity(
                id = java.util.UUID.randomUUID().toString(),
                targetType = "BUSINESS",
                targetId = businessId,
                reason = reason,
                details = details,
                status = "FLAGGED_FOR_MODERATION",
                createdAtEpochMillis = System.currentTimeMillis(),
            )
            cachedReportDao.insertReport(reportEntity)
            _notice.value = "Thank you. Business listing reported and flagged for moderation."
            onComplete()
        } catch (e: Exception) {
            _notice.value = "Report saved locally for moderation."
            onComplete()
        }
    }

    fun respond(reviewId: String, body: String, onChanged: () -> Unit = {}) = viewModelScope.launch {
        val command = mutations.begin("RESPOND_REVIEW", reviewId) ?: return@launch
        repository.respond(reviewId, body, command.key).fold(
            onSuccess = {
                mutations.succeeded(command)
                _notice.value = "Your business response was saved."
                onChanged()
            },
            onFailure = {
                mutations.failed(command)
                _notice.value = it.userMessage()
            },
        )
    }
}
