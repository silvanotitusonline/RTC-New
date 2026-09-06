package za.org.rtc.community.core.map.tomtom

import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates

/**
 * The Marketplace feature depends on this boundary rather than TomTom UI classes directly.
 * A live implementation is enabled only when a compatible SDK and non-secret mobile key are
 * provisioned outside source control. The fallback deliberately returns no synthetic maps/routes.
 */
interface MarketplaceMapGateway {
    val availability: MarketplaceMapAvailability
    suspend fun searchAddress(query: String): Result<List<MarketplaceAddressSuggestion>>
    suspend fun reverseGeocode(point: MarketplaceCoordinates): Result<MarketplaceAddressSuggestion?>
}

interface MarketplaceRoutingGateway {
    val availability: MarketplaceMapAvailability
    suspend fun previewRoute(origin: MarketplaceCoordinates, destination: MarketplaceCoordinates): Result<MarketplaceRoutePreview>
}

interface MarketplaceNavigationController {
    val availability: MarketplaceMapAvailability
    fun start(destination: MarketplaceCoordinates): Result<Unit>
    fun stop()
}

data class MarketplaceMapAvailability(val available: Boolean, val message: String)
data class MarketplaceAddressSuggestion(val label: String, val locality: String, val coordinates: MarketplaceCoordinates)
data class MarketplaceRoutePreview(val distanceMetres: Int, val durationSeconds: Int)

class UnavailableTomTomMarketplaceGateway : MarketplaceMapGateway, MarketplaceRoutingGateway, MarketplaceNavigationController {
    override val availability = MarketplaceMapAvailability(
        available = false,
        message = "Maps and in-app navigation are unavailable because the required TomTom mobile SDK configuration has not been provisioned for this build.",
    )
    override suspend fun searchAddress(query: String): Result<List<MarketplaceAddressSuggestion>> = Result.failure(IllegalStateException(availability.message))
    override suspend fun reverseGeocode(point: MarketplaceCoordinates): Result<MarketplaceAddressSuggestion?> = Result.failure(IllegalStateException(availability.message))
    override suspend fun previewRoute(origin: MarketplaceCoordinates, destination: MarketplaceCoordinates): Result<MarketplaceRoutePreview> = Result.failure(IllegalStateException(availability.message))
    override fun start(destination: MarketplaceCoordinates): Result<Unit> = Result.failure(IllegalStateException(availability.message))
    override fun stop() = Unit
}
