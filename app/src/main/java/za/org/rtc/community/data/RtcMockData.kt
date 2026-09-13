package za.org.rtc.community.data

import za.org.rtc.community.core.AdminAnalyticsDashboard
import za.org.rtc.community.core.AdminAuditTrailEvent
import za.org.rtc.community.core.AdminLocalitySummary
import za.org.rtc.community.core.AdministrativeActivityEvent
import za.org.rtc.community.core.AssignedSupportCase
import za.org.rtc.community.core.CentreRecord
import za.org.rtc.community.core.CommunityAlert
import za.org.rtc.community.core.CommunityAlertDashboardItem
import za.org.rtc.community.core.DashboardMetrics
import za.org.rtc.community.core.EditorialNoticeRecord
import za.org.rtc.community.core.HelpArticle
import za.org.rtc.community.core.ModerationAppeal
import za.org.rtc.community.core.ModerationQueueItem
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.OperationalIncident
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.core.PublicSearchResult
import za.org.rtc.community.core.RtcNotification
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.core.SupportCaseMessage
import za.org.rtc.community.core.SystemHealthStatus

/**
 * Legacy compatibility surface only.
 *
 * Production runtime must never fabricate content when a live backend is empty or unavailable.
 * These methods intentionally return empty/default values while callers are migrated to explicit
 * loading, empty, and error states. Do not add fixture or demonstration records here.
 */
@Deprecated("Production runtime must use authoritative repositories, not sample data")
object RtcMockData {
    fun getSampleMetrics(): DashboardMetrics = DashboardMetrics()
    fun getSampleProjects(): List<ProjectRecord> = emptyList()
    fun getSampleCentres(): List<CentreRecord> = emptyList()
    fun getSampleOpportunities(): List<OpportunityRecord> = emptyList()
    fun getSampleNotices(): List<OfficialNotice> = emptyList()
    fun getSampleHelpArticles(): List<HelpArticle> = emptyList()
    fun getSampleAlerts(): List<CommunityAlert> = emptyList()
    fun getSampleAlertDashboard(): List<CommunityAlertDashboardItem> = emptyList()
    fun getSampleSupportCases(): List<SupportCase> = emptyList()
    fun getSampleAssignedCases(): List<AssignedSupportCase> = emptyList()
    fun getSampleSupportCaseMessages(caseId: String): List<SupportCaseMessage> = emptyList()
    fun getSampleNotifications(): List<RtcNotification> = emptyList()
    fun getSampleOperationsWorkQueue(): List<OperationsWorkItem> = emptyList()
    fun getSampleSystemHealth(): List<SystemHealthStatus> = emptyList()
    fun getSampleAdministrativeActivity(): List<AdministrativeActivityEvent> = emptyList()
    fun getSampleOperationalIncidents(): List<OperationalIncident> = emptyList()
    fun getSampleModerationQueue(): List<ModerationQueueItem> = emptyList()
    fun getSampleModerationAppeals(): List<ModerationAppeal> = emptyList()
    fun getSampleEditorialNotices(): List<EditorialNoticeRecord> = emptyList()
    fun getSampleAdminAnalyticsDashboard(): AdminAnalyticsDashboard = AdminAnalyticsDashboard()
    fun getSampleAdminLocalities(): List<AdminLocalitySummary> = emptyList()
    fun getSampleAdminAuditEvents(): List<AdminAuditTrailEvent> = emptyList()
    fun getSamplePublicSearchResults(query: String): List<PublicSearchResult> = emptyList()
}
