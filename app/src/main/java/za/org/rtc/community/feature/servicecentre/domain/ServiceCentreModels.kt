package za.org.rtc.community.feature.servicecentre.domain

import java.math.BigDecimal
import java.time.Instant

data class ServiceCentreCategory(
    val id: String,
    val name: String,
    val slug: String,
    val iconKey: String? = null,
)

data class ServiceCentreProvider(
    val providerUserId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val categoryId: String,
    val categoryName: String,
    val locality: String,
    val distanceMetres: Int? = null,
    val startingPrice: BigDecimal,
    val currencyCode: String = "ZAR",
    val marketplaceBusinessId: String? = null,
    val ratingAverage: Double? = null,
    val reviewCount: Int? = null,
    val verified: Boolean = false,
    val active: Boolean = true,
)

data class ServiceCentreProviderProfile(
    val userId: String,
    val primaryCategoryId: String,
    val categoryName: String,
    val marketplaceBusinessId: String? = null,
    val locality: String,
    val serviceRadiusKm: Int = 25,
    val startingPrice: BigDecimal,
    val currencyCode: String = "ZAR",
    val active: Boolean = true,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

data class ServiceCentreProviderDraft(
    val primaryCategoryId: String,
    val locality: String,
    val latitude: Double,
    val longitude: Double,
    val startingPrice: BigDecimal,
    val serviceRadiusKm: Int = 25,
    val marketplaceBusinessId: String? = null,
)

enum class ServiceCentreActorRole { CUSTOMER, PROVIDER }

data class ServiceCentreBooking(
    val id: String,
    val customerUserId: String,
    val providerUserId: String,
    val actorRole: ServiceCentreActorRole,
    val counterpartyUserId: String,
    val counterpartyDisplayName: String,
    val counterpartyAvatarUrl: String? = null,
    val categoryId: String,
    val categoryName: String,
    val marketplaceBusinessId: String? = null,
    val marketplaceOfferingId: String? = null,
    val requestedStartAt: Instant,
    val serviceLocationText: String,
    val offerAmount: BigDecimal,
    val currencyCode: String = "ZAR",
    val status: ServiceCentreBookingStatus,
    val acceptedAt: Instant? = null,
    val declinedAt: Instant? = null,
    val confirmedAt: Instant? = null,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ServiceCentreBookingDraft(
    val providerUserId: String,
    val requestedStartAt: Instant,
    val serviceLocationText: String,
    val offerAmount: BigDecimal,
    val idempotencyKey: String,
    val marketplaceBusinessId: String? = null,
    val marketplaceOfferingId: String? = null,
)

data class ServiceCentreMessage(
    val id: String,
    val bookingId: String,
    val senderUserId: String,
    val senderDisplayName: String? = null,
    val senderAvatarUrl: String? = null,
    val body: String,
    val mine: Boolean = false,
    val createdAt: Instant,
)

enum class ServiceCentreNotificationEvent {
    SERVICE_BOOKING_NEW,
    SERVICE_BOOKING_ACCEPTED,
    SERVICE_BOOKING_DECLINED,
    SERVICE_BOOKING_MESSAGE,
    SERVICE_BOOKING_COMPLETED,
    SERVICE_BOOKING_CANCELLED,
}
