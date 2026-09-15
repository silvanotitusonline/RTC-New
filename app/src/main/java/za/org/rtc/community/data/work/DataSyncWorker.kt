package za.org.rtc.community.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.org.rtc.community.data.RtcRepository
import za.org.rtc.community.feature.community.CommunitySyncManager

/**
 * Periodic WorkManager task injected with Hilt to sync critical user profile
 * and feed data in the background, keeping local Room cached content fresh when app is closed.
 */
@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val rtcRepository: RtcRepository,
    private val communitySyncManager: CommunitySyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic background sync for user profile & community feed...")

            // 1. Sync pending offline posts if network is available
            val syncedPendingCount = communitySyncManager.syncPendingPosts()
            Log.d(TAG, "Synced $syncedPendingCount pending offline posts")

            // 2. Fetch fresh user session profile, feed items, and persist to Room database
            rtcRepository.refreshLiveContent()

            Log.d(TAG, "Background user profile & feed data sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic background data sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "DataSyncWorker"
        const val WORK_NAME = "periodic_user_profile_feed_data_sync"
    }
}
