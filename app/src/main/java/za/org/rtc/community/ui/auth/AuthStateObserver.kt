package za.org.rtc.community.ui.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the Supabase GoTrue session and triggers a global UI event when the session expires,
 * prompting the user to re-authenticate via Google Sign-In.
 */
@Singleton
class AuthStateObserver @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 64)
    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

    init {
        startObserving()
    }

    private fun startObserving() {
        observerScope.launch {
            supabaseClient.auth.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.NotAuthenticated,
                    is SessionStatus.RefreshFailure -> {
                        _sessionExpiredEvents.emit(Unit)
                    }
                    else -> Unit
                }
            }
        }
    }
}
