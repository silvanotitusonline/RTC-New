package za.org.rtc.community.ui.auth

import android.util.Log

/**
 * Specifically monitors the Google Credential Manager/Google Sign-In flow
 * and logs state transitions (STARTED, AUTHENTICATING, SUCCESS, FAILURE)
 * to the Android Logcat for debugging purposes.
 */
object AuthenticationLogger {
    private const val TAG = "AuthenticationLogger"

    enum class TransitionState {
        STARTED,
        AUTHENTICATING,
        SUCCESS,
        FAILURE
    }

    private var currentState: TransitionState? = null

    /**
     * Logs the current state transition of the Google authentication flow.
     */
    fun logState(state: TransitionState, details: String? = null) {
        currentState = state
        val message = "[Google Credential Flow] State Transition: $state" + 
                if (details != null) " | Details: $details" else ""
        
        when (state) {
            TransitionState.STARTED -> Log.i(TAG, "🟢 $message")
            TransitionState.AUTHENTICATING -> Log.i(TAG, "🟡 $message")
            TransitionState.SUCCESS -> Log.i(TAG, "✅ $message")
            TransitionState.FAILURE -> Log.e(TAG, "❌ $message")
        }
    }
}
