package za.org.rtc.community.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val RTC_SAFETY_ALERTS_CHANNEL = "rtc_safety_alerts"
const val RTC_COMMUNITY_UPDATES_CHANNEL = "rtc_community_updates"
const val RTC_SERVICE_BOOKINGS_CHANNEL = "rtc_service_bookings"

fun createRtcNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    val safety = NotificationChannel(
        RTC_SAFETY_ALERTS_CHANNEL,
        "Safety alerts",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Urgent RTC safety and emergency information."
        enableVibration(true)
    }
    val community = NotificationChannel(
        RTC_COMMUNITY_UPDATES_CHANNEL,
        "Community updates",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "RTC Community notices, support and community updates."
    }
    val serviceBookings = NotificationChannel(
        RTC_SERVICE_BOOKINGS_CHANNEL,
        "Service bookings",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "Updates about your RTC Service Centre bookings and booking chat."
    }
    manager.createNotificationChannels(listOf(safety, community, serviceBookings))
}
