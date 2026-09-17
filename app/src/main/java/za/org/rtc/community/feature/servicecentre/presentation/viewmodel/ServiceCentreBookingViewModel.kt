package za.org.rtc.community.feature.servicecentre.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingDraft
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreDiscoveryRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreMessage
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProvider
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreValidation

data class ServiceCentreBookingUiState(
    val provider: ServiceCentreProvider? = null,
    val bookings: List<ServiceCentreBooking> = emptyList(),
    val detail: ServiceCentreBooking? = null,
    val messages: List<ServiceCentreMessage> = emptyList(),
    val createdBookingId: String? = null,
    val loading: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ServiceCentreBookingViewModel @Inject constructor(
    private val bookingRepository: ServiceCentreBookingRepository,
    private val discoveryRepository: ServiceCentreDiscoveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ServiceCentreBookingUiState())
    val state = _state.asStateFlow()
    private var createIdempotencyKey = UUID.randomUUID().toString()
    private val transitionKeys = mutableMapOf<String, String>()
    private var messageKey = UUID.randomUUID().toString()

    fun loadProvider(reference: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            discoveryRepository.provider(reference).onSuccess { provider ->
                _state.value = _state.value.copy(provider = provider, loading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = SafeUiError.serviceCentre(error, "Provider could not be loaded."),
                )
            }
        }
    }

    fun loadBookings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            bookingRepository.myBookings().onSuccess { bookings ->
                _state.value = _state.value.copy(bookings = bookings, loading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = SafeUiError.serviceCentre(error, "Bookings could not be loaded."),
                )
            }
        }
    }

    fun loadDetail(bookingId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            bookingRepository.bookingDetail(bookingId).onSuccess { booking ->
                _state.value = _state.value.copy(detail = booking, loading = false, message = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    detail = null,
                    loading = false,
                    message = SafeUiError.serviceCentre(error, "Booking details could not be loaded."),
                )
            }
        }
    }

    fun createBooking(
        whenText: String,
        location: String,
        offerText: String,
        marketplaceBusinessId: String? = null,
        marketplaceOfferingId: String? = null,
    ) {
        val provider = _state.value.provider ?: run {
            _state.value = _state.value.copy(message = "Provider details are still loading.")
            return
        }
        val requestedAt = parseWhen(whenText)
        val offer = ServiceCentreValidation.moneyOrNull(offerText)
        val error = when {
            requestedAt == null -> "Enter the booking time as YYYY-MM-DD HH:mm."
            offer == null -> "Enter a valid offer amount."
            else -> ServiceCentreValidation.booking(
                currentUserId = bookingRepository.currentUserId().orEmpty(),
                providerUserId = provider.providerUserId,
                requestedStartAt = requestedAt,
                serviceLocation = location,
                offerAmount = offer,
            )
        }
        if (error != null || requestedAt == null || offer == null) {
            _state.value = _state.value.copy(message = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            bookingRepository.createBooking(
                ServiceCentreBookingDraft(
                    providerUserId = provider.providerUserId,
                    requestedStartAt = requestedAt,
                    serviceLocationText = location.trim(),
                    offerAmount = offer,
                    idempotencyKey = createIdempotencyKey,
                    marketplaceBusinessId = marketplaceBusinessId ?: provider.marketplaceBusinessId,
                    marketplaceOfferingId = marketplaceOfferingId,
                )
            ).onSuccess { booking ->
                createIdempotencyKey = UUID.randomUUID().toString()
                _state.value = _state.value.copy(working = false, createdBookingId = booking.id, detail = booking)
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    working = false,
                    message = SafeUiError.serviceCentre(failure, "Booking request could not be sent."),
                )
            }
        }
    }

    fun accept(bookingId: String) = transition(bookingId, "accept") { key -> bookingRepository.acceptBooking(bookingId, key) }
    fun decline(bookingId: String) = transition(bookingId, "decline") { key -> bookingRepository.declineBooking(bookingId, key) }
    fun cancel(bookingId: String) = transition(bookingId, "cancel") { key -> bookingRepository.cancelBooking(bookingId, key) }
    fun complete(bookingId: String) = transition(bookingId, "complete") { key -> bookingRepository.completeBooking(bookingId, key) }

    fun refreshMessages(bookingId: String) {
        viewModelScope.launch {
            bookingRepository.messages(bookingId).onSuccess { messages ->
                _state.value = _state.value.copy(messages = messages, message = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    messages = emptyList(),
                    message = SafeUiError.serviceCentre(error, "Messages could not be loaded."),
                )
            }
        }
    }

    fun sendMessage(bookingId: String, body: String) {
        ServiceCentreValidation.message(body)?.let { error ->
            _state.value = _state.value.copy(message = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            val now = Instant.now()
            val currentUserId = bookingRepository.currentUserId()
                ?: run {
                    _state.value = _state.value.copy(working = false, message = "Sign in to send a message.")
                    return@launch
                }
            val newMessage = ServiceCentreMessage(
                id = "msg_${System.currentTimeMillis()}",
                bookingId = bookingId,
                senderUserId = currentUserId,
                senderDisplayName = "Me",
                body = body.trim(),
                mine = true,
                createdAt = now,
            )
            bookingRepository.sendMessage(bookingId, body, messageKey).onSuccess {
                messageKey = UUID.randomUUID().toString()
                _state.value = _state.value.copy(working = false)
                refreshMessages(bookingId)
            }.onFailure {
                val updatedMessages = _state.value.messages + newMessage
                _state.value = _state.value.copy(working = false, messages = updatedMessages)
            }
        }
    }

    fun consumeCreatedBooking() {
        _state.value = _state.value.copy(createdBookingId = null)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun transition(
        bookingId: String,
        action: String,
        operation: suspend (String) -> Result<ServiceCentreBooking>,
    ) {
        val mapKey = "$bookingId:$action"
        val key = transitionKeys.getOrPut(mapKey) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            operation(key).onSuccess { booking ->
                transitionKeys.remove(mapKey)
                val bookings = _state.value.bookings.map { if (it.id == booking.id) booking else it }
                _state.value = _state.value.copy(working = false, detail = booking, bookings = bookings)
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    working = false,
                    message = SafeUiError.serviceCentre(failure, "Booking could not be updated."),
                )
            }
        }
    }

    companion object {
        private val bookingFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun parseWhen(value: String): Instant? = runCatching {
            LocalDateTime.parse(value.trim(), bookingFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }.getOrNull()

        fun defaultWhenText(now: LocalDateTime = LocalDateTime.now()): String =
            now.plusDays(1).withSecond(0).withNano(0).format(bookingFormatter)

    }
}
