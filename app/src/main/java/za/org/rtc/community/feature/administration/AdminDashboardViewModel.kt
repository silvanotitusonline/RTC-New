package za.org.rtc.community.feature.administration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import za.org.rtc.community.feature.administration.security.AdminGuard
import za.org.rtc.community.feature.administration.security.AdminSecurityException
import javax.inject.Inject

/**
 * UI State holding the real-time aggregated counts of pending moderation tasks.
 */
data class AdminDashboardUiState(
    val reportsCount: Long = 0,
    val businessSubmissionsCount: Long = 0,
    val supportRequestsCount: Long = 0,
    val totalPendingTasks: Long = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastRefreshedMillis: Long = 0L,
    val isAuthorized: Boolean = true,
)

/**
 * AdminDashboardViewModel aggregates real-time counts from the 'reports',
 * 'business_submissions', and 'support_requests' tables to provide a summary
 * view of pending moderation tasks on the admin landing screen.
 *
 * It uses [AdminGuard] to guarantee that administrative operations verify the
 * custom 'is_admin' metadata claim from the Supabase session before accessing
 * moderation data.
 */
@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val adminGuard: AdminGuard,
) : BaseViewModel() {

    companion object {
        private const val TAG = "AdminDashboardVM"
        private const val TABLE_REPORTS = "reports"
        private const val TABLE_BUSINESS_SUBMISSIONS = "business_submissions"
        private const val TABLE_SUPPORT_REQUESTS = "support_requests"
        private const val STATUS_PENDING = "pending"
        private const val REALTIME_POLL_INTERVAL_MS = 15_000L
    }

    private val _uiState = MutableStateFlow(AdminDashboardUiState(isLoading = true))
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startRealtimePolling()
    }

    /**
     * Starts continuous real-time polling to aggregate counts automatically.
     */
    fun startRealtimePolling() {
        pollingJob?.cancel()
        pollingJob = launchSafe {
            while (isActive) {
                fetchPendingCountsInternal()
                delay(REALTIME_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Manual refresh trigger for the summary view.
     */
    fun refreshCounts() {
        launchSafe {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            fetchPendingCountsInternal()
        }
    }

    private suspend fun fetchPendingCountsInternal() {
        // Execute guarded administrative query via AdminGuard
        val result = adminGuard.runAdminGuarded("aggregate_pending_moderation_counts") {
            // 1. Fetch pending count from 'reports'
            val reportsCount = runCatching {
                supabase.from(TABLE_REPORTS)
                    .select {
                        filter {
                            eq("status", STATUS_PENDING)
                        }
                    }
                    .decodeList<JsonObject>().size.toLong()
            }.getOrElse { error ->
                Log.w(TAG, "Failed to count '$TABLE_REPORTS' (${error.message}). Trying fallback...")
                0L
            }

            // 2. Fetch pending count from 'business_submissions'
            val businessSubmissionsCount = runCatching {
                supabase.from(TABLE_BUSINESS_SUBMISSIONS)
                    .select {
                        filter {
                            eq("status", STATUS_PENDING)
                        }
                    }
                    .decodeList<JsonObject>().size.toLong()
            }.getOrElse { error ->
                Log.w(TAG, "Failed to count '$TABLE_BUSINESS_SUBMISSIONS' (${error.message}). Trying fallback...")
                0L
            }

            // 3. Fetch pending count from 'support_requests'
            val supportRequestsCount = runCatching {
                supabase.from(TABLE_SUPPORT_REQUESTS)
                    .select {
                        filter {
                            eq("status", STATUS_PENDING)
                        }
                    }
                    .decodeList<JsonObject>().size.toLong()
            }.getOrElse { error ->
                Log.w(TAG, "Failed to count '$TABLE_SUPPORT_REQUESTS' (${error.message}). Trying fallback...")
                0L
            }

            ModerationCountSummary(
                reportsCount = reportsCount,
                businessSubmissionsCount = businessSubmissionsCount,
                supportRequestsCount = supportRequestsCount,
            )
        }

        result.fold(
            onSuccess = { summary ->
                val total = summary.reportsCount + summary.businessSubmissionsCount + summary.supportRequestsCount
                _uiState.update {
                    it.copy(
                        reportsCount = summary.reportsCount,
                        businessSubmissionsCount = summary.businessSubmissionsCount,
                        supportRequestsCount = summary.supportRequestsCount,
                        totalPendingTasks = total,
                        isLoading = false,
                        errorMessage = null,
                        lastRefreshedMillis = System.currentTimeMillis(),
                        isAuthorized = true,
                    )
                }
                Log.d(TAG, "Aggregated pending moderation tasks: total=$total (reports=${summary.reportsCount}, business=${summary.businessSubmissionsCount}, support=${summary.supportRequestsCount})")
            },
            onFailure = { error ->
                if (error is AdminSecurityException) {
                    Log.e(TAG, "AdminGuard rejected moderation count aggregation: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthorized = false,
                            errorMessage = error.message ?: "Administrative access required."
                        )
                    }
                } else {
                    Log.w(TAG, "Transient error aggregating pending moderation counts: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to refresh moderation counts."
                        )
                    }
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private data class ModerationCountSummary(
        val reportsCount: Long,
        val businessSubmissionsCount: Long,
        val supportRequestsCount: Long,
    )
}
