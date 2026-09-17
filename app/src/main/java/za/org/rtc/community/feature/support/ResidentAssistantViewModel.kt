package za.org.rtc.community.feature.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ResidentAssistantReply(
    val answer: String = "",
    val suggestions: List<String> = emptyList(),
)

data class ResidentAssistantMessage(
    val text: String,
    val fromResident: Boolean,
)

data class ResidentAssistantUiState(
    val messages: List<ResidentAssistantMessage> = listOf(
        ResidentAssistantMessage("Hi. I can help you find your way around RTC, understand a feature, or recover from a problem. What are you trying to do?", false),
    ),
    val draft: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ResidentAssistantViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(ResidentAssistantUiState())
    val state = _state.asStateFlow()

    fun updateDraft(value: String) {
        _state.value = _state.value.copy(draft = value.take(2_000), error = null)
    }

    fun ask(question: String = _state.value.draft) {
        val clean = question.trim().take(2_000)
        if (clean.length < 2 || _state.value.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                messages = _state.value.messages + ResidentAssistantMessage(clean, true),
                draft = "",
                isLoading = true,
                error = null,
            )
            runCatching {
                val response = supabase.functions.invoke(
                    "rtc-resident-assistant",
                    buildJsonObject { put("message", clean) },
                )
                check(response.status.value in 200..299) { "The assistant is temporarily unavailable." }
                json.decodeFromString<ResidentAssistantReply>(response.bodyAsText())
            }.onSuccess { reply ->
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ResidentAssistantMessage(reply.answer, false),
                    isLoading = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error.message ?: "The assistant could not respond. Try again.",
                )
            }
        }
    }
}
