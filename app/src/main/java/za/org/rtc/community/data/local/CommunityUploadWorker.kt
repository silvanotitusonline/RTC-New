package za.org.rtc.community.data.local

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.org.rtc.community.supabase.ProductionUxRepository

/** Recovers durable upload rows after process/network interruption. */
@HiltWorker
class CommunityUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: RtcDatabase,
    private val production: ProductionUxRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // The durable outbox is account-scoped. Never resume media when no verified owner is active.
        val ownerUserId = production.currentAuthenticatedUserIdOrNull() ?: return Result.success()
        val pending = database.uploadOutboxDao().pendingForOwner(ownerUserId)
        if (pending.isEmpty()) return Result.success()
        var retryNeeded = false
        pending.map { it.draftId }.distinct().forEach { draftId ->
            production.resumeCommunityUpload(draftId)
                .onFailure { retryNeeded = true }
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }
}
