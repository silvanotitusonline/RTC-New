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
) {
    val hasAttentionItems: Boolean
        get() = summary.highPriority > 0 || summary.overdue > 0 || summary.readyForReview > 0
}

/**
 * Lightweight Operations Hub summary. Counts come from a single role-scoped server RPC;
 * failures preserve the last successful summary instead of silently presenting false zeroes.
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
                _uiState.value = AdminDashboardUiState(
                    summary = summary,
                    isLoading = false,
                    errorMessage = null,
                    lastRefreshedMillis = System.currentTimeMillis(),
                )
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
