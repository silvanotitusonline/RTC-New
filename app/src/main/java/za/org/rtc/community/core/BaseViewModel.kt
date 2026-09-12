package za.org.rtc.community.core

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

/**
 * Base class for every ViewModel in the app.
 *
 * Network and database failures must never take the process down. [launchSafe]
 * funnels any non-cancellation throwable into a single logging seam so a failed
 * Supabase call degrades the screen instead of crashing the app.
 */
open class BaseViewModel : ViewModel() {

    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled coroutine failure: " + (throwable.message ?: "unknown"), throwable)
    }

    /**
     * Launches [block] on [viewModelScope] with the shared exception handler.
     * Cancellation propagates normally; everything else is logged and swallowed.
     */
    protected fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Safe block failed: " + (throwable.message ?: "unknown"), throwable)
            }
        }
    }

    private companion object {
        const val TAG = "RtcViewModel"
    }
}
