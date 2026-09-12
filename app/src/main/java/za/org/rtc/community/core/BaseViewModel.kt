
package za.org.rtc.community.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import android.util.Log

open class BaseViewModel : ViewModel() {
    // Global exception handler to prevent hard crashes
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("BaseViewModel", "Caught unhandled exception: ${throwable.message}", throwable)
        // Here we would typically update a UI state to show a Snackbar error
    }

    fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            try {
                block()
            } catch (e: Exception) {
                Log.e("BaseViewModel", "Error executing safe block: ${e.message}")
            }
        }
    }
}
