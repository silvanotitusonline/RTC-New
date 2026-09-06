package za.org.rtc.community.ui.navigation

import androidx.navigation.NavHostController
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.navigatePrimary

internal object ResidentComposerPrefill {
    const val PUBLIC_REPORT_DESCRIPTION = "public_report_description_prefill"
    const val COMMUNITY_REPORT_SCOPE = "community_public_report_scope"
    const val COMMUNITY_INITIAL_SECTION = "community_initial_section"
    const val COMMUNITY_OPEN_COMPOSER = "community_open_composer"
}

internal fun NavHostController.openCommunityReports(scope: PublicReportScope) {
    navigatePrimary(RtcRoute.COMMUNITY)
    getBackStackEntry(RtcRoute.COMMUNITY).savedStateHandle.apply {
        set(ResidentComposerPrefill.COMMUNITY_INITIAL_SECTION, "reports")
        set(ResidentComposerPrefill.COMMUNITY_REPORT_SCOPE, scope.name)
    }
}

internal fun NavHostController.openCommunityFeed() {
    navigatePrimary(RtcRoute.COMMUNITY)
    getBackStackEntry(RtcRoute.COMMUNITY).savedStateHandle[
        ResidentComposerPrefill.COMMUNITY_INITIAL_SECTION
    ] = "discussions"
    getBackStackEntry(RtcRoute.COMMUNITY).savedStateHandle[
        ResidentComposerPrefill.COMMUNITY_OPEN_COMPOSER
    ] = true
}

internal fun NavHostController.openPublicReportComposer(description: String) {
    val prefill = description.trim().takeIf { it.isNotEmpty() } ?: return
    navigateOverlay(RtcRoute.PUBLIC_REPORT_NEW)
    getBackStackEntry(RtcRoute.PUBLIC_REPORT_NEW).savedStateHandle[
        ResidentComposerPrefill.PUBLIC_REPORT_DESCRIPTION
    ] = prefill
}
