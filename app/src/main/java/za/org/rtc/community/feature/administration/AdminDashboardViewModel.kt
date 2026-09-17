package za.org.rtc.community.feature.administration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import za.org.rtc.community.feature.administration.security.AdminGuard
import za.org.rtc.community.feature.administration.security.AdminSecurityException
import javax.inject.Inject

/**
 * Authoritative, production-backed counts for the staff administration workspace.
 * The values are returned by one guarded Supabase RPC so a failed source query
 * cannot silently become a misleading zero.
 */
data class AdminDashboardUiState(
    val reportsCount: Long = 0,
    val noticesCount: Long = 0,
    val eventsCount: Long = 0,
    val workQueueCount: Long = 0,
    val totalPendingTasks: Long = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastRefreshedMillis: Long = 0L,
    val isAuthorized: Boolean = true,
)

@Serializable
private data class AdminDashboardSummary(
    val reports_count: Long = 0,
    val notices_count: Long = 0,
    val events_count: Long = 0,
    val work_queue_count: Long = 0,
    val total_pending_tasks: Long = 0,
)

/**
 * Loads one bounded server-authoritative projection for the administration
 * dashboard. Refresh is explicit and lifecycle-scoped; the previous 15-second
 * polling loop caused unnecessary traffic and could mask backend failures.
 */
@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val adminGuard: AdminGuard,
) : ViewModel() {

    companion object {
        private const val TAG = "AdminDashboardVM"
        private const val SUMMARY_RPC = "admin_get_moderation_dashboard_summary_v1"
    }

    private val _uiState = MutableStateFlow(AdminDashboardUiState(isLoading = true))
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        refreshCounts()
    }

    /** Manual refresh trigger for the summary view. */
    fun refreshCounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            fetchPendingCountsInternal()
        }
    }

    private suspend fun fetchPendingCountsInternal() {
        val result = adminGuard.runAdminGuarded("aggregate_pending_moderation_counts") {
            supabase.postgrest.rpc(SUMMARY_RPC)
                .decodeList<AdminDashboardSummary>()
                .single()
        }

        result.fold(
            onSuccess = { summary ->
                _uiState.update {
                    it.copy(
                        reportsCount = summary.reports_count,
                        noticesCount = summary.notices_count,
                        eventsCount = summary.events_count,
                        workQueueCount = summary.work_queue_count,
                        totalPendingTasks = summary.total_pending_tasks,
                        isLoading = false,
                        errorMessage = null,
                        lastRefreshedMillis = System.currentTimeMillis(),
                        isAuthorized = true,
                    )
                }
                Log.d(TAG, "Loaded production dashboard summary: total=${summary.total_pending_tasks}")
            },
            onFailure = { error ->
                val authorizationFailure = error is AdminSecurityException ||
                    error.message?.contains("authorised staff", ignoreCase = true) == true
                Log.w(TAG, "Failed to load production dashboard summary", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthorized = !authorizationFailure,
                        errorMessage = if (authorizationFailure) {
                            error.message ?: "Authorised staff access is required."
                        } else {
                            "The live administration summary is unavailable. No counts were substituted."
                        },
                    )
                }
            },
        )
    }
}
