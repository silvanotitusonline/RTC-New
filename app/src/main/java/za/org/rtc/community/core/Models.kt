package za.org.rtc.community.core

import java.time.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UserRole {
    ANONYMOUS_PUBLIC,
    RESIDENT_A,
    RESIDENT_B,
    CASE_STAFF,
    CONTENT_EDITOR,
    MODERATOR,
    EVIDENCE_REVIEWER,
    SYSTEM_ADMIN;

    val isStaff: Boolean
        get() = this in setOf(CASE_STAFF, CONTENT_EDITOR, MODERATOR, EVIDENCE_REVIEWER, SYSTEM_ADMIN)

    val canUseAi: Boolean
        get() = this in setOf(CONTENT_EDITOR, MODERATOR, SYSTEM_ADMIN)
}

enum class ManagedAccessRole(val wireValue: String, val label: String) {
    RESIDENT("RESIDENT", "Resident"),
    CASE_STAFF("CASE_STAFF", "Case Staff"),
    CONTENT_EDITOR("CONTENT_EDITOR", "Content Editor"),
    MODERATOR("MODERATOR", "Moderator"),
    EVIDENCE_REVIEWER("EVIDENCE_REVIEWER", "Evidence Reviewer"),
    SYSTEM_ADMIN("SYSTEM_ADMIN", "System Administrator");

    companion object {
        fun fromWire(value: String?): ManagedAccessRole? = entries.firstOrNull { it.wireValue == value }
    }
}

@Serializable
data class AccessManagedAccount(
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("effective_role") val effectiveRole: String? = null,
)

