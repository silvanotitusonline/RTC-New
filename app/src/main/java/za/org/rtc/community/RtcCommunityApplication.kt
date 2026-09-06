package za.org.rtc.community

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import za.org.rtc.community.feature.marketplace.data.work.MarketplacePrefetchWorker
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import za.org.rtc.community.core.configuration.RemoteFeatureManager
import za.org.rtc.community.notifications.createRtcNotificationChannels
import javax.inject.Inject

@HiltAndroidApp
class RtcCommunityApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var remoteFeatureManager: RemoteFeatureManager

    val marketplaceImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("marketplace_persistent_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB persistent disk cache for offline gallery persistence
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // aggressively cache regardless of server headers
            .build()
    }
    override fun onCreate() {
        super.onCreate()
        createRtcNotificationChannels(this)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
            
        val prefetchWork = PeriodicWorkRequestBuilder<MarketplacePrefetchWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "marketplace_prefetch",
            ExistingPeriodicWorkPolicy.KEEP,
            prefetchWork
        )
    }
}
