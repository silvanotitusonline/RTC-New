package za.org.rtc.community.feature.servicecentre.domain

import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates

interface ServiceCentreDiscoveryRepository {
    suspend fun categories(): Result<List<ServiceCentreCategory>>
    suspend fun localRadar(
        categoryId: String? = null,
        locality: String? = null,
        origin: MarketplaceCoordinates? = null,
        radiusMetres: Int = 25_000,
        offset: Int = 0,
        limit: Int = 20,
    ): Result<List<ServiceCentreProvider>>
    suspend fun provider(reference: String): Result<ServiceCentreProvider>
}

interface ServiceCentreProviderRepository {
    suspend fun myProviderProfile(): Result<ServiceCentreProviderProfile?>
    suspend fun upsertProviderProfile(draft: ServiceCentreProviderDraft): Result<ServiceCentreProviderProfile>
    suspend fun setProviderActive(active: Boolean): Result<ServiceCentreProviderProfile>
}

interface ServiceCentreBookingRepository {
    fun currentUserId(): String?
    suspend fun createBooking(draft: ServiceCentreBookingDraft): Result<ServiceCentreBooking>
    suspend fun myBookings(): Result<List<ServiceCentreBooking>>
    suspend fun bookingDetail(bookingId: String): Result<ServiceCentreBooking>
    suspend fun acceptBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking>
    suspend fun declineBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking>
    suspend fun cancelBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking>
    suspend fun completeBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking>
    suspend fun messages(bookingId: String, limit: Int = 100): Result<List<ServiceCentreMessage>>
    suspend fun sendMessage(bookingId: String, body: String, idempotencyKey: String): Result<ServiceCentreMessage>
    suspend fun notifyBookingEvent(
        bookingId: String,
        event: ServiceCentreNotificationEvent,
        messageId: String? = null,
    ): Result<Unit>
}
