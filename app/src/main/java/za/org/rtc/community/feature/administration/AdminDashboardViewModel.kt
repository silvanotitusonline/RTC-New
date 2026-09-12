package za.org.rtc.community.feature.administration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardUiState(
    val summary: AdminOperationsSummary = AdminOperationsSummary(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastRefreshedMillis: Long = 0L,
    val eligibleAssigneesByWorkItem: Map<String, List<AdminEligibleAssignee>> = emptyMap(),
    val assigneeLoadingWorkItemId: String? = null,
    val assigneeErrorByWorkItem: Map<String, String> = emptyMap(),
) {
    val hasAttentionItems: Boolean
        get() = summary.highPriority > 0 || summary.overdue > 0 || summary.readyForReview > 0
}

/**
 * Lightweight Operations Hub state. Counts come from a single role-scoped server RPC;
 * failures preserve the last successful summary instead of silently presenting false zeroes.
 * Eligible assignees are fetched only when a System Administrator opens reassignment.
 */
@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val repository: AdminOperationsSummaryRepository,
) : ViewModel() {
    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
    }

    private val _uiState = MutableStateFlow(AdminDashboardUiState(isLoading = true))
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startRefreshLoop()
    }

    fun refreshCounts() {
        viewModelScope.launch { loadSummary(showLoading = true) }
    }

    fun loadEligibleAssignees(workItemId: String) {
        if (_uiState.value.eligibleAssigneesByWorkItem.containsKey(workItemId) ||
            _uiState.value.assigneeLoadingWorkItemId == workItemId
        ) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    assigneeLoadingWorkItemId = workItemId,
                    assigneeErrorByWorkItem = it.assigneeErrorByWorkItem - workItemId,
                )
            }
            repository.loadEligibleAssignees(workItemId).fold(
                onSuccess = { assignees ->
                    _uiState.update {
                        it.copy(
                            eligibleAssigneesByWorkItem = it.eligibleAssigneesByWorkItem + (workItemId to assignees),
                            assigneeLoadingWorkItemId = null,
                            assigneeErrorByWorkItem = it.assigneeErrorByWorkItem - workItemId,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            assigneeLoadingWorkItemId = null,
                            assigneeErrorByWorkItem = it.assigneeErrorByWorkItem + (
                                workItemId to (
                                    error.message?.takeIf(String::isNotBlank)
                                        ?: "Eligible staff could not be loaded."
                                    )
                                ),
                        )
                    }
                },
            )
        }
    }

    fun retryEligibleAssignees(workItemId: String) {
        _uiState.update {
            it.copy(
                eligibleAssigneesByWorkItem = it.eligibleAssigneesByWorkItem - workItemId,
                assigneeErrorByWorkItem = it.assigneeErrorByWorkItem - workItemId,
            )
        }
        loadEligibleAssignees(workItemId)
    }

    fun invalidateEligibleAssignees(workItemId: String) {
        _uiState.update {
            it.copy(
                eligibleAssigneesByWorkItem = it.eligibleAssigneesByWorkItem - workItemId,
                assigneeErrorByWorkItem = it.assigneeErrorByWorkItem - workItemId,
            )
        }
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                loadSummary(showLoading = _uiState.value.lastRefreshedMillis == 0L)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun loadSummary(showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        repository.load().fold(
            onSuccess = { summary ->
                _uiState.update {
                    it.copy(
                        summary = summary,
                        isLoading = false,
                        errorMessage = null,
                        lastRefreshedMillis = System.currentTimeMillis(),
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?.takeIf(String::isNotBlank)
                            ?: "Operations summary could not be refreshed.",
                    )
                }
            },
        )
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}
