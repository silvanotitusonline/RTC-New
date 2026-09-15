package za.org.rtc.community.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.org.rtc.community.data.local.RtcDatabase
import java.util.concurrent.TimeUnit

/**
 * Hilt-injected WorkManager task that periodically cleans up stale or expired cached records
 * from the Room database to ensure local storage remains performant.
 */
@HiltWorker
class CacheCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: RtcDatabase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic Room database cache cleanup...")

            // Define retention threshold: records older than 30 days
            val thirtyDaysMillis = TimeUnit.DAYS.toMillis(30)
            val cutoffEpochMillis = System.currentTimeMillis() - thirtyDaysMillis

            val deletedPosts = database.cachedPostDao().deleteStalePosts(cutoffEpochMillis)
            val deletedComments = database.cachedCommentDao().deleteStaleComments(cutoffEpochMillis)
            val deletedReports = database.cachedReportDao().deleteStaleReports(cutoffEpochMillis)
            val deletedAppState = database.cachedAppStateDao().deleteStaleAppState(cutoffEpochMillis)
            val deletedArticles = database.dailyPostDao().deleteStaleArticles(cutoffEpochMillis)

            val totalDeleted = deletedPosts + deletedComments + deletedReports + deletedAppState + deletedArticles

            Log.d(
                TAG,
                "Room database cache cleanup completed: deleted $totalDeleted stale records " +
                        "(Posts: $deletedPosts, Comments: $deletedComments, Reports: $deletedReports, AppState: $deletedAppState, Articles: $deletedArticles)"
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Room database cache cleanup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "CacheCleanupWorker"
        const val WORK_NAME = "periodic_room_cache_cleanup"
    }
}
