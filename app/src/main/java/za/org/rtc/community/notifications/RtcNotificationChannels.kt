package za.org.rtc.community.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val RTC_SAFETY_ALERTS_CHANNEL = "rtc_safety_alerts"
const val RTC_COMMUNITY_UPDATES_CHANNEL = "rtc_community_updates"

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
    manager.createNotificationChannels(listOf(safety, community))
}
