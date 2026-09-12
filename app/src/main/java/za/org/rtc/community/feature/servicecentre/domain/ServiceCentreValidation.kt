package za.org.rtc.community.feature.servicecentre.domain

import java.math.BigDecimal
import java.time.Instant

object ServiceCentreValidation {
    private val maxMoney = BigDecimal("999999.99")
    private val minBookingOffer = BigDecimal("1.00")

    fun provider(
        categoryId: String,
        locality: String,
        startingPrice: BigDecimal,
        radiusKm: Int,
    ): String? = when {
        categoryId.isBlank() -> "Choose a service category."
        locality.trim().length !in 2..120 -> "Enter a service area."
        radiusKm !in 1..50 -> "Travel distance must be between 1 and 50 km."
        startingPrice < BigDecimal.ZERO || startingPrice > maxMoney -> "Starting price is outside the supported range."
        else -> null
    }

    fun booking(
        currentUserId: String,
        providerUserId: String,
        requestedStartAt: Instant,
        serviceLocation: String,
        offerAmount: BigDecimal,
        now: Instant = Instant.now(),
    ): String? = when {
        providerUserId.isBlank() -> "Choose a provider."
        currentUserId.isNotBlank() && currentUserId == providerUserId -> "Choose another provider."
        !requestedStartAt.isAfter(now) -> "Choose a future booking time."
        serviceLocation.trim().length !in 3..240 -> "Enter a valid service location."
        offerAmount < minBookingOffer || offerAmount > maxMoney -> "Offer amount is outside the supported range."
        else -> null
    }

    fun message(body: String): String? = when {
        body.trim().isEmpty() -> "Enter a message."
        body.trim().length > 1000 -> "Message must be 1000 characters or fewer."
        else -> null
    }

    fun moneyOrNull(value: String): BigDecimal? = value
        .trim()
        .replace(",", "")
        .takeIf(String::isNotBlank)
        ?.toBigDecimalOrNull()
}
