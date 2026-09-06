package za.org.rtc.community.feature.servicecentre.domain

enum class ServiceCentreBookingTab {
    PENDING,
    ACCEPTED,
    HISTORY,
}

enum class ServiceCentreBookingStatus {
    PENDING_PROVIDER,
    ACCEPTED_AWAITING_PAYMENT,
    CONFIRMED,
    COMPLETED,
    DECLINED,
    CANCELLED,
    ;

    val tab: ServiceCentreBookingTab
        get() = when (this) {
            PENDING_PROVIDER -> ServiceCentreBookingTab.PENDING
            ACCEPTED_AWAITING_PAYMENT, CONFIRMED -> ServiceCentreBookingTab.ACCEPTED
            COMPLETED, DECLINED, CANCELLED -> ServiceCentreBookingTab.HISTORY
        }

    fun canTransitionTo(target: ServiceCentreBookingStatus): Boolean = when (this) {
        PENDING_PROVIDER -> target in setOf(ACCEPTED_AWAITING_PAYMENT, DECLINED, CANCELLED)
        ACCEPTED_AWAITING_PAYMENT -> target in setOf(CONFIRMED, CANCELLED)
        CONFIRMED -> target == COMPLETED
        COMPLETED, DECLINED, CANCELLED -> false
    }

    val terminal: Boolean
        get() = this in setOf(COMPLETED, DECLINED, CANCELLED)
}
