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
        val payloadType = message.data["type"].orEmpty()
        if (notificationType == "DAILY_POST" || payloadType == "DAILY_POST") {
            postDailyPostNotification(message)
            return
        }
        if (notificationType.startsWith("PUBLIC_REPORT") || notificationType.startsWith("REPORT_STATUS") || message.data.containsKey("report_id") || message.data.containsKey("public_report_id")) {
            postPublicReportNotification(message)
            return
        }
        postCommunityNotification(message)
    }

    private fun postDailyPostNotification(message: RemoteMessage) {
        val postId = message.data["post_id"] ?: message.data["daily_post_id"] ?: return
        if (!isSafeId(postId)) return
        val title = message.notification?.title ?: message.data["title"] ?: "The Daily Post"
        val body = message.notification?.body ?: message.data["body"] ?: "Open The Daily Post for the full story."
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_OPEN_DAILY_POST
            data = Uri.parse("rtc://community/daily-post/$postId")
            putExtra(MainActivity.EXTRA_DAILY_POST_ID, postId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ("daily-post:$postId").hashCode(),
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
            .setPriority(if (title.startsWith("BREAKING", ignoreCase = true)) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfPermitted(("daily-post:$postId").hashCode(), notification)
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

        if (mediaUrl.isNullOrBlank() || !mediaUrl.startsWith("http")) {
            postCommunityText(title, body, channel, pendingIntent, notificationId)
            return
        }

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
        val pictureStyle = NotificationCompat.BigPictureStyle()
            .bigPicture(bitmap)
            .bigLargeIcon(null as Bitmap?)
            .setSummaryText(body.take(240))

        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.take(120))
            .setContentText(body.take(240))
            .setLargeIcon(bitmap)
            .setStyle(pictureStyle)
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

    private companion object {
        const val IMAGE_FETCH_TIMEOUT_MS = 5_000
    }
}
