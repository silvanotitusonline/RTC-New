package za.org.rtc.community.core.notifications

/**
 * Canonical push payload contract shared by the admin composer, the FCM service
 * and the Supabase edge function that ultimately dispatches the message.
 *
 * Keys are part of the wire format: [RtcFirebaseMessagingService] reads
 * "notification_type", "community_alert_id", "media_url" and "category"
 * by exactly these names, so changing a key here changes the transport.
 */
object NotificationPayloads {

    const val TYPE_COMMUNITY_ALERT = "COMMUNITY_ALERT"
    const val TYPE_SERVICE_BOOKING = "SERVICE_BOOKING"
    const val TYPE_PUBLIC_REPORT = "PUBLIC_REPORT"

    const val CATEGORY_SAFETY_EMERGENCY = "SAFETY_EMERGENCY"
    const val CATEGORY_COMMUNITY_UPDATE = "COMMUNITY_UPDATE"

    const val KEY_NOTIFICATION_TYPE = "notification_type"
    const val KEY_ALERT_ID = "community_alert_id"
    const val KEY_MEDIA_URL = "media_url"
    const val KEY_CATEGORY = "category"
    const val KEY_TITLE = "title"
    const val KEY_BODY = "body"

    /**
     * Builds the data map handed to the dispatch edge function.
     *
     * [mediaUrl] is optional; when present and absolute the receiving client
     * renders a BigPicture notification instead of a plain text one.
     */
    fun createNotificationPayload(
        title: String,
        body: String,
        alertId: String? = null,
        mediaUrl: String? = null,
        category: String = CATEGORY_COMMUNITY_UPDATE,
        type: String = TYPE_COMMUNITY_ALERT,
    ): Map<String, String> {
        val payload = linkedMapOf(
            KEY_TITLE to title,
            KEY_BODY to body,
            KEY_CATEGORY to category,
            KEY_NOTIFICATION_TYPE to type,
        )
        alertId?.takeIf { it.isNotBlank() }?.let { payload[KEY_ALERT_ID] = it }
        mediaUrl?.takeIf { it.isNotBlank() }?.let { payload[KEY_MEDIA_URL] = it }
        return payload
    }

    /** Safety alerts route to the high-importance channel on the client. */
    fun isSafetyCritical(category: String): Boolean =
        category.equals(CATEGORY_SAFETY_EMERGENCY, ignoreCase = true)
}
