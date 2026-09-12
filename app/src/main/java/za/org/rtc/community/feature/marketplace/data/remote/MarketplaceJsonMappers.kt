package za.org.rtc.community.feature.marketplace.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursException
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursInterval
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation

/** Maps the public Marketplace location contract, tolerating additive fields being absent. */
internal fun JsonObject.toMarketplaceLocation() = MarketplaceLocation(
    id = stringValue("id"),
    label = stringValue("label"),
    locality = stringValue("locality"),
    municipality = stringOrNullValue("municipality"),
    province = stringOrNullValue("province"),
    address = stringOrNullValue("address"),
    visibility = stringValue("visibility"),
    latitude = doubleOrNullValue("latitude"),
    longitude = doubleOrNullValue("longitude"),
    timezone = stringValue("timezone"),
    accessibilityFeatures = arrayValue("accessibilityFeatures").mapNotNull { value ->
        value.jsonPrimitive.contentOrNull
    },
    parkingNote = stringOrNullValue("parkingNote"),
    hours = arrayValue("hours").map { value ->
        val row = value.jsonObject
        MarketplaceHoursInterval(
            dayOfWeek = row.intValue("dayOfWeek"),
            intervalOrder = row.intValue("intervalOrder"),
            state = row.stringValue("state"),
            opensAt = row.stringOrNullValue("opensAt"),
            closesAt = row.stringOrNullValue("closesAt"),
        )
    },
    hourExceptions = arrayValue("hourExceptions").map { value ->
        val row = value.jsonObject
        MarketplaceHoursException(
            date = row.stringValue("date"),
            state = row.stringValue("state"),
            opensAt = row.stringOrNullValue("opensAt"),
            closesAt = row.stringOrNullValue("closesAt"),
            note = row.stringOrNullValue("note"),
        )
    },
)

private fun JsonObject.stringValue(name: String): String = stringOrNullValue(name).orEmpty()

private fun JsonObject.stringOrNullValue(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.intValue(name: String): Int =
    this[name]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.doubleOrNullValue(name: String): Double? =
    this[name]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.arrayValue(name: String): JsonArray =
    this[name]?.jsonArray ?: JsonArray(emptyList())
