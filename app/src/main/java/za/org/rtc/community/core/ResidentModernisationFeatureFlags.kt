package za.org.rtc.community.core

/**
 * Client-side rollout guard for resident-modernisation capabilities.
 *
 * Production-beta capabilities are fixed at build time. The application deliberately exposes
 * no mutable client-side override, so residents cannot bypass server authorization or select an
 * environment from the device.
 */
enum class ResidentModernisationFeature(
    val configKey: String,
    val releaseDefaultEnabled: Boolean = false,
) {
    PUBLIC_REPORTS("public_reports_enabled", releaseDefaultEnabled = true),
    RESIDENT_NAVIGATION_V2("resident_navigation_v2_enabled", releaseDefaultEnabled = true),
    COMMUNITY_EVENTS("community_events_enabled", releaseDefaultEnabled = true),
    UNIFIED_INBOX("unified_inbox_enabled", releaseDefaultEnabled = true),
}

internal object ResidentModernisationFeatureFlags {
    fun isEnabled(feature: ResidentModernisationFeature): Boolean = feature.releaseDefaultEnabled
}
