package za.org.rtc.community.feature.servicecentre.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingDraft
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreCategory
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreDiscoveryRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreMessage
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreNotificationEvent
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProvider
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderDraft
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderProfile
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderRepository

@Singleton
class SupabaseServiceCentreRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : ServiceCentreDiscoveryRepository, ServiceCentreProviderRepository, ServiceCentreBookingRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    override suspend fun categories(): Result<List<ServiceCentreCategory>> = runCatching {
        supabase.postgrest.rpc("service_centre_categories")
            .decodeSingle<JsonArray>()
            .map { it.jsonObject.toServiceCentreCategory() }
    }

    override suspend fun localRadar(
        categoryId: String?,
        locality: String?,
        origin: MarketplaceCoordinates?,
        radiusMetres: Int,
        offset: Int,
        limit: Int,
    ): Result<List<ServiceCentreProvider>> = runCatching {
        require(offset >= 0) { "Provider offset cannot be negative." }
        val boundedRadius = radiusMetres.coerceIn(100, 50_000)
        val boundedLimit = limit.coerceIn(1, 50)
        supabase.postgrest.rpc("service_centre_local_radar", buildJsonObject {
            categoryId?.takeIf(String::isNotBlank)?.let { put("p_category_id", it) }
            locality?.takeIf(String::isNotBlank)?.let { put("p_locality", it.trim()) }
            origin?.let {
                put("p_origin_latitude", it.latitude)
                put("p_origin_longitude", it.longitude)
            }
            put("p_radius_metres", boundedRadius)
            put("p_offset", offset)
            put("p_limit", boundedLimit)
        }).decodeList<JsonObject>().map { it.toServiceCentreProvider() }
    }

    override suspend fun provider(reference: String): Result<ServiceCentreProvider> = runCatching {
        require(reference.isNotBlank()) { "Choose a provider." }
        supabase.postgrest.rpc("service_centre_provider_detail", buildJsonObject {
            put("p_provider_reference", reference.trim())
        }).decodeSingle<JsonObject>().toServiceCentreProvider()
    }

    override suspend fun myProviderProfile(): Result<ServiceCentreProviderProfile?> = runCatching {
        val response = supabase.postgrest.rpc("service_centre_my_provider_profile")
        val element = json.parseToJsonElement(response.data)
        if (element is JsonNull) null else element.jsonObject.toServiceCentreProviderProfile()
    }

    override suspend fun upsertProviderProfile(draft: ServiceCentreProviderDraft): Result<ServiceCentreProviderProfile> = runCatching {
        supabase.postgrest.rpc("service_centre_upsert_provider_profile", buildJsonObject {
            put("p_primary_category_id", draft.primaryCategoryId)
            put("p_locality", draft.locality.trim())
            put("p_latitude", draft.latitude)
            put("p_longitude", draft.longitude)
            put("p_starting_price", draft.startingPrice.toPlainString())
            put("p_service_radius_km", draft.serviceRadiusKm.coerceIn(1, 50))
            draft.marketplaceBusinessId?.let { put("p_marketplace_business_id", it) }
        }).decodeSingle<JsonObject>().toServiceCentreProviderProfile()
    }

    override suspend fun setProviderActive(active: Boolean): Result<ServiceCentreProviderProfile> = runCatching {
        supabase.postgrest.rpc("service_centre_set_provider_active", buildJsonObject {
            put("p_active", active)
        }).decodeSingle<JsonObject>().toServiceCentreProviderProfile()
    }

    override suspend fun createBooking(draft: ServiceCentreBookingDraft): Result<ServiceCentreBooking> = runCatching {
        supabase.postgrest.rpc("service_centre_create_booking", buildJsonObject {
            put("p_provider_user_id", draft.providerUserId)
            put("p_requested_start_at", draft.requestedStartAt.toString())
            put("p_service_location_text", draft.serviceLocationText.trim())
            put("p_offer_amount", draft.offerAmount.toPlainString())
            put("p_idempotency_key", draft.idempotencyKey)
            draft.marketplaceBusinessId?.let { put("p_marketplace_business_id", it) }
            draft.marketplaceOfferingId?.let { put("p_marketplace_offering_id", it) }
        }).decodeSingle<JsonObject>().toServiceCentreBooking().also { booking ->
            notifyBookingEvent(booking.id, ServiceCentreNotificationEvent.SERVICE_BOOKING_NEW)
        }
    }

    override suspend fun myBookings(): Result<List<ServiceCentreBooking>> = runCatching {
        supabase.postgrest.rpc("service_centre_my_bookings")
            .decodeSingle<JsonArray>()
            .map { it.jsonObject.toServiceCentreBooking() }
    }

    override suspend fun bookingDetail(bookingId: String): Result<ServiceCentreBooking> = runCatching {
        require(bookingId.isNotBlank()) { "Choose a booking." }
        supabase.postgrest.rpc("service_centre_booking_detail", buildJsonObject {
            put("p_booking_id", bookingId)
        }).decodeSingle<JsonObject>().toServiceCentreBooking()
    }

    override suspend fun acceptBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking> =
        transition("service_centre_accept_booking", bookingId, idempotencyKey, ServiceCentreNotificationEvent.SERVICE_BOOKING_ACCEPTED)

    override suspend fun declineBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking> =
        transition("service_centre_decline_booking", bookingId, idempotencyKey, ServiceCentreNotificationEvent.SERVICE_BOOKING_DECLINED)

    override suspend fun cancelBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking> =
        transition("service_centre_cancel_booking", bookingId, idempotencyKey, ServiceCentreNotificationEvent.SERVICE_BOOKING_CANCELLED)

    override suspend fun completeBooking(bookingId: String, idempotencyKey: String): Result<ServiceCentreBooking> =
        transition("service_centre_complete_booking", bookingId, idempotencyKey, ServiceCentreNotificationEvent.SERVICE_BOOKING_COMPLETED)

    override suspend fun messages(bookingId: String, limit: Int): Result<List<ServiceCentreMessage>> = runCatching {
        require(bookingId.isNotBlank()) { "Choose a booking conversation." }
        supabase.postgrest.rpc("service_centre_booking_messages", buildJsonObject {
            put("p_booking_id", bookingId)
            put("p_limit", limit.coerceIn(1, 200))
        }).decodeSingle<JsonArray>().map { it.jsonObject.toServiceCentreMessage() }
    }

    override suspend fun sendMessage(bookingId: String, body: String, idempotencyKey: String): Result<ServiceCentreMessage> = runCatching {
        val message = supabase.postgrest.rpc("service_centre_send_message", buildJsonObject {
            put("p_booking_id", bookingId)
            put("p_body", body.trim())
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().toServiceCentreMessage()
        notifyBookingEvent(bookingId, ServiceCentreNotificationEvent.SERVICE_BOOKING_MESSAGE, message.id)
        message
    }

    override suspend fun notifyBookingEvent(
        bookingId: String,
        event: ServiceCentreNotificationEvent,
        messageId: String?,
    ): Result<Unit> = runCatching {
        val response = supabase.functions.invoke("service-centre-notify", buildJsonObject {
            put("bookingId", bookingId)
            put("eventType", event.name)
            messageId?.let { put("messageId", it) }
        })
        check(response.status.value in 200..299) { "The booking was saved, but the notification could not be delivered." }
    }

    private suspend fun transition(
        rpc: String,
        bookingId: String,
        idempotencyKey: String,
        notification: ServiceCentreNotificationEvent,
    ): Result<ServiceCentreBooking> = runCatching {
        val booking = supabase.postgrest.rpc(rpc, buildJsonObject {
            put("p_booking_id", bookingId)
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().toServiceCentreBooking()
        notifyBookingEvent(booking.id, notification)
        booking
    }

}
