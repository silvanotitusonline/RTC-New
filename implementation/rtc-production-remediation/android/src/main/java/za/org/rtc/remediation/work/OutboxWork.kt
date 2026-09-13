package za.org.rtc.remediation.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import za.org.rtc.remediation.data.RtcRepository

class OutboxScheduler(context: Context) {
    private val work = WorkManager.getInstance(context.applicationContext)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue(owner: String) {
        val request = OneTimeWorkRequestBuilder<RtcSyncWorker>()
            .setConstraints(constraints).setInputData(workDataOf("owner" to owner))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        // Appending avoids KEEP's enqueue-at-worker-exit race; all mutations still use
        // the same durable idempotency key and DAO lease.
        work.enqueueUniqueWork("rtc-outbox-$owner", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    fun reconcilePeriodically(owner: String) {
        val request = PeriodicWorkRequestBuilder<RtcSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).setInputData(workDataOf("owner" to owner))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        work.enqueueUniquePeriodicWork("rtc-outbox-recovery-$owner",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun cancel(owner: String) {
        work.cancelUniqueWork("rtc-outbox-$owner")
        work.cancelUniqueWork("rtc-outbox-recovery-$owner")
    }
}
class RtcSyncWorker(context: Context, params: WorkerParameters, private val repository: RtcRepository) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        return when(repository.drain(owner)) {
            DrainResult.COMPLETE, DrainResult.WAIT_FOR_AUTH -> Result.success()
            DrainResult.RETRY -> Result.retry()
        }
    }
}
enum class DrainResult { COMPLETE, WAIT_FOR_AUTH, RETRY }
class RtcWorkerFactory(private val repository: () -> RtcRepository) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if(workerClassName == RtcSyncWorker::class.java.name) RtcSyncWorker(appContext,workerParameters,repository()) else null
}
