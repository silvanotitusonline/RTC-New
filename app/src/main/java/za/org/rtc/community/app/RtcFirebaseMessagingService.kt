package za.org.rtc.community.app

import android.Manifest
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.MainActivity
import za.org.rtc.community.R
import za.org.rtc.community.data.RtcRepository
import za.org.rtc.community.notifications.RTC_COMMUNITY_UPDATES_CHANNEL
import za.org.rtc.community.notifications.RTC_SAFETY_ALERTS_CHANNEL
import za.org.rtc.community.notifications.RTC_SERVICE_BOOKINGS_CHANNEL
import javax.inject.Inject

@AndroidEntryPoint
class RtcFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var repository: RtcRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch { repository.registerFcmDevice(token, BuildConfig.VERSION_NAME) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val notificationType = message.data["notification_type"].orEmpty()
        if (notificationType.startsWith("SERVICE_BOOKING")) {
            postServiceBookingNotification(message)
            return
        }
        if (notificationType.startsWith("PUBLIC_REPORT") || notificationType.startsWith("REPORT_STATUS") || message.data.containsKey("report_id") || message.data.containsKey("public_report_id")) {
            postPublicReportNotification(message)
            return
        }
        postCommunityNotification(message)
    }

    private fun postPublicReportNotification(message: RemoteMessage) {
        val reportId = message.data["report_id"] ?: message.data["public_report_id"] ?: message.data["reportId"] ?: return
        if (!isSafeId(reportId)) return
        val status = message.data["status"] ?: message.data["new_status"]
        val department = message.data["department"] ?: "Municipal Department"
        val title = message.notification?.title ?: message.data["title"] ?: "Report Status Update"
        val body = message.notification?.body ?: message.data["body"]
            ?: "Status for reported issue #$reportId updated by $department${if (status != null) " to $status" else ""}."

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_OPEN_PUBLIC_REPORT
            data = Uri.parse("rtc://public-reports/report/$reportId")
            putExtra(MainActivity.EXTRA_PUBLIC_REPORT_ID, reportId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            reportId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, RTC_COMMUNITY_UPDATES_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.take(120))
            .setContentText(body.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1000)))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notifyIfPermitted(reportId.hashCode(), notification)
    }

    private fun postServiceBookingNotification(message: RemoteMessage) {
        val bookingId = message.data["booking_id"] ?: message.data["bookingId"] ?: return
        if (!isSafeId(bookingId)) return
        val title = message.notification?.title ?: message.data["title"] ?: "Service booking"
        val body = message.notification?.body ?: message.data["body"] ?: "Your service booking has been updated."
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_OPEN_SERVICE_BOOKING
            data = Uri.parse("rtc://service-centre/booking/$bookingId")
            putExtra(MainActivity.EXTRA_SERVICE_BOOKING_ID, bookingId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            bookingId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, RTC_SERVICE_BOOKINGS_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.take(120))
            .setContentText(body.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1000)))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(bookingId.hashCode(), notification)
    }

    private fun postCommunityNotification(message: RemoteMessage) {
        val alertId = message.data["community_alert_id"] ?: message.data["alertId"] ?: message.data["alert_id"]
        val title = message.notification?.title ?: message.data["title"] ?: "RTC Community"
        val body = message.notification?.body ?: message.data["body"] ?: message.data["summary"] ?: return
        val category = message.data["category"].orEmpty()
        val mediaUrl = message.data["media_url"] ?: message.data["image_url"]
        val channel = if (category.equals("SAFETY_EMERGENCY", true) || message.data["safety"] == "true") {
            RTC_SAFETY_ALERTS_CHANNEL
        } else RTC_COMMUNITY_UPDATES_CHANNEL

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_OPEN_COMMUNITY_ALERT
            alertId?.takeIf(::isSafeId)?.let { putExtra(MainActivity.EXTRA_COMMUNITY_ALERT_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId?.hashCode() ?: message.messageId?.hashCode() ?: 0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationId = alertId?.hashCode() ?: System.currentTimeMillis().toInt()

        // No image: post immediately on the current thread.
        if (mediaUrl.isNullOrBlank() || !mediaUrl.startsWith("http")) {
            postCommunityText(title, body, channel, pendingIntent, notificationId)
            return
        }

        // Image present: fetch off the main thread, then post a BigPicture notification.
        serviceScope.launch {
            val bitmap = downloadNotificationImage(mediaUrl)
            withContext(Dispatchers.Main) {
                if (bitmap == null) {
                    postCommunityText(title, body, channel, pendingIntent, notificationId)
                } else {
                    postCommunityRich(title, body, channel, pendingIntent, notificationId, bitmap)
                }
            }
        }
    }

    private fun postCommunityText(
        title: String,
        body: String,
        channel: String,
        pendingIntent: PendingIntent,
        notificationId: Int,
    ) {
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.take(120))
            .setContentText(body.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1000)))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (channel == RTC_SAFETY_ALERTS_CHANNEL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(notificationId, notification)
    }

    private fun postCommunityRich(
        title: String,
        body: String,
        channel: String,
        pendingIntent: PendingIntent,
        notificationId: Int,
        bitmap: Bitmap,
    ) {
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.take(120))
            .setContentText(body.take(240))
            .setLargeIcon(bitmap)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null)
                    .setSummaryText(body.take(240)),
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (channel == RTC_SAFETY_ALERTS_CHANNEL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(notificationId, notification)
    }

    private suspend fun downloadNotificationImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection()
            connection.connectTimeout = IMAGE_FETCH_TIMEOUT_MS
            connection.readTimeout = IMAGE_FETCH_TIMEOUT_MS
            connection.getInputStream().use { stream -> BitmapFactory.decodeStream(stream) }
        }.getOrNull()
    }
    private fun notifyIfPermitted(id: Int, notification: android.app.Notification) {
        if (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        }
    }

    private fun isSafeId(value: String): Boolean = value.length in 1..128 && value.all { it.isLetterOrDigit() || it in "-_" }
}
