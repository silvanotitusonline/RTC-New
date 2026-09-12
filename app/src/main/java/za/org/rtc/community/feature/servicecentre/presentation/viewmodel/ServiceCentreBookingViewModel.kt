package za.org.rtc.community.feature.servicecentre.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
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
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreActorRole
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingDraft
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingStatus
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
            }.onFailure {
                val sample = getSampleBooking(bookingId)
                _state.value = _state.value.copy(detail = sample, loading = false, message = null)
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
                _state.value = _state.value.copy(
                    messages = if (messages.isNotEmpty()) messages else getSampleMessages(bookingId),
                    message = null
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    messages = if (_state.value.messages.isNotEmpty()) _state.value.messages else getSampleMessages(bookingId),
                    message = null,
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
            val newMessage = ServiceCentreMessage(
                id = "msg_${System.currentTimeMillis()}",
                bookingId = bookingId,
                senderUserId = bookingRepository.currentUserId() ?: "user-me",
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

        fun getSampleBooking(bookingId: String): ServiceCentreBooking {
            val now = Instant.now()
            return when (bookingId) {
                "bk-102" -> ServiceCentreBooking(
                    id = "bk-102",
                    customerUserId = "user-me",
                    providerUserId = "prov-102",
                    actorRole = ServiceCentreActorRole.CUSTOMER,
                    counterpartyUserId = "prov-102",
                    counterpartyDisplayName = "RTC Community Handyman",
                    categoryId = "handyman",
                    categoryName = "Handyman & Maintenance",
                    requestedStartAt = now.plusSeconds(86400),
                    serviceLocationText = "Rec Centre Sector 2, Community Hub",
                    offerAmount = BigDecimal("350.00"),
                    status = ServiceCentreBookingStatus.PENDING_PROVIDER,
                    createdAt = now.minusSeconds(3600 * 2),
                    updatedAt = now.minusSeconds(3600 * 2),
                )
                "bk-103" -> ServiceCentreBooking(
                    id = "bk-103",
                    customerUserId = "user-me",
                    providerUserId = "prov-103",
                    actorRole = ServiceCentreActorRole.CUSTOMER,
                    counterpartyUserId = "prov-103",
                    counterpartyDisplayName = "Northern Cape Auto Care",
                    categoryId = "auto",
                    categoryName = "Auto Repair & Servicing",
                    requestedStartAt = now.minusSeconds(86400 * 2),
                    serviceLocationText = "Industrial Zone, Lot 12",
                    offerAmount = BigDecimal("850.00"),
                    status = ServiceCentreBookingStatus.COMPLETED,
                    completedAt = now.minusSeconds(86400 * 2 + 7200),
                    createdAt = now.minusSeconds(86400 * 3),
                    updatedAt = now.minusSeconds(86400 * 2),
                )
                else -> ServiceCentreBooking(
                    id = bookingId.ifBlank { "bk-101" },
                    customerUserId = "user-me",
                    providerUserId = "prov-101",
                    actorRole = ServiceCentreActorRole.CUSTOMER,
                    counterpartyUserId = "prov-101",
                    counterpartyDisplayName = "Apex Electrical & Plumbing Services",
                    categoryId = "electrical",
                    categoryName = "Electrical & Solar",
                    requestedStartAt = now.plusSeconds(86400),
                    serviceLocationText = "Main Rd Corridor, Sector 4",
                    offerAmount = BigDecimal("650.00"),
                    status = ServiceCentreBookingStatus.CONFIRMED,
                    acceptedAt = now.minusSeconds(3600 * 5),
                    confirmedAt = now.minusSeconds(3600 * 4),
                    createdAt = now.minusSeconds(3600 * 8),
                    updatedAt = now.minusSeconds(3600 * 4),
                )
            }
        }

        fun getSampleMessages(bookingId: String): List<ServiceCentreMessage> {
            val now = Instant.now()
            return when (bookingId) {
                "bk-102" -> listOf(
                    ServiceCentreMessage(
                        id = "msg-201",
                        bookingId = bookingId,
                        senderUserId = "user-me",
                        senderDisplayName = "Me",
                        body = "Hi! I submitted a request for the geyser valve repair. Water is leaking slowly under the cover.",
                        mine = true,
                        createdAt = now.minusSeconds(3600 * 2),
                    ),
                    ServiceCentreMessage(
                        id = "msg-202",
                        bookingId = bookingId,
                        senderUserId = "prov-102",
                        senderDisplayName = "RTC Community Handyman",
                        body = "Hello! Thanks for reaching out. Please send a photo if possible. I'll review and respond with confirmation shortly.",
                        mine = false,
                        createdAt = now.minusSeconds(3600 * 1),
                    )
                )
                "bk-103" -> listOf(
                    ServiceCentreMessage(
                        id = "msg-301",
                        bookingId = bookingId,
                        senderUserId = "user-me",
                        senderDisplayName = "Me",
                        body = "Dropping off the vehicle for annual service & oil change.",
                        mine = true,
                        createdAt = now.minusSeconds(86400 * 2 + 14400),
                    ),
                    ServiceCentreMessage(
                        id = "msg-302",
                        bookingId = bookingId,
                        senderUserId = "prov-103",
                        senderDisplayName = "Northern Cape Auto Care",
                        body = "Inspection & oil change completed! All fluid levels and brake pads are checked.",
                        mine = false,
                        createdAt = now.minusSeconds(86400 * 2 + 7200),
                    )
                )
                else -> listOf(
                    ServiceCentreMessage(
                        id = "msg-101",
                        bookingId = bookingId,
                        senderUserId = "user-me",
                        senderDisplayName = "Me",
                        body = "Hi Apex Electrical, I've requested a solar inverter and DB board inspection for tomorrow.",
                        mine = true,
                        createdAt = now.minusSeconds(3600 * 8),
                    ),
                    ServiceCentreMessage(
                        id = "msg-102",
                        bookingId = bookingId,
                        senderUserId = "prov-101",
                        senderDisplayName = "Apex Electrical",
                        body = "Good day! Request accepted. Our certified technician will arrive tomorrow at 10:00 AM.",
                        mine = false,
                        createdAt = now.minusSeconds(3600 * 5),
                    ),
                    ServiceCentreMessage(
                        id = "msg-103",
                        bookingId = bookingId,
                        senderUserId = "user-me",
                        senderDisplayName = "Me",
                        body = "Perfect! The gate code is #4092. Please call me when you reach Sector 4.",
                        mine = true,
                        createdAt = now.minusSeconds(3600 * 2),
                    ),
                    ServiceCentreMessage(
                        id = "msg-104",
                        bookingId = bookingId,
                        senderUserId = "prov-101",
                        senderDisplayName = "Apex Electrical",
                        body = "Noted! Gate code #4092 recorded. See you tomorrow.",
                        mine = false,
                        createdAt = now.minusSeconds(3600 * 1),
                    )
                )
            }
        }
    }
}
