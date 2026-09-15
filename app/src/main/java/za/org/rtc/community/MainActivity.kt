package za.org.rtc.community

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.ui.config.RtcConfiguredAppRoot
import za.org.rtc.community.ui.navigation.ServiceCentreDeepLinkScope

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient
    private val rtcViewModel: RtcViewModel by viewModels()
    private val pendingServiceCentreBookingId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            rtcViewModel.isSessionRestoring.value
        }
        supabase.handleDeeplinks(intent) {
            if (intent.data?.getQueryParameter("type") == "recovery") rtcViewModel.beginPasswordRecovery()
        }
        handleCommunityAlertIntent(intent)
        handleCommunityPostIntent(intent)
        handlePublicReportIntent(intent)
        handleServiceCentreIntent(intent)
        handleDailyPostIntent(intent)
        applyDebugSessionIntent(intent)
        setTheme(R.style.Theme_RtcCommunity)
        enableEdgeToEdge()
        setContent {
            ServiceCentreDeepLinkScope(
                bookingId = pendingServiceCentreBookingId.value,
                onConsumed = { pendingServiceCentreBookingId.value = null },
            ) {
                RtcConfiguredAppRoot(rtcViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabase.handleDeeplinks(intent) {
            if (intent.data?.getQueryParameter("type") == "recovery") rtcViewModel.beginPasswordRecovery()
        }
        handleCommunityAlertIntent(intent)
        handleCommunityPostIntent(intent)
        handlePublicReportIntent(intent)
        handleServiceCentreIntent(intent)
        handleDailyPostIntent(intent)
        applyDebugSessionIntent(intent)
    }

    private fun applyDebugSessionIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val debugRole = intent?.getStringExtra(EXTRA_DEBUG_SESSION_ROLE)
        if (debugRole == DEBUG_RESIDENT_A) {
            rtcViewModel.beginDevelopmentResidentSession()
        }
    }

    private fun handleCommunityAlertIntent(intent: Intent?) {
        val alertId = intent?.getStringExtra(EXTRA_COMMUNITY_ALERT_ID)
            ?: intent?.data?.takeIf { it.scheme == "rtc" && it.host == "community" && it.pathSegments.firstOrNull() == "alert" }
                ?.pathSegments?.getOrNull(1)
        if (intent?.action == ACTION_OPEN_COMMUNITY_ALERT || !alertId.isNullOrBlank()) {
            rtcViewModel.openCommunityAlertFromSystemNotification(
                alertId?.takeIf { value ->
                    value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
                }
            )
        }
    }

    private fun handleCommunityPostIntent(intent: Intent?) {
        val postId = intent?.data
            ?.takeIf { it.scheme == "rtc" && it.host == "community" && it.pathSegments.firstOrNull() == "post" }
            ?.pathSegments?.getOrNull(1)
        if (!postId.isNullOrBlank()) rtcViewModel.openCommunityPostFromDeepLink(postId)
    }

    private fun handlePublicReportIntent(intent: Intent?) {
        val reportId = intent?.getStringExtra(EXTRA_PUBLIC_REPORT_ID)
            ?: intent?.data
                ?.takeIf { it.scheme == "rtc" && it.host == "public-reports" && it.pathSegments.firstOrNull() == "report" }
                ?.pathSegments?.getOrNull(1)
        if (intent?.action == ACTION_OPEN_PUBLIC_REPORT || !reportId.isNullOrBlank()) {
            reportId?.takeIf { value ->
                value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
            }?.let { safeId ->
                rtcViewModel.openPublicReportFromDeepLink(safeId)
            }
        }
    }

    private fun handleServiceCentreIntent(intent: Intent?) {
        val bookingId = intent?.getStringExtra(EXTRA_SERVICE_BOOKING_ID)
            ?: intent?.data
                ?.takeIf { it.scheme == "rtc" && it.host == "service-centre" && it.pathSegments.firstOrNull() == "booking" }
                ?.pathSegments?.getOrNull(1)
        if (intent?.action == ACTION_OPEN_SERVICE_BOOKING || !bookingId.isNullOrBlank()) {
            pendingServiceCentreBookingId.value = bookingId?.takeIf { value ->
                value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
            }
        }
    }

    private fun handleDailyPostIntent(intent: Intent?) {
        val articleId = intent?.getStringExtra(EXTRA_DAILY_POST_ID)
            ?: intent?.data
                ?.takeIf { it.scheme == "rtc" && it.host == "daily-post" && it.pathSegments.firstOrNull() == "article" }
                ?.pathSegments?.getOrNull(1)
        if (intent?.action == ACTION_OPEN_DAILY_POST || !articleId.isNullOrBlank()) {
            articleId?.takeIf { value ->
                value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
            }?.let { safeId ->
                rtcViewModel.openDailyPostFromDeepLink(safeId)
            }
        }
    }

    companion object {
        const val ACTION_OPEN_COMMUNITY_ALERT = "za.org.rtc.community.OPEN_COMMUNITY_ALERT"
        const val EXTRA_COMMUNITY_ALERT_ID = "community_alert_id"
        const val ACTION_OPEN_PUBLIC_REPORT = "za.org.rtc.community.OPEN_PUBLIC_REPORT"
        const val EXTRA_PUBLIC_REPORT_ID = "public_report_id"
        const val ACTION_OPEN_SERVICE_BOOKING = "za.org.rtc.community.OPEN_SERVICE_BOOKING"
        const val EXTRA_SERVICE_BOOKING_ID = "service_booking_id"
        const val ACTION_OPEN_DAILY_POST = "za.org.rtc.community.OPEN_DAILY_POST"
        const val EXTRA_DAILY_POST_ID = "daily_post_id"
        const val EXTRA_DEBUG_SESSION_ROLE = "za.org.rtc.community.DEBUG_SESSION_ROLE"
        private const val DEBUG_RESIDENT_A = "RESIDENT_A"
    }
}
