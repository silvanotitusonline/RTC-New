package za.org.rtc.community.feature.servicecentre.data.remote

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates

/** Resolves a resident-entered locality without requiring device location permission. */
@Singleton
class ServiceCentreLocalityResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Suppress("DEPRECATION")
    suspend fun coordinates(locality: String): Result<MarketplaceCoordinates> = withContext(Dispatchers.IO) {
        runCatching {
            val value = locality.trim()
            require(value.length in 2..120) { "Enter a valid service area." }
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocationName(value, 1)
                ?.firstOrNull()
                ?: error("We could not locate that service area. Try a nearby town or suburb.")
            MarketplaceCoordinates(address.latitude, address.longitude)
        }
    }

    @Suppress("DEPRECATION")
    suspend fun locality(coordinates: MarketplaceCoordinates): String? = withContext(Dispatchers.IO) {
        runCatching {
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                ?.firstOrNull()
            address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
        }.getOrNull()?.trim()?.takeIf(String::isNotBlank)
    }
}
