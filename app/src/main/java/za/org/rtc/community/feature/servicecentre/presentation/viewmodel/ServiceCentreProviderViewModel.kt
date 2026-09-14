package za.org.rtc.community.feature.servicecentre.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocationRepository
import za.org.rtc.community.feature.servicecentre.data.remote.ServiceCentreLocalityResolver
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreCategory
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreDiscoveryRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderDraft
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderProfile
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreValidation

data class ServiceCentreProviderUiState(
    val categories: List<ServiceCentreCategory> = emptyList(),
    val profile: ServiceCentreProviderProfile? = null,
    val coordinates: MarketplaceCoordinates? = null,
    val loading: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ServiceCentreProviderViewModel @Inject constructor(
    private val providerRepository: ServiceCentreProviderRepository,
    private val discoveryRepository: ServiceCentreDiscoveryRepository,
    private val locationRepository: MarketplaceLocationRepository,
    private val localityResolver: ServiceCentreLocalityResolver,
) : ViewModel() {
    private val _state = MutableStateFlow(ServiceCentreProviderUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null, saved = false)
            val categories = async { discoveryRepository.categories() }
            val profile = async { providerRepository.myProviderProfile() }
            val categoryResult = categories.await()
            val profileResult = profile.await()
            val failure = categoryResult.exceptionOrNull() ?: profileResult.exceptionOrNull()
            _state.value = _state.value.copy(
                categories = categoryResult.getOrElse { emptyList() },
                profile = profileResult.getOrNull(),
                loading = false,
                message = failure?.let {
                    SafeUiError.serviceCentre(it, "Service Centre provider details could not be loaded.")
                },
            )
        }
    }

    fun useMyLocation() {
        val coordinates = locationRepository.lastKnownCoordinates()
        if (coordinates == null) {
            _state.value = _state.value.copy(message = "Location is unavailable. Enter your town or suburb instead.")
            return
        }
        _state.value = _state.value.copy(coordinates = coordinates, message = null)
    }

    suspend fun suggestedLocality(): String? = _state.value.coordinates?.let { localityResolver.locality(it) }

    fun save(
        categoryId: String,
        locality: String,
        startingPriceText: String,
        radiusKm: Int,
        marketplaceBusinessId: String? = _state.value.profile?.marketplaceBusinessId,
    ) {
        val price = ServiceCentreValidation.moneyOrNull(startingPriceText)
        val error = when {
            price == null -> "Enter a valid starting price."
            else -> ServiceCentreValidation.provider(categoryId, locality, price, radiusKm)
        }
        if (error != null || price == null) {
            _state.value = _state.value.copy(message = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null, saved = false)
            val coordinatesResult = _state.value.coordinates?.let { Result.success(it) }
                ?: localityResolver.coordinates(locality)
            val coordinates = coordinatesResult.getOrElse { failure ->
                _state.value = _state.value.copy(
                    working = false,
                    message = SafeUiError.serviceCentre(failure, "Service area could not be resolved."),
                )
                return@launch
            }
            providerRepository.upsertProviderProfile(
                ServiceCentreProviderDraft(
                    primaryCategoryId = categoryId,
                    locality = locality.trim(),
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    startingPrice = price,
                    serviceRadiusKm = radiusKm,
                    marketplaceBusinessId = marketplaceBusinessId,
                )
            ).onSuccess { profile ->
                _state.value = _state.value.copy(
                    profile = profile,
                    coordinates = coordinates,
                    working = false,
                    saved = true,
                    message = "Provider profile saved.",
                )
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    working = false,
                    message = SafeUiError.serviceCentre(failure, "Provider profile could not be saved."),
                )
            }
        }
    }

    fun setActive(active: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null, saved = false)
            providerRepository.setProviderActive(active).onSuccess { profile ->
                _state.value = _state.value.copy(
                    profile = profile,
                    working = false,
                    saved = true,
                    message = if (active) "Provider profile resumed." else "Provider profile paused.",
                )
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    working = false,
                    message = SafeUiError.serviceCentre(failure, "Provider status could not be changed."),
                )
            }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, saved = false)
    }
}
