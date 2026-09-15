package za.org.rtc.community.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SystemUpdateSyncEngine provides automated periodic syncing and real-time update
 * broadcasting across all user sessions and views when any user or administrator posts an update.
 */
@Singleton
class SystemUpdateSyncEngine @Inject constructor() {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    private val _lastSyncedEpochMillis = MutableStateFlow(System.currentTimeMillis())
    val lastSyncedEpochMillis: StateFlow<Long> = _lastSyncedEpochMillis.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _systemUpdateEvents = MutableSharedFlow<SystemUpdateEvent>(extraBufferCapacity = 64)
    val systemUpdateEvents: SharedFlow<SystemUpdateEvent> = _systemUpdateEvents.asSharedFlow()

    private val _syncCount = MutableStateFlow(0)
    val syncCount: StateFlow<Int> = _syncCount.asStateFlow()

    sealed class SystemUpdateEvent {
        data class PostCreated(val postId: String, val author: String) : SystemUpdateEvent()
        data class PostDeleted(val postId: String) : SystemUpdateEvent()
        data class CommentDeleted(val commentId: String) : SystemUpdateEvent()
        data class ProfileUpdated(val userId: String, val newName: String, val avatarUrl: String? = null) : SystemUpdateEvent()
        data class DailyPostPublished(val articleId: String, val title: String) : SystemUpdateEvent()
        data class DailyPostDeleted(val articleId: String) : SystemUpdateEvent()
        data class PublicReportUpdated(val reportId: String, val status: String) : SystemUpdateEvent()
        data class ReportDeleted(val reportId: String) : SystemUpdateEvent()
        data class NoticePublished(val noticeId: String, val title: String) : SystemUpdateEvent()
        object GlobalSystemRefresh : SystemUpdateEvent()
    }

    /**
     * Starts background auto-sync loop that continuously fetches updates for all active users.
     */
    fun startAutoSync(
        intervalMillis: Long = 15_000L,
        onPerformSync: suspend () -> Unit
    ) {
        if (syncJob?.isActive == true) return
        syncJob = engineScope.launch {
            while (isActive) {
                runCatching {
                    _isSyncing.value = true
                    onPerformSync()
                    _lastSyncedEpochMillis.value = System.currentTimeMillis()
                    _syncCount.value += 1
                }
                _isSyncing.value = false
                delay(intervalMillis)
            }
        }
    }

    fun stopAutoSync() {
        syncJob?.cancel()
        syncJob = null
        _isSyncing.value = false
    }

    /**
     * Trigger an instant system-wide refresh broadcast when a user or admin posts an update.
     */
    fun triggerSystemWideUpdate(
        event: SystemUpdateEvent = SystemUpdateEvent.GlobalSystemRefresh,
        onPerformSync: (suspend () -> Unit)? = null
    ) {
        engineScope.launch {
            _isSyncing.value = true
            _systemUpdateEvents.emit(event)
            onPerformSync?.invoke()
            _lastSyncedEpochMillis.value = System.currentTimeMillis()
            _syncCount.value += 1
            _isSyncing.value = false
        }
    }
}
