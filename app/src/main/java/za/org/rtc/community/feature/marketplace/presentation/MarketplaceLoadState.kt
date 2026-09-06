package za.org.rtc.community.feature.marketplace.presentation

import za.org.rtc.community.app.SafeUiError

sealed interface MarketplaceLoadState<out T> {
    data object Idle : MarketplaceLoadState<Nothing>
    data object Loading : MarketplaceLoadState<Nothing>
    data class Data<T>(val value: T) : MarketplaceLoadState<T>
    data class Failure(val message: String) : MarketplaceLoadState<Nothing>
}

internal fun Throwable.userMessage(): String =
    SafeUiError.marketplace(this, "Marketplace action could not be completed. Please try again.")
