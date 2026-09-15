package za.org.rtc.community.feature.communityhub

import androidx.compose.runtime.Composable
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.presentation.PublicReportsScreen
import za.org.rtc.community.navigation.CommunitySection

@Composable
fun CommunityHubScreen(
    initialSection: CommunitySection = CommunitySection.DISCUSSIONS,
    initialReportScope: PublicReportScope = PublicReportScope.VERIFIED,
    isAdmin: Boolean = false,
    discussions: @Composable (onNavigateToReports: () -> Unit) -> Unit,
    onOpenReport: (String) -> Unit,
    onComposeReport: () -> Unit,
    onOpenAdminWorkspace: (() -> Unit)? = null,
) {
    CommunityModernisationScreen(
        initialSection = initialSection,
        discussions = discussions,
        reports = {
            PublicReportsScreen(
                initialScope = initialReportScope,
                isAdmin = isAdmin,
                onOpenReport = onOpenReport,
                onCompose = onComposeReport,
                onOpenAdminWorkspace = onOpenAdminWorkspace,
            )
        },
    )
}
