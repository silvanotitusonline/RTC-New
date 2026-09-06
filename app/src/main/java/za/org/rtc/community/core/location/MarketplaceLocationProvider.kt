package za.org.rtc.community.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocationRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Foreground-only, user-triggered approximate/precise Marketplace location lookup. */
@Singleton
class MarketplaceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : MarketplaceLocationRepository {
    override fun lastKnownCoordinates(): MarketplaceCoordinates? {
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val approximate = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!precise && !approximate) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = buildList {
            if (precise) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val location = providers.asSequence().mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time } ?: return null
        return MarketplaceCoordinates(location.latitude, location.longitude)
    }
}
