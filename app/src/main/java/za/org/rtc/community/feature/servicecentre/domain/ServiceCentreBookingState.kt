package za.org.rtc.community.feature.servicecentre.domain

enum class ServiceCentreBookingTab {
    PENDING,
    ACCEPTED,
    HISTORY,
}

enum class ServiceCentreBookingStatus {
    PENDING_PROVIDER,
    CONFIRMED,
    COMPLETED,
    DECLINED,
    CANCELLED,
    ;

    val tab: ServiceCentreBookingTab
        get() = when (this) {
            PENDING_PROVIDER -> ServiceCentreBookingTab.PENDING
            CONFIRMED -> ServiceCentreBookingTab.ACCEPTED
            COMPLETED, DECLINED, CANCELLED -> ServiceCentreBookingTab.HISTORY
        }

    fun canTransitionTo(target: ServiceCentreBookingStatus): Boolean = when (this) {
        PENDING_PROVIDER -> target in setOf(CONFIRMED, DECLINED, CANCELLED)
        CONFIRMED -> target in setOf(COMPLETED, CANCELLED)
        COMPLETED, DECLINED, CANCELLED -> false
    }

    val terminal: Boolean
        get() = this in setOf(COMPLETED, DECLINED, CANCELLED)
}
