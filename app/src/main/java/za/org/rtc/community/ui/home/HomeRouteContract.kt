package za.org.rtc.community.ui.home

import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.feature.home.HomePublicReportState
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope

/**
 * Route-scoped input to the resident Home renderer. It keeps unrelated application
 * state out of the Home API while preserving the existing singleton session owner.
 */
internal data class HomeRouteState(
    val layout: HomeLayout,
    val displayName: String,
    val cases: List<SupportCase>,
    val notices: List<OfficialNotice>,
    val readingMode: Boolean,
    val draft: LocalDraft?,
    val pendingSyncCount: Int,
    val publicReports: HomePublicReportState,
    val isRefreshing: Boolean,
    val events: List<za.org.rtc.community.feature.events.domain.CommunityEvent> = emptyList(),
)

/** All Home-triggered effects remain explicit and are owned by the navigation host. */
internal data class HomeRouteActions(
    val onRefresh: () -> Unit,
    val onNavigate: (MainDestination) -> Unit,
    val onOpenDirectory: (String) -> Unit,
    val onOpenNotice: (OfficialNotice) -> Unit,
    val onResumeDraft: (LocalDraft) -> Unit,
    val onDiscardDraft: (LocalDraft) -> Unit,
    val onHelp: () -> Unit,
    val onOpenPublicReportScope: (PublicReportScope) -> Unit,
    val onCommunityPostDraft: (String) -> Unit,
    val onPublicReportDraft: (String) -> Unit,
    val onToggleEventRsvp: ((String) -> Unit)? = null,
)

internal data class HomeRouteContract(
    val state: HomeRouteState,
    val actions: HomeRouteActions,
)
