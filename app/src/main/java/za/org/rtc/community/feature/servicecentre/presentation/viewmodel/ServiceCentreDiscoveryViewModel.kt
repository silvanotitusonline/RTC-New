package za.org.rtc.community.feature.servicecentre.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocationRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreCategory
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreDiscoveryRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProvider

data class ServiceCentreDiscoveryUiState(
    val categories: List<ServiceCentreCategory> = emptyList(),
    val providers: List<ServiceCentreProvider> = emptyList(),
    val selectedCategoryId: String? = null,
    val locality: String = "",
    val origin: MarketplaceCoordinates? = null,
    val loading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ServiceCentreDiscoveryViewModel @Inject constructor(
    private val repository: ServiceCentreDiscoveryRepository,
    private val locationRepository: MarketplaceLocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ServiceCentreDiscoveryUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val categories = async { repository.categories() }
            val providers = async { radar() }
            val categoryResult = categories.await()
            val providerResult = providers.await()
            val failure = categoryResult.exceptionOrNull() ?: providerResult.exceptionOrNull()
            _state.value = _state.value.copy(
                categories = categoryResult.getOrElse { emptyList() },
                providers = providerResult.getOrElse { emptyList() },
                loading = false,
                message = failure?.let { SafeUiError.serviceCentre(it, "Service Centre could not be loaded.") },
            )
        }
    }

    fun setLocality(value: String) {
        _state.value = _state.value.copy(locality = value.take(120))
    }

    fun applyLocality() {
        _state.value = _state.value.copy(origin = null)
        refreshProviders()
    }

    fun selectCategory(categoryId: String?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
        refreshProviders()
    }

    fun useMyLocation() {
        val coordinates = locationRepository.lastKnownCoordinates()
        if (coordinates == null) {
            _state.value = _state.value.copy(message = "Location is unavailable. You can search by town or suburb instead.")
            return
        }
        _state.value = _state.value.copy(origin = coordinates, message = null)
        refreshProviders()
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun refreshProviders() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            radar().onSuccess { providers ->
                _state.value = _state.value.copy(providers = providers, loading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = SafeUiError.serviceCentre(error, "Providers could not be loaded."),
                )
            }
        }
    }

    private suspend fun radar(): Result<List<ServiceCentreProvider>> {
        val current = _state.value
        return repository.localRadar(
            categoryId = current.selectedCategoryId,
            locality = current.locality.trim().takeIf(String::isNotBlank),
            origin = current.origin,
        )
    }
}
