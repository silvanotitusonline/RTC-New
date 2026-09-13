package za.org.rtc.remediation

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import za.org.rtc.remediation.data.RtcDatabase
import za.org.rtc.remediation.data.RtcRepository
import za.org.rtc.remediation.media.PhotoPreparation
import za.org.rtc.remediation.network.*
import za.org.rtc.remediation.work.OutboxScheduler
import za.org.rtc.remediation.work.RtcWorkerFactory

/** Create once per application process, before WorkManager executes its first worker. */
class RtcClient(context: Context, apiBaseUrl: String, sessions: SessionProvider) {
    private val app=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val database=Room.databaseBuilder(app,RtcDatabase::class.java,"rtc-node-client.db").build()
    private val connectivity=Connectivity(app)
    private val network=RtcNetwork(apiBaseUrl,sessions,connectivity)
    private val scheduler by lazy { OutboxScheduler(app) }
    val repository=RtcRepository(app,database,network,sessions,connectivity,{scheduler},scope)
    val photos=PhotoPreparation(app)
    val workerFactory=RtcWorkerFactory { repository }
    /** Call from Application.onCreate after assigning this client and its worker factory. */
    fun start() = repository.start()
    /** Tests/explicit host teardown only; normal Android Application stays process-scoped. */
    fun close() { scope.cancel(); repository.imageLoader.shutdown(); database.close() }
}
