package za.org.rtc.community.feature.administration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.core.notifications.NotificationPayloads

/** Audience selector for an administrator broadcast. */
enum class NotificationTarget(val wireValue: String, val label: String) {
    ALL_USERS("ALL", "All users"),
    VERIFIED_ONLY("VERIFIED", "Verified only"),
    STAFF_ONLY("STAFF", "Staff only"),
}

/**
 * Editable state of the admin notification composer.
 *
 * [lastResult] carries the outcome of the most recent dispatch so the screen can
 * confirm success or surface the failure without a separate event channel.
 */
data class NotificationComposerState(
    val title: String = "",
    val body: String = "",
    val mediaUrl: String = "",
    val target: NotificationTarget = NotificationTarget.ALL_USERS,
    val isSending: Boolean = false,
    val lastResult: String? = null,
    val lastResultIsError: Boolean = false,
) {
    val canSend: Boolean
        get() = title.isNotBlank() && body.isNotBlank() && !isSending
}

/**
 * Drives the administrator push composer.
 *
 * Dispatch delegates to the "send-notification" edge function, which owns
 * audience resolution and FCM fan-out; this class only validates input and
 * forwards a payload the client and server both understand.
 */
@HiltViewModel
class NotificationComposerViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationComposerState())
    val state = _state.asStateFlow()

    fun updateTitle(value: String) {
        _state.update { it.copy(title = value.take(MAX_TITLE), lastResult = null) }
    }

    fun updateBody(value: String) {
        _state.update { it.copy(body = value.take(MAX_BODY), lastResult = null) }
    }

    fun updateMediaUrl(value: String) {
        _state.update { it.copy(mediaUrl = value.trim(), lastResult = null) }
    }

    fun updateTarget(target: NotificationTarget) {
        _state.update { it.copy(target = target, lastResult = null) }
    }

    fun clearResult() {
        _state.update { it.copy(lastResult = null, lastResultIsError = false) }
    }

    /**
     * Sends the composed notification to the selected audience.
     *
     * The media URL is optional. When supplied it must be absolute; relative or
     * blank values are dropped so the client falls back to a text notification
     * rather than attempting a fetch that cannot succeed.
     */
    fun send() {
        val current = _state.value
        if (!current.canSend) return

        val mediaUrl = current.mediaUrl.takeIf { it.startsWith("http", ignoreCase = true) }

        _state.update { it.copy(isSending = true, lastResult = null, lastResultIsError = false) }

        viewModelScope.launch {
            runCatching {
                val response = supabase.functions.invoke(
                    "send-notification",
                    buildJsonObject {
                        put("title", current.title.trim())
                        put("body", current.body.trim())
                        put("target", current.target.wireValue)
                        mediaUrl?.let { put(NotificationPayloads.KEY_MEDIA_URL, it) }
                        put(NotificationPayloads.KEY_CATEGORY, NotificationPayloads.CATEGORY_COMMUNITY_UPDATE)
                        put(NotificationPayloads.KEY_NOTIFICATION_TYPE, NotificationPayloads.TYPE_COMMUNITY_ALERT)
                    },
                )
                check(response.status.value in 200..299) {
                    "Dispatch rejected with status " + response.status.value
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        isSending = false,
                        lastResult = "Notification dispatched to " + current.target.label.lowercase() + ".",
                        lastResultIsError = false,
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Notification dispatch failed", error)
                _state.update {
                    it.copy(
                        isSending = false,
                        lastResult = "Dispatch failed: " + (error.message ?: "unknown error"),
                        lastResultIsError = true,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "NotificationComposer"
        const val MAX_TITLE = 120
        const val MAX_BODY = 1000
    }
}