@Serializable
data class AccessRoleChangeRequest(
    val id: String,
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("target_email") val targetEmail: String,
    @SerialName("previous_role") val previousRole: String? = null,
    @SerialName("requested_role") val requestedRole: String,
    @SerialName("requested_by_email") val requestedByEmail: String,
    val reason: String,
    val state: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AccessRoleAuditEvent(
    val id: String,
    @SerialName("actor_email") val actorEmail: String,
    @SerialName("target_email") val targetEmail: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("previous_role") val previousRole: String? = null,
    @SerialName("new_role") val newRole: String? = null,
    val reason: String,
    @SerialName("occurred_at") val occurredAt: String,
)

data class AccessManagementUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

enum class AdminAnalyticsPeriod(val wireValue: String, val label: String) {
    TODAY("TODAY", "Today"),
    LAST_7_DAYS("7D", "7 days"),
    LAST_30_DAYS("30D", "30 days"),
    LAST_90_DAYS("90D", "90 days"),
    ALL_HISTORY("ALL", "All history");
}

enum class AdminAccountLookupPurpose(val wireValue: String, val label: String) {
    ACCOUNT_SUPPORT("ACCOUNT_SUPPORT", "Account support"),
    SECURITY_REVIEW("SECURITY_REVIEW", "Security review"),
    ROLE_ADMINISTRATION("ROLE_ADMINISTRATION", "Role administration"),
    DATA_SUBJECT_REQUEST("DATA_SUBJECT_REQUEST", "Data-subject request"),
    LEGAL_COMPLIANCE("LEGAL_COMPLIANCE", "Legal / compliance");
}

@Serializable
data class AdminAnalyticsMetric(
    val metric: String,
    val label: String,
    @SerialName("metric_value") val metricValue: Long,
)

data class AdminAnalyticsDashboard(
    val period: AdminAnalyticsPeriod = AdminAnalyticsPeriod.LAST_30_DAYS,
    val metrics: List<AdminAnalyticsMetric> = emptyList(),
) {
    fun value(metric: String): Long = metrics.firstOrNull { it.metric == metric }?.metricValue ?: 0L
}

@Serializable
data class AdminLocalitySummary(
    @SerialName("source_label") val sourceLabel: String,
    @SerialName("locality_label") val localityLabel: String,
    @SerialName("metric_value") val metricValue: Long,
)

@Serializable
data class AdminAccessManagementHistoryItem(
    @SerialName("event_type") val eventType: String,
    @SerialName("previous_role") val previousRole: String? = null,
    @SerialName("new_role") val newRole: String? = null,
    val reason: String,
    @SerialName("occurred_at") val occurredAt: String,
)

@Serializable
data class AdminAccountProfile(
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("effective_role") val effectiveRole: String? = null,
    @SerialName("signed_up_at") val signedUpAt: String,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerialName("declared_locality") val declaredLocality: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
    @SerialName("community_posts_last_24_months") val communityPostsLast24Months: Long = 0,
    @SerialName("community_comments_last_24_months") val communityCommentsLast24Months: Long = 0,
    @SerialName("support_requests_last_24_months") val supportRequestsLast24Months: Long = 0,
    @SerialName("directory_searches_last_24_months") val directorySearchesLast24Months: Long = 0,
    @SerialName("support_notifications") val supportNotifications: Boolean = true,
    @SerialName("community_notifications") val communityNotifications: Boolean = true,
    @SerialName("access_management_history") val accessManagementHistory: List<AdminAccessManagementHistoryItem> = emptyList(),
)

@Serializable
data class AdminAuditTrailEvent(
    val id: String,
    @SerialName("actor_email") val actorEmail: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("entity_type") val entityType: String,
    val result: String? = null,
    @SerialName("target_email") val targetEmail: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    val source: String? = null,
    val details: String? = null,
)

data class AdminAnalyticsUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

@Serializable
data class OperationsWorkItem(
    val id: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_id") val sourceId: String,
    val title: String,
    val description: String,
    val priority: String,
    val state: String,
    @SerialName("assigned_to_me") val assignedToMe: Boolean = false,
    @SerialName("is_unassigned") val isUnassigned: Boolean = true,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SystemHealthStatus(
    @SerialName("service_key") val serviceKey: String,
    val status: String,
    val category: String,
    @SerialName("affected_count") val affectedCount: Int = 0,
    @SerialName("last_successful_at") val lastSuccessfulAt: String? = null,
    val detail: String,
)

@Serializable
data class AdministrativeActivityEvent(
    val id: String,
    @SerialName("actor_email") val actorEmail: String,
    val category: String,
    @SerialName("event_type") val eventType: String,
    val outcome: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("target_label") val targetLabel: String? = null,
    val details: String? = null,
)

@Serializable
data class ModerationQueueItem(
    @SerialName("report_id") val reportId: String,
    @SerialName("post_id") val postId: String,
    @SerialName("reason_code") val reasonCode: String,
    @SerialName("report_detail") val reportDetail: String,
    @SerialName("report_state") val reportState: String,
    @SerialName("reported_at") val reportedAt: String,
    @SerialName("post_body") val postBody: String,
    @SerialName("post_state") val postState: String,
    @SerialName("report_count") val reportCount: Int = 0,
    @SerialName("author_display_name") val authorDisplayName: String,
    @SerialName("is_auto_limited") val isAutoLimited: Boolean = false,
)

@Serializable
data class ModerationAppeal(
    @SerialName("appeal_id") val appealId: String,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("subject_id") val subjectId: String,
    val reason: String,
    val state: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("post_body") val postBody: String? = null,
)

@Serializable
data class StaffWorkPreferences(
    @SerialName("queue_order") val queueOrder: List<String> = listOf("priority", "age"),
    @SerialName("assigned_work_notifications") val assignedWorkNotifications: Boolean = true,
    @SerialName("availability_status") val availabilityStatus: String = "available",
)

@Serializable
data class OperationalIncident(
    val id: String,
    val title: String,
    @SerialName("impact_summary") val impactSummary: String,
    val severity: String,
    val state: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("closing_summary") val closingSummary: String? = null,
)

@Serializable
data class OperationsControlState(
    @SerialName("maintenance_message") val maintenanceMessage: String? = null,
    @SerialName("maintenance_expires_at") val maintenanceExpiresAt: String? = null,
    @SerialName("community_paused") val communityPaused: Boolean = false,
    @SerialName("community_pause_expires_at") val communityPauseExpiresAt: String? = null,
)

@Serializable
data class EditorialNoticeRecord(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val status: String,
    @SerialName("safety_sensitive") val safetySensitive: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

data class OperationsUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

enum class MainDestination(val label: String) {
    HOME("Home"),
    COMMUNITY("Community"),
    EXPLORE("Explore"),
    SUPPORT("Support"),
    ACCOUNT("Account")
}

enum class NoticeStatus(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under review"),
    SCHEDULED("Scheduled"),
    PUBLISHED("Published"),
    NOT_PUBLISHED("Not published"),
    ARCHIVED("Archived")
}

enum class CaseStage(val label: String, val nextStep: String) {
    SUBMITTED("Submitted", "Your request has been received."),
    REVIEWING("Being reviewed", "A staff member is checking the details."),
    ACTION_PLANNED("Action planned", "The next action is being arranged."),
    RESOLVED("Resolved", "The request is complete. You can reopen it if needed.")
}

data class RtcSession(
    val id: String,
    val displayName: String,
    val handle: String,
    val role: UserRole,
    val onboardingComplete: Boolean = true,
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val readingMode: Boolean = false,
    val darkMode: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val supportNotifications: Boolean = true,
    val communityNotifications: Boolean = true,
    /** Optional resident-entered locality, distinct from any live device-location feature. */
    val declaredLocality: String? = null,
    val authority: SessionAuthority = SessionAuthority.PUBLIC,
    val authenticatedEmail: String? = null,
    val administratorMfaStatus: AdministratorMfaStatus = AdministratorMfaStatus.NOT_REQUIRED,
    val avatarUrl: String? = null,
)

@Serializable
data class DashboardMetrics(
    @SerialName("overall_project_progress") val overallProjectProgress: Double = 0.0,
    @SerialName("active_project_count") val activeProjectCount: Int = 0,
    @SerialName("centre_count") val centreCount: Int = 0,
    @SerialName("opportunity_count") val opportunityCount: Int = 0,
)

@Serializable
data class ProjectRecord(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    val details: String? = null,
    val sector: String,
    @SerialName("project_status") val projectStatus: String,
    @SerialName("budget_amount") val budgetAmount: Double? = null,
    @SerialName("budget_currency") val budgetCurrency: String = "ZAR",
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_as_of") val sourceAsOf: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CentreRecord(
    val id: String,
    val slug: String,
    val name: String,
    val summary: String,
    val category: String,
    val address: String? = null,
    val locality: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("opening_hours") val openingHours: String? = null,
    @SerialName("directions_url") val directionsUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class OpportunityRecord(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    @SerialName("opportunity_type") val opportunityType: String,
    val organisation: String? = null,
    val locality: String? = null,
    @SerialName("closing_at") val closingAt: String? = null,
    @SerialName("application_url") val applicationUrl: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PublicSearchResult(
    @SerialName("result_type") val resultType: String,
    @SerialName("result_id") val resultId: String,
    val title: String,
    val summary: String,
    val route: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val rank: Int = 99,
)

data class DirectoryPage<T>(
    val items: List<T> = emptyList(),
    val offset: Int = 0,
    val canLoadMore: Boolean = true,
)

enum class ThemePreference { LIGHT, DARK, SYSTEM }

enum class SessionAuthority { PUBLIC, DEVELOPMENT_ADAPTER, SUPABASE_AUTH }

enum class MediaKind { IMAGE, VIDEO }

enum class MediaTargetType { COMMUNITY_POST, PUBLIC_REPORT, PROJECT, CENTRE, OPPORTUNITY, NOTICE, ALERT }

enum class ModerationReason(val wireValue: String, val label: String) {
    SPAM("SPAM", "Spam"),
    HARMFUL_CONTENT("HARMFUL_CONTENT", "Harmful content"),
    PRIVACY_CONCERN("PRIVACY_CONCERN", "Privacy concern"),
    OTHER("OTHER", "Other"),
}

data class MediaItem(
    val id: String,
    val targetType: MediaTargetType,
    val targetId: String,
    val storagePath: String,
    val kind: MediaKind,
    val mimeType: String,
    val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Int? = null,
    val position: Int,
    val caption: String? = null,
    val signedUrl: String? = null,
)

data class MediaUploadCandidate(
    val uri: String,
    val kind: MediaKind,
    val mimeType: String,
    val displayName: String,
    val byteSize: Long,
    val durationSeconds: Int? = null,
)

enum class AdministratorMfaStatus {
    NOT_REQUIRED,
    ENROLLMENT_REQUIRED,
    VERIFICATION_REQUIRED,
    VERIFIED,
}

enum class CommunityAlertCategory(val wireValue: String, val label: String) {
    COMMUNITY_UPDATE("COMMUNITY_UPDATE", "Community update"),
    SERVICE_DISRUPTION("SERVICE_DISRUPTION", "Service disruption"),
    SAFETY_EMERGENCY("SAFETY_EMERGENCY", "Safety / emergency"),
    EVENT("EVENT", "Event"),
    OPPORTUNITY("OPPORTUNITY", "Opportunity");

    val isSafety: Boolean get() = this == SAFETY_EMERGENCY
}

enum class CommunityAlertState(val wireValue: String, val label: String) {
    ORIGINAL("ORIGINAL", "Original"),
    CORRECTION("CORRECTION", "Correction"),
    RETRACTION("RETRACTION", "Retraction"),
}

data class CommunityAlert(
    val id: String,
    val notificationId: String,
    val category: CommunityAlertCategory,
    val state: CommunityAlertState,
    val originalAlertId: String? = null,
    val title: String,
    val summary: String,
    val body: String,
    val linkedNoticeId: String? = null,
    val publishedAt: String? = null,
    val expiresAt: String,
    val createdAt: String,
    val readAt: String? = null,
) {
    val unread: Boolean get() = readAt == null
}

data class CommunityAlertDashboardItem(
    val id: String,
    val category: CommunityAlertCategory,
    val state: CommunityAlertState,
    val title: String,
    val summary: String,
    val status: String,
    val scheduledAt: String? = null,
    val publishedAt: String? = null,
    val expiresAt: String,
    val dispatchState: String,
    val intendedRecipients: Int,
    val eligibleDevices: Int,
    val fcmAccepted: Int,
    val fcmFailed: Int,
    val readCount: Int,
    val createdAt: String,
)

data class RtcNotification(
    val id: String,
    val title: String,
    val message: String,
    val createdAt: String,
    val unread: Boolean = true,
    val route: MainDestination = MainDestination.HOME,
    val alertId: String? = null,
)

data class SupportCase(
    val id: String,
    val title: String,
    val stage: CaseStage,
    val updatedAt: String,
    val actionRequired: Boolean = false,
    val category: String = "GENERAL",
    val priority: Int = 3,
    val locationLabel: String? = null,
)

data class SupportCaseMessage(
    val id: String,
    val caseId: String,
    val authorId: String,
    val body: String,
    val createdAt: String,
)

/**
 * Assignment-scoped case summary for the authenticated Case Staff member.
 * The server returns a resident ID for authorization purposes, but this client model
 * deliberately omits it and all message, attachment, and assignment-management data.
 */
data class AssignedSupportCase(
    val id: String,
    val title: String,
    val category: String,
    val state: String,
    val priority: Int,
    val locationLabel: String? = null,
    val updatedAt: String,
)

data class CommunityPost(
    val id: String,
    val author: String,
    val handle: String,
    val content: String,
    val category: String,
    val createdAt: String,
    val reactions: Int,
    val comments: Int,
    /** Server-projected state for the currently authenticated Community member only. */
    val viewerHasLiked: Boolean = false,
    val userReactions: Set<String> = emptySet(),
    val reactionCounts: Map<String, Int> = emptyMap(),
    val trendingScore: Int = 0,
    val isFollowedTopic: Boolean = false,
    val hasMedia: Boolean = false,
    val media: List<MediaItem> = emptyList(),
    val isOfficial: Boolean = false,
    val authorId: String = "",
    val authorAvatarUrl: String? = null,
    val isLocked: Boolean = false,
    val editedAt: String? = null,
    val repostOfId: String? = null,
    val quotePostId: String? = null,
    val repostCount: Int = 0,
    val bookmarkCount: Int = 0,
    val isRepostedByViewer: Boolean = false,
    val isBookmarkedByViewer: Boolean = false,
    val quotedPost: CommunityPost? = null,
    val isPendingSync: Boolean = false,
)

data class CommunityComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val author: String,
    val handle: String,
    val authorAvatarUrl: String? = null,
    val content: String,
    val createdAt: String,
    val editedAt: String? = null,
    val isStaff: Boolean = false,
    val parentId: String? = null,
    val replyCount: Int = 0,
    val depth: Int = 0,
)

data class OfficialNotice(
    val id: String,
    val title: String,
    val summary: String,
    val status: NoticeStatus,
    val publishedAt: String? = null,
    val scheduledFor: LocalDateTime? = null,
    val requiresSafetyReview: Boolean = false
)

data class HelpArticle(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    val body: String,
    val category: String,
    val publishedAt: String? = null,
)

data class WorkQueueItem(
    val id: String,
    val title: String,
    val description: String,
    val priority: String,
    val kind: String
)

enum class DraftArea {
    COMMUNITY,
    COMMUNITY_COMMENT,
    NOTICE,
    SUPPORT,
    STAFF_CONTENT,
    STAFF_MODERATION,
}

data class LocalDraft(
    val id: String,
    val area: DraftArea,
    val title: String = "",
    val body: String,
    val savedAt: String = "Just now",
)

data class AiProposal(
    val id: String,
    val summary: String,
    val affectedRecords: List<String>,
    val status: String,
    val createdAt: String
)

data class CommunitySearchCursor(
    val lastRank: Float,
    val lastId: String,
)

data class CommunityRealtimeNotification(
    val id: String,
    val recipientId: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: String,
)
