package za.org.rtc.community.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import za.org.rtc.community.MainActivity
import za.org.rtc.community.R
import za.org.rtc.community.notifications.RTC_SAFETY_ALERTS_CHANNEL

data class HighIssueHotspotZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val activeReportCount: Int,
    val mainIssueTypes: String,
)

object HighIssueGeofenceData {
    val HOTSPOTS = listOf(
        HighIssueHotspotZone(
            id = "zone_main_rd",
            name = "Main Rd & 4th Ave Corridor",
            latitude = -26.2041,
            longitude = 28.0473,
            radiusMeters = 500f,
            activeReportCount = 8,
            mainIssueTypes = "Potholes & Road Resurfacing",
        ),
        HighIssueHotspotZone(
            id = "zone_park_north",
            name = "Community Park & North Gate",
            latitude = -26.2085,
            longitude = 28.0420,
            radiusMeters = 400f,
            activeReportCount = 5,
            mainIssueTypes = "Streetlight Outages & Public Safety",
        ),
        HighIssueHotspotZone(
            id = "zone_rec_center",
            name = "Recreation Centre Sector 2",
            latitude = -26.1980,
            longitude = 28.0550,
            radiusMeters = 600f,
            activeReportCount = 6,
            mainIssueTypes = "Water Main Leaks & Pipe Outages",
        ),
        HighIssueHotspotZone(
            id = "zone_pine_street",
            name = "Pine Street Meadow",
            latitude = -26.2120,
            longitude = 28.0510,
            radiusMeters = 500f,
            activeReportCount = 4,
            mainIssueTypes = "Illegal Dumping & Waste Clearance",
        ),
    )
}

class HighIssueGeofenceService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val notifiedZones = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasLocationPermission(this)) {
            stopSelf()
            return
        }
        try {
            startForegroundServiceNotification()
            startLocationTracking()
        } catch (e: SecurityException) {
            stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasLocationPermission(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, RTC_SAFETY_ALERTS_CHANNEL)
            .setContentTitle("Civic Geofencing Active")
            .setContentText("Monitoring high-concentration issue zones nearby.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasLocationPermission(this)) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                    )
                } else {
                    stopSelf()
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000L,
                    20f,
                    this,
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L,
                    20f,
                    this,
                )
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        checkGeofenceHotspots(location.latitude, location.longitude)
    }

    private fun checkGeofenceHotspots(userLat: Double, userLng: Double) {
        for (hotspot in HighIssueGeofenceData.HOTSPOTS) {
            val results = FloatArray(1)
            Location.distanceBetween(
                userLat,
                userLng,
                hotspot.latitude,
                hotspot.longitude,
                results,
            )
            val distance = results[0]

            if (distance <= hotspot.radiusMeters) {
                if (!notifiedZones.contains(hotspot.id)) {
                    notifiedZones.add(hotspot.id)
                    sendHotspotNotification(this, hotspot, distance.toInt())
                }
            } else {
                notifiedZones.remove(hotspot.id)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        runCatching { locationManager.removeUpdates(this) }
    }

    companion object {
        const val NOTIFICATION_ID = 8842

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun start(context: Context): Boolean {
            if (!hasLocationPermission(context)) {
                return false
            }
            return try {
                val intent = Intent(context, HighIssueGeofenceService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, HighIssueGeofenceService::class.java)
                context.stopService(intent)
            }
        }

        fun sendHotspotNotification(context: Context, hotspot: HighIssueHotspotZone, distanceMeters: Int) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = MainActivity.ACTION_OPEN_PUBLIC_REPORT
                data = Uri.parse("rtc://public-reports/hotspot/${hotspot.id}")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                hotspot.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, RTC_SAFETY_ALERTS_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⚠️ High Issue Zone Alert")
                .setContentText("You entered ${hotspot.name} (${hotspot.activeReportCount} active reports nearby).")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "You entered ${hotspot.name} ($distanceMeters m away).\n" +
                            "High concentration of reported issues: ${hotspot.mainIssueTypes} (${hotspot.activeReportCount} active issues)."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(hotspot.id.hashCode(), notification)
        }
    }
}
