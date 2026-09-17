package za.org.rtc.community.feature.administration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import za.org.rtc.community.feature.administration.security.AdminGuard
import za.org.rtc.community.feature.administration.security.AdminSecurityException


enum class AdminRealtimeStatus {
    CONNECTING,
    LIVE,
    DISCONNECTED,
}

data class AdminDashboardUiState(
    val reportsCount: Long = 0,
    val noticesCount: Long = 0,
    val eventsCount: Long = 0,
    val workQueueCount: Long = 0,
    val totalPendingTasks: Long = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastRefreshedMillis: Long = 0L,
    val lastRealtimeEventMillis: Long = 0L,
    val realtimeStatus: AdminRealtimeStatus = AdminRealtimeStatus.CONNECTING,
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
 * Loads one bounded server-authoritative projection and refreshes it when the
 * staff-only, payload-free invalidation stream reports a source-table change.
 * Realtime events never become counts directly; the guarded RPC remains the
 * only count authority.
 */
@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val adminGuard: AdminGuard,
) : ViewModel() {

    companion object {
        private const val TAG = "AdminDashboardVM"
        private const val SUMMARY_RPC = "admin_get_moderation_dashboard_summary_v1"
        private const val INVALIDATION_TABLE = "admin_dashboard_invalidations"
        private const val INVALIDATION_DEBOUNCE_MILLIS = 750L
    }

    private val _uiState = MutableStateFlow(AdminDashboardUiState(isLoading = true))
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()
    private var realtimeJob: Job? = null

    init {
        refreshCounts()
        startRealtimeSync()
    }

    /** Manual refresh trigger for the summary view. */
    fun refreshCounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            fetchPendingCountsInternal()
        }
    }

    private fun startRealtimeSync() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            observeDashboardInvalidations()
                .onStart {
                    _uiState.update { it.copy(realtimeStatus = AdminRealtimeStatus.CONNECTING) }
                }
                .debounce(INVALIDATION_DEBOUNCE_MILLIS)
                .onEach {
                    _uiState.update {
                        it.copy(
                            realtimeStatus = AdminRealtimeStatus.LIVE,
                            lastRealtimeEventMillis = System.currentTimeMillis(),
                            errorMessage = null,
                        )
                    }
                }
                .catch { error ->
                    Log.w(TAG, "Administration realtime stream disconnected", error)
                    _uiState.update {
                        it.copy(
                            realtimeStatus = AdminRealtimeStatus.DISCONNECTED,
                            errorMessage = "Live updates are unavailable. Use Refresh to check for changes.",
                        )
                    }
                }
                .collectLatest {
                    fetchPendingCountsInternal()
                }
        }
    }

    private fun observeDashboardInvalidations(): Flow<Unit> = callbackFlow {
        val channel = supabase.realtime.channel("admin-dashboard-invalidations")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = INVALIDATION_TABLE
        }
        val collector = CoroutineScope(Dispatchers.IO).launch {
            changeFlow.collect { trySend(Unit).isSuccess }
        }
        runCatching { channel.subscribe() }
            .onFailure { close(it) }
        awaitClose {
            collector.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
            }
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
                        errorMessage = if (it.realtimeStatus == AdminRealtimeStatus.DISCONNECTED) it.errorMessage else null,
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

    override fun onCleared() {
        realtimeJob?.cancel()
        realtimeJob = null
        super.onCleared()
    }
}

internal fun AdminDashboardUiState.lastUpdatedLabel(): String = when {
    lastRefreshedMillis <= 0L -> "Not updated yet"
    else -> "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(lastRefreshedMillis))}"
}
