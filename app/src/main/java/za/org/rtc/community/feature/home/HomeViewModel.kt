package za.org.rtc.community.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository

data class HomePublicReportState(
    val dashboard: PublicReportDashboard? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
open class HomeViewModel @Inject constructor(
    private val repository: PublicReportRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {
    private val _state = MutableStateFlow(HomePublicReportState(loading = true))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.dashboardUpdates.collect { dashboard ->
                if (dashboard != null) {
                    _state.update {
                        it.copy(
                            dashboard = dashboard,
                            loading = false,
                            refreshing = false,
                            message = null,
                        )
                    }
                }
            }
        }
        refresh()
    }

    fun load() {
        refresh()
    }

    fun refresh() {
        val hasSnapshot = _state.value.dashboard != null
        _state.update {
            it.copy(
                loading = !hasSnapshot,
                refreshing = hasSnapshot,
                message = null,
            )
        }
        viewModelScope.launch {
            repository.dashboard()
                .onSuccess { dashboard ->
                    _state.update {
                        it.copy(
                            dashboard = dashboard,
                            loading = false,
                            refreshing = false,
                            message = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            message = SafeUiError.generic(
                                error,
                                "The verified Public Reports snapshot is unavailable.",
                            ),
                        )
                    }
                }
        }
    }
}
