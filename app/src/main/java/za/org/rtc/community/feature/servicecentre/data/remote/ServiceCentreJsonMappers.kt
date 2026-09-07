package za.org.rtc.community.feature.servicecentre.data.remote

import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreActorRole
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingStatus
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreCategory
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreMessage
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProvider
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderProfile

internal fun JsonObject.toServiceCentreCategory(): ServiceCentreCategory = ServiceCentreCategory(
    id = string("id"),
    name = string("name"),
    slug = string("slug"),
    iconKey = stringOrNull("iconKey"),
)

internal fun JsonObject.toServiceCentreProvider(): ServiceCentreProvider = ServiceCentreProvider(
    providerUserId = string("providerUserId", "provider_user_id"),
    displayName = string("displayName", "display_name"),
    avatarUrl = stringOrNull("avatarUrl", "avatar_url"),
    categoryId = string("categoryId", "category_id"),
    categoryName = string("categoryName", "category_name"),
    locality = string("locality"),
    distanceMetres = intOrNull("distanceMetres", "distance_metres"),
    startingPrice = decimal("startingPrice", "starting_price"),
    currencyCode = stringOrNull("currencyCode", "currency_code") ?: "ZAR",
    marketplaceBusinessId = stringOrNull("marketplaceBusinessId", "marketplace_business_id"),
    ratingAverage = doubleOrNull("ratingAverage", "rating_average"),
    reviewCount = intOrNull("reviewCount", "review_count"),
    verified = boolean("verified"),
    active = booleanOrDefault(true, "isActive", "is_active"),
)

internal fun JsonObject.toServiceCentreProviderProfile(): ServiceCentreProviderProfile = ServiceCentreProviderProfile(
    userId = string("userId", "user_id"),
    primaryCategoryId = string("primaryCategoryId", "primary_category_id"),
    categoryName = string("categoryName", "category_name"),
    marketplaceBusinessId = stringOrNull("marketplaceBusinessId", "marketplace_business_id"),
    locality = string("locality"),
    serviceRadiusKm = intOrNull("serviceRadiusKm", "service_radius_km") ?: 25,
    startingPrice = decimal("startingPrice", "starting_price"),
    currencyCode = stringOrNull("currencyCode", "currency_code") ?: "ZAR",
    active = booleanOrDefault(true, "isActive", "is_active"),
    createdAt = instantOrNull("createdAt", "created_at"),
    updatedAt = instantOrNull("updatedAt", "updated_at"),
)

internal fun JsonObject.toServiceCentreBooking(): ServiceCentreBooking = ServiceCentreBooking(
    id = string("id"),
    customerUserId = string("customerUserId", "customer_user_id"),
    providerUserId = string("providerUserId", "provider_user_id"),
    actorRole = ServiceCentreActorRole.valueOf(string("actorRole", "actor_role")),
    counterpartyUserId = string("counterpartyUserId", "counterparty_user_id"),
    counterpartyDisplayName = string("counterpartyDisplayName", "counterparty_display_name"),
    counterpartyAvatarUrl = stringOrNull("counterpartyAvatarUrl", "counterparty_avatar_url"),
    categoryId = string("categoryId", "category_id"),
    categoryName = string("categoryName", "category_name"),
    marketplaceBusinessId = stringOrNull("marketplaceBusinessId", "marketplace_business_id"),
    marketplaceOfferingId = stringOrNull("marketplaceOfferingId", "marketplace_offering_id"),
    requestedStartAt = instant("requestedStartAt", "requested_start_at"),
    serviceLocationText = string("serviceLocationText", "service_location_text"),
    offerAmount = decimal("offerAmount", "offer_amount"),
    currencyCode = stringOrNull("currencyCode", "currency_code") ?: "ZAR",
    status = ServiceCentreBookingStatus.valueOf(string("status")),
    acceptedAt = instantOrNull("acceptedAt", "accepted_at"),
    declinedAt = instantOrNull("declinedAt", "declined_at"),
    confirmedAt = instantOrNull("confirmedAt", "confirmed_at"),
    completedAt = instantOrNull("completedAt", "completed_at"),
    cancelledAt = instantOrNull("cancelledAt", "cancelled_at"),
    createdAt = instant("createdAt", "created_at"),
    updatedAt = instant("updatedAt", "updated_at"),
)

internal fun JsonObject.toServiceCentreMessage(): ServiceCentreMessage = ServiceCentreMessage(
    id = string("id"),
    bookingId = string("bookingId", "booking_id"),
    senderUserId = string("senderUserId", "sender_user_id"),
    senderDisplayName = stringOrNull("senderDisplayName", "sender_display_name"),
    senderAvatarUrl = stringOrNull("senderAvatarUrl", "sender_avatar_url"),
    body = string("body"),
    mine = booleanOrDefault(false, "isMine", "is_mine"),
    createdAt = instant("createdAt", "created_at"),
)

private fun JsonObject.element(vararg keys: String) = keys.firstNotNullOfOrNull { key -> get(key) }

private fun JsonObject.string(vararg keys: String): String = stringOrNull(*keys)
    ?: error("Missing Service Centre field: ${keys.joinToString()}")

private fun JsonObject.stringOrNull(vararg keys: String): String? = element(*keys)
    ?.jsonPrimitive
    ?.contentOrNull
    ?.takeUnless { it.equals("null", ignoreCase = true) }

private fun JsonObject.boolean(vararg keys: String): Boolean = element(*keys)?.jsonPrimitive?.booleanOrNull ?: false
private fun JsonObject.booleanOrDefault(default: Boolean, vararg keys: String): Boolean = element(*keys)?.jsonPrimitive?.booleanOrNull ?: default
private fun JsonObject.intOrNull(vararg keys: String): Int? = element(*keys)?.jsonPrimitive?.intOrNull
private fun JsonObject.doubleOrNull(vararg keys: String): Double? = element(*keys)?.jsonPrimitive?.doubleOrNull
private fun JsonObject.decimal(vararg keys: String): BigDecimal = decimalOrNull(*keys) ?: error("Missing Service Centre amount: ${keys.joinToString()}")
private fun JsonObject.decimalOrNull(vararg keys: String): BigDecimal? = stringOrNull(*keys)?.toBigDecimalOrNull()
private fun JsonObject.instant(vararg keys: String): Instant = instantOrNull(*keys) ?: error("Missing Service Centre timestamp: ${keys.joinToString()}")
private fun JsonObject.instantOrNull(vararg keys: String): Instant? = stringOrNull(*keys)?.let { runCatching { Instant.parse(it) }.getOrNull() }
