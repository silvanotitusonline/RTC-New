package za.org.rtc.community.feature.community

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import za.org.rtc.community.data.local.CachedPostDao
import za.org.rtc.community.supabase.ProductionUxRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunitySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cachedPostDao: CachedPostDao,
    private val productionUxRepository: ProductionUxRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var isNetworkCallbackRegistered = false

    init {
        start()
    }

    fun start() {
        registerNetworkCallback()
        scope.launch {
            syncPendingPosts()
        }
    }

    private fun registerNetworkCallback() {
        if (isNetworkCallbackRegistered) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        syncPendingPosts()
                    }
                }
            })
            isNetworkCallbackRegistered = true
        } catch (e: Exception) {
            Log.e("CommunitySyncManager", "Failed to register network callback for post sync", e)
        }
    }

    suspend fun syncPendingPosts(): Int {
        val pendingPosts = cachedPostDao.getPendingSyncPosts()
        if (pendingPosts.isEmpty()) return 0

        var syncedCount = 0
        for (post in pendingPosts) {
            try {
                val result = productionUxRepository.createCommunityPost(post.content)
                if (result.isSuccess) {
                    cachedPostDao.updatePendingSync(post.id, false)
                    syncedCount++
                }
            } catch (e: Exception) {
                Log.w("CommunitySyncManager", "Sync failed for post ${post.id}, will retry when network available", e)
            }
        }
        return syncedCount
    }
}
