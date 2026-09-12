package za.org.rtc.community.feature.marketplace.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDiscoveryRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocationRepository

@HiltWorker
class MarketplacePrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val marketplaceRepository: MarketplaceDiscoveryRepository,
    private val locationRepository: MarketplaceLocationRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val coords = locationRepository.lastKnownCoordinates()
            marketplaceRepository.home(locality = null, origin = coords).fold(
                onSuccess = {
                    Result.success()
                },
                onFailure = {
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
