package za.org.rtc.community.supabase

import android.content.Context
import android.app.Application
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.ktor.http.ContentType
import io.ktor.client.statement.bodyAsText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.UploadData
import io.github.jan.supabase.storage.storage
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import za.org.rtc.community.BuildConfig
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import za.org.rtc.community.core.AccessManagedAccount
import za.org.rtc.community.core.AccessRoleAuditEvent
import za.org.rtc.community.core.AccessRoleChangeRequest
import za.org.rtc.community.core.AiProposal
import za.org.rtc.community.core.AdminAccountProfile
import za.org.rtc.community.core.AdminAnalyticsMetric
import za.org.rtc.community.core.AdminAuditTrailEvent
import za.org.rtc.community.core.AdminLocalitySummary
import za.org.rtc.community.core.AdministrativeActivityEvent
import za.org.rtc.community.core.AssignedSupportCase
import za.org.rtc.community.core.EditorialNoticeRecord
import za.org.rtc.community.core.ModerationAppeal
import za.org.rtc.community.core.ModerationQueueItem
import za.org.rtc.community.core.OperationsControlState
import za.org.rtc.community.core.OperationalIncident
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.core.SystemHealthStatus
import za.org.rtc.community.core.StaffWorkPreferences
import za.org.rtc.community.core.CentreRecord
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.core.SupportCaseMessage
import za.org.rtc.community.core.CaseStage
import za.org.rtc.community.core.HelpArticle
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.DashboardMetrics
import za.org.rtc.community.core.DirectoryPage
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.core.PublicSearchResult
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.core.MediaTargetType
import za.org.rtc.community.core.ModerationReason
import za.org.rtc.community.core.CommunityAlert
import za.org.rtc.community.core.CommunityAlertCategory
import za.org.rtc.community.core.CommunityAlertDashboardItem
import za.org.rtc.community.core.CommunityAlertState
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.data.local.MediaPreparation
import za.org.rtc.community.data.local.RtcDatabase
import za.org.rtc.community.data.local.UploadOutboxEntity

private val ASSIGNED_SUPPORT_CASE_ALLOWED_STATES = setOf(
    "IN_REVIEW",
    "IN_PROGRESS",
    "RESOLVED",
    "CLOSED",
)

@Serializable
private data class ProfileDisplayNamePayload(
    @SerialName("display_name") val displayName: String,
)

@Serializable
private data class ProfileDisplayNameRow(
    @SerialName("display_name") val displayName: String = "",
)

@Serializable
private data class ProfileDetailsPayload(
    @SerialName("user_id") val userId: String,
    val bio: String,
    val interests: List<String>,
)

@Serializable
private data class ProfileDetailsRow(
    val bio: String = "",
    val interests: List<String> = emptyList(),
)

data class PersistedOwnProfile(
    val displayName: String,
    val bio: String,
    val interests: List<String>,
)

@Serializable
private data class ExperiencePreferencesPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("reading_mode") val readingMode: Boolean,
    @SerialName("theme_preference") val themePreference: String,
)

@Serializable
private data class ExperiencePreferencesRow(
    @SerialName("reading_mode") val readingMode: Boolean = false,
    @SerialName("theme_preference") val themePreference: String = "system",
)

data class PersistedExperiencePreferences(
    val readingMode: Boolean,
    val themePreference: ThemePreference,
)

@Serializable
private data class SupportNotificationPreferencePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("support_notifications") val enabled: Boolean,
)

@Serializable
private data class SupportNotificationPreferenceRow(
    @SerialName("support_notifications") val enabled: Boolean = true,
)

@Serializable
private data class DeclaredLocalityPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("declared_locality") val declaredLocality: String? = null,
)

@Serializable
private data class DeclaredLocalityRow(
    @SerialName("declared_locality") val declaredLocality: String? = null,
)

@Serializable
data class DataExportRequestPayload(
    @SerialName("requester_id") val requesterId: String,
)

@Serializable
data class UserFeedbackPayload(
    @SerialName("reporter_id") val reporterId: String,
    val message: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("device_summary") val deviceSummary: String,
    @SerialName("attachment_object_path") val attachmentObjectPath: String? = null,
)

@Serializable
data class OfficialNoticeSubmissionPayload(
    val title: String,
    val body: String,
    val category: String = "Community Updates",
    val status: String = "submitted",
    @SerialName("safety_sensitive") val safetySensitive: Boolean = false,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class StaffWorkPreferencesPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("queue_order") val queueOrder: List<String>,
    @SerialName("assigned_work_notifications") val assignedWorkNotifications: Boolean,
    @SerialName("availability_status") val availabilityStatus: String,
)

@Serializable
data class AccessRoleMutationResult(
    val result: String,
    @SerialName("request_id") val requestId: String? = null,
    val message: String,
)

@Serializable
data class HelpArticleDto(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    val body: String,
    val category: String,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
private data class LiveOfficialNoticeRow(
    val id: String,
    val title: String,
    val body: String,
    val status: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("safety_sensitive") val safetySensitive: Boolean = false,
)

@Serializable
private data class CommunityPostFeedRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_handle") val authorHandle: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("avatar_updated_at") val avatarUpdatedAt: String? = null,
    @SerialName("staff_badge") val staffBadge: Boolean = false,
    val body: String,
    @SerialName("category_slug") val categorySlug: String? = null,
    @SerialName("category_label") val categoryLabel: String? = null,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("report_count") val reportCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("viewer_has_liked") val viewerHasLiked: Boolean = false,
    @SerialName("trending_score") val trendingScore: Int = 0,
    @SerialName("is_followed_topic") val isFollowedTopic: Boolean = false,
    val media: List<CommunityEmbeddedMediaRow> = emptyList(),
)

@Serializable
private data class CommunityPostLikeOutcome(
    val liked: Boolean,
    @SerialName("like_count") val likeCount: Int,
)

@Serializable
private data class CommunityEmbeddedMediaRow(
    val id: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val position: Int,
    val caption: String? = null,
)

@Serializable
private data class CommunityCommentFeedRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_handle") val authorHandle: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("avatar_updated_at") val avatarUpdatedAt: String? = null,
    @SerialName("staff_badge") val staffBadge: Boolean = false,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
)

@Serializable
private data class CommunityCommentCreatePayload(
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    val state: String = "PUBLISHED",
)

@Serializable
private data class CommunityCommentChangePayload(
    val body: String? = null,
    val state: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class CommunityMediaFeedRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val position: Int,
    val caption: String? = null,
)

@Serializable
private data class CommunityMediaCreatePayload(
    @SerialName("post_id") val postId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Long,
    val position: Int,
    val caption: String? = null,
    @SerialName("uploaded_by") val uploadedBy: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
)

@Serializable
private data class SupportCaseRow(
    val id: String,
    val title: String,
    val category: String,
    val state: String,
    val priority: Int,
    @SerialName("location_label") val locationLabel: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("action_required") val actionRequired: Boolean = false,
)

/** Deliberately omits the `resident_id` returned by the assignment-scoped RPC. */
@Serializable
private data class AssignedSupportCaseRow(
    val id: String,
    val title: String,
    val category: String,
    val state: String,
    val priority: Int,
    @SerialName("location_label") val locationLabel: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class SupportCaseMessageRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class FcmDeviceRegistrationPayload(
    @SerialName("p_fcm_token") val token: String,
    @SerialName("p_app_version") val appVersion: String? = null,
)

@Serializable
private data class CommunityAlertPreferencePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("ordinary_alerts_enabled") val ordinaryAlertsEnabled: Boolean,
)

@Serializable
private data class OrdinaryAlertPreferenceRow(
    @SerialName("ordinary_alerts_enabled") val enabled: Boolean = true,
)

@Serializable
private data class NotificationReadUpdate(
    @SerialName("read_at") val readAt: String,
)

@Serializable
private data class CommunityAlertInboxRow(
    @SerialName("notification_id") val notificationId: String,
    @SerialName("read_at") val readAt: String? = null,
    val id: String,
    val category: String,
    @SerialName("message_state") val messageState: String,
    @SerialName("original_alert_id") val originalAlertId: String? = null,
    val title: String,
    val summary: String,
    val body: String,
    @SerialName("linked_notice_id") val linkedNoticeId: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class CommunityAlertDashboardRow(
    val id: String,
    val category: String,
    @SerialName("message_state") val messageState: String,
    val title: String,
    val summary: String,
    val status: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("dispatch_state") val dispatchState: String,
    @SerialName("intended_recipient_count") val intendedRecipientCount: Int,
    @SerialName("eligible_device_count") val eligibleDeviceCount: Int,
    @SerialName("fcm_accepted_count") val fcmAcceptedCount: Int,
    @SerialName("fcm_failed_count") val fcmFailedCount: Int,
    @SerialName("read_count") val readCount: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class CommunityMediaUrlResponse(
    val url: String,
    @SerialName("expiresAt") val expiresAt: String,
)

@Serializable
private data class AiProposalChangeResponse(
    @SerialName("entityType") val entityType: String,
    @SerialName("entityId") val entityId: String? = null,
    val operation: String,
    val changes: JsonObject = JsonObject(emptyMap()),
    val reason: String,
)

@Serializable
private data class AiProposalBodyResponse(
    val kind: String,
    val summary: String,
    val changes: List<AiProposalChangeResponse> = emptyList(),
)

@Serializable
private data class AiCommandResponse(
    @SerialName("proposalId") val proposalId: String,
    val proposal: AiProposalBodyResponse,
    @SerialName("expiresAt") val expiresAt: String,
)

@Serializable
private data class AiConfirmResponse(
    @SerialName("draftsCreated") val draftsCreated: Int = 0,
)

@Serializable
private data class ProfileAvatarUpdate(
    @SerialName("avatar_url") val avatarUrl: String?,
)

@Serializable
private data class ProfileAvatarRow(
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * All requests run through the authenticated Supabase client; the database remains the
 * enforcement boundary through RLS. This class never carries a service-role or Gemini secret.
 */
@Singleton
class ProductionUxRepository @Inject constructor(
    private val supabase: SupabaseClient,
    @param:ApplicationContext private val applicationContext: Context,
    private val database: RtcDatabase,
    private val mediaPreparation: MediaPreparation,
 ) {
    private val json = Json { ignoreUnknownKeys = true }
    private val recaptchaMutex = Mutex()
    @Volatile private var recaptchaClient: RecaptchaClient? = null

    private suspend fun recaptchaToken(action: String): String {
        val client = recaptchaMutex.withLock {
            recaptchaClient ?: Recaptcha.fetchClient(
                applicationContext.applicationContext as Application,
                BuildConfig.RECAPTCHA_SITE_KEY,
            ).also { recaptchaClient = it }
        }
        return client.execute(RecaptchaAction.custom(action), timeout = 10_000L).getOrThrow()
    }

    private fun authenticatedUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("A signed-in session is required.")

    /** Used only by local recovery workers to scope durable rows to the active real session. */
    fun currentAuthenticatedUserIdOrNull(): String? = supabase.auth.currentUserOrNull()?.id

    suspend fun ownExperiencePreferences(): Result<PersistedExperiencePreferences> = runCatching {
        val userId = authenticatedUserId()
        val stored = supabase.from("account_preferences").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<ExperiencePreferencesRow>().firstOrNull() ?: ExperiencePreferencesRow()
        PersistedExperiencePreferences(
            readingMode = stored.readingMode,
            themePreference = runCatching { ThemePreference.valueOf(stored.themePreference.uppercase()) }.getOrDefault(ThemePreference.SYSTEM),
        )
    }

    suspend fun saveOwnExperiencePreferences(readingMode: Boolean, themePreference: ThemePreference): Result<PersistedExperiencePreferences> = runCatching {
        val userId = authenticatedUserId()
        supabase.from("account_preferences").upsert(
            ExperiencePreferencesPayload(
                userId = userId,
                readingMode = readingMode,
                themePreference = themePreference.name.lowercase(),
            )
        )
        val persisted = ownExperiencePreferences().getOrElse { throw it }
        check(persisted.readingMode == readingMode && persisted.themePreference == themePreference) {
            "The experience preferences could not be confirmed."
        }
        persisted
    }

    suspend fun ownSupportNotificationPreference(): Result<Boolean> = runCatching {
        val userId = authenticatedUserId()
        supabase.from("account_preferences").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<SupportNotificationPreferenceRow>().firstOrNull()?.enabled ?: true
    }

    suspend fun saveOwnSupportNotificationPreference(enabled: Boolean): Result<Boolean> = runCatching {
        val userId = authenticatedUserId()
        supabase.from("account_preferences").upsert(
            SupportNotificationPreferencePayload(userId = userId, enabled = enabled)
        )
        ownSupportNotificationPreference().getOrElse { throw it }
    }

    suspend fun ownPersistedProfile(): Result<PersistedOwnProfile> = runCatching {
        val userId = authenticatedUserId()
        val displayName = supabase.from("profiles").select {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<ProfileDisplayNameRow>().firstOrNull()?.displayName?.trim()?.takeIf(String::isNotBlank)
            ?: error("The profile display name could not be loaded.")
        val details = supabase.from("account_preferences").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<ProfileDetailsRow>().firstOrNull() ?: ProfileDetailsRow()
        PersistedOwnProfile(displayName, details.bio, details.interests)
    }

    suspend fun saveOwnProfile(
        displayName: String,
        bio: String,
        interests: List<String>,
    ): Result<PersistedOwnProfile> = runCatching {
        val userId = authenticatedUserId()
        val cleanName = displayName.trim()
        val cleanBio = bio.trim()
        val cleanInterests = interests.map(String::trim).filter(String::isNotBlank).distinct().take(MAX_PROFILE_INTERESTS)
        require(cleanName.length in 2..120) { "Enter a display name between 2 and 120 characters." }
        require(cleanBio.length <= MAX_PROFILE_BIO_LENGTH) { "Keep the bio to 600 characters or fewer." }
        supabase.from("profiles").update(ProfileDisplayNamePayload(cleanName)) {
            filter { eq("id", userId) }
        }
        supabase.from("account_preferences").upsert(
            ProfileDetailsPayload(userId = userId, bio = cleanBio, interests = cleanInterests)
        )
        val persisted = ownPersistedProfile().getOrElse { throw it }
        check(persisted.displayName == cleanName && persisted.bio == cleanBio && persisted.interests == cleanInterests) {
            "The profile changes could not be confirmed."
        }
        persisted
    }

    suspend fun ownDeclaredLocality(): Result<String?> = runCatching {
        val userId = authenticatedUserId()
        supabase.from("account_preferences").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<DeclaredLocalityRow>().firstOrNull()?.declaredLocality
    }

    suspend fun saveOwnDeclaredLocality(locality: String?): Result<Unit> = runCatching {
        val clean = locality?.trim()?.takeIf(String::isNotBlank)
        require(clean == null || clean.length in 2..120) { "Enter between 2 and 120 characters, or clear the field." }
        supabase.from("account_preferences").upsert(
            DeclaredLocalityPayload(userId = authenticatedUserId(), declaredLocality = clean)
        )
    }

    suspend fun requestMachineReadableExport(): Result<Unit> = runCatching {
        supabase.from("data_export_requests").insert(
            DataExportRequestPayload(requesterId = authenticatedUserId())
        )
    }

    /**
     * The server validates that the account signed in again recently and records an audit event.
     * The client can request deletion but never deletes Auth, Storage, or database records directly.
     */
    suspend fun submitAuthenticatedDeletionRequest(): Result<Unit> = runCatching {
        val response = supabase.functions.invoke(
            "rtc-privacy-requests",
            buildJsonObject { put("operation", "request_account_deletion") }
        )
        check(response.status.value == 202) { "Deletion request could not be submitted." }
    }

    suspend fun submitFeedback(
        message: String,
        appVersion: String,
        deviceSummary: String,
        attachmentObjectPath: String? = null,
    ): Result<Unit> = runCatching {
        supabase.from("user_feedback").insert(
            UserFeedbackPayload(
                reporterId = authenticatedUserId(),
                message = message.trim(),
                appVersion = appVersion,
                deviceSummary = deviceSummary,
                attachmentObjectPath = attachmentObjectPath,
            )
        )
    }

    suspend fun submitResidentNotice(
        title: String,
        body: String,
        category: String,
        safetySensitive: Boolean,
    ): Result<Unit> = runCatching {
        supabase.from("official_notices").insert(
            OfficialNoticeSubmissionPayload(
                title = title.trim(),
                body = body.trim(),
                category = category,
                safetySensitive = safetySensitive,
                createdBy = authenticatedUserId(),
            )
        )
    }

    suspend fun workPreferences(): Result<StaffWorkPreferences> = runCatching {
        supabase.from("staff_work_preferences").select {
            filter { eq("user_id", authenticatedUserId()) }
            limit(1)
        }.decodeList<StaffWorkPreferences>().firstOrNull() ?: StaffWorkPreferences()
    }

    suspend fun uploadFeedbackScreenshot(uri: Uri): Result<String> = runCatching {
        val userId = authenticatedUserId()
        val mimeType = applicationContext.contentResolver.getType(uri)?.lowercase()
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("Choose a JPEG, PNG, or WebP screenshot.")
        }
        queryContentSize(uri)?.let { size ->
            require(size in 1..MAX_FEEDBACK_SCREENSHOT_BYTES) { "The screenshot must be no larger than 5 MB." }
        }
        val staged = copyContentToBoundedFile(uri, ".${extension}", MAX_FEEDBACK_SCREENSHOT_BYTES)
        try {
            val path = "$userId/${UUID.randomUUID()}.$extension"
            supabase.storage.from(FEEDBACK_MEDIA_BUCKET).upload(
                path,
                UploadData(staged.inputStream().toByteReadChannel(), staged.length()),
            ) {
                contentType = ContentType.parse(mimeType)
            }
            path
        } finally {
            staged.delete()
        }
    }

    suspend fun deleteFeedbackScreenshot(path: String): Result<Unit> = runCatching {
        require(path.startsWith("${authenticatedUserId()}/")) { "A user may remove only their own feedback attachment." }
        supabase.storage.from(FEEDBACK_MEDIA_BUCKET).delete(path)
    }

    suspend fun saveWorkPreferences(
        queueOrder: List<String>,
        assignedWorkNotifications: Boolean,
        availabilityStatus: String,
    ): Result<Unit> = runCatching {
        supabase.from("staff_work_preferences").upsert(
            StaffWorkPreferencesPayload(
                userId = authenticatedUserId(),
                queueOrder = queueOrder,
                assignedWorkNotifications = assignedWorkNotifications,
                availabilityStatus = availabilityStatus,
            )
        )
    }

    suspend fun submitSupportCase(title: String, description: String, category: String = "GENERAL", priority: Int = 3, locationLabel: String? = null): Result<String> = runCatching {
        supabase.postgrest.rpc(
            "submit_support_case",
            buildJsonObject {
                put("p_title", title.trim())
                put("p_description", description.trim())
                put("p_category", category.trim().uppercase())
                put("p_priority", priority.coerceIn(1, 5))
                locationLabel?.trim()?.takeIf(String::isNotBlank)?.let { put("p_location_label", it) }
                put("p_details", buildJsonObject { })
            },
        ).decodeSingle<String>()
    }

    suspend fun mySupportCases(): Result<List<SupportCase>> = runCatching {
        supabase.postgrest.rpc("list_my_support_cases").decodeList<SupportCaseRow>().map { it.toSupportCase() }
    }

    /**
     * The server restricts this RPC to CASE_STAFF and returns only cases assigned to the caller.
     * The response also contains resident_id, which is intentionally not decoded or retained here.
     */
    suspend fun listAssignedSupportCases(): Result<List<AssignedSupportCase>> = runCatching {
        supabase.postgrest.rpc("list_assigned_support_cases")
            .decodeList<AssignedSupportCaseRow>()
            .map { row ->
                AssignedSupportCase(
                    id = row.id,
                    title = row.title,
                    category = row.category,
                    state = row.state,
                    priority = row.priority,
                    locationLabel = row.locationLabel,
                    updatedAt = row.updatedAt,
                )
            }
    }

    suspend fun updateAssignedSupportCaseState(caseId: String, state: String, note: String): Result<Unit> = runCatching {
        val cleanCaseId = caseId.trim()
        val cleanState = state.trim().uppercase()
        val cleanNote = note.trim()
        require(cleanCaseId.isNotEmpty()) { "Choose an assigned support case." }
        require(cleanState in ASSIGNED_SUPPORT_CASE_ALLOWED_STATES) { "Choose a valid staff case state." }
        require(cleanNote.length in 3..1000) { "Provide a case update note between 3 and 1000 characters." }
        supabase.postgrest.rpc(
            "update_assigned_support_case_state",
            buildJsonObject {
                put("p_case_id", cleanCaseId)
                put("p_state", cleanState)
                put("p_note", cleanNote)
            },
        )
    }

    suspend fun supportCaseMessages(caseId: String): Result<List<SupportCaseMessage>> = runCatching {
        supabase.postgrest.rpc(
            "list_support_case_messages",
            buildJsonObject { put("p_case_id", caseId) },
        ).decodeList<SupportCaseMessageRow>().map { row ->
            SupportCaseMessage(row.id, caseId, row.authorId, row.body, row.createdAt)
        }
    }

    suspend fun addSupportCaseMessage(caseId: String, body: String): Result<String> = runCatching {
        supabase.postgrest.rpc(
            "add_support_case_message",
            buildJsonObject { put("p_case_id", caseId); put("p_body", body.trim()); put("p_attachments", buildJsonArray { }) },
        ).decodeSingle<String>()
    }

    private fun SupportCaseRow.toSupportCase(): SupportCase = SupportCase(
        id = id,
        title = title,
        stage = when (state.uppercase()) {
            "OPEN" -> CaseStage.SUBMITTED
            "IN_REVIEW" -> CaseStage.REVIEWING
            "IN_PROGRESS" -> CaseStage.ACTION_PLANNED
            "RESOLVED", "CLOSED" -> CaseStage.RESOLVED
            else -> CaseStage.REVIEWING
        },
        updatedAt = updatedAt,
        actionRequired = actionRequired,
        category = category,
        priority = priority,
        locationLabel = locationLabel,
    )

    suspend fun publishedOfficialNotices(): Result<List<OfficialNotice>> = runCatching {
        supabase.from("official_notices").select {
            order(column = "published_at", order = Order.DESCENDING)
        }.decodeList<LiveOfficialNoticeRow>()
            .filter { it.status.equals("published", ignoreCase = true) }
            .map { row ->
                OfficialNotice(
                    id = row.id,
                    title = row.title,
                    summary = row.body,
                    status = NoticeStatus.PUBLISHED,
                    publishedAt = row.publishedAt,
                    requiresSafetyReview = row.safetySensitive,
                )
            }
    }

    suspend fun publishedHelpArticles(): Result<List<HelpArticle>> = runCatching {
        supabase.from("help_articles").select {
            order(column = "published_at", order = Order.DESCENDING)
        }.decodeList<HelpArticleDto>()
            .filter { it.publishedAt != null }
            .map { row -> HelpArticle(row.id, row.slug, row.title, row.summary, row.body, row.category, row.publishedAt) }
    }

    suspend fun publishedCommunityPosts(): Result<List<CommunityPost>> = runCatching {
        val rows = supabase.from("community_post_feed").select {
            order(column = "created_at", order = Order.DESCENDING)
        }.decodeList<CommunityPostFeedRow>()
        coroutineScope { rows.map { row -> async { toCommunityPost(row) } }.awaitAll() }
    }

    suspend fun communityPost(postId: String): Result<CommunityPost?> = runCatching {
        val row = supabase.from("community_post_feed").select {
            filter { eq("id", postId) }
            limit(1)
        }.decodeList<CommunityPostFeedRow>().firstOrNull()
        row?.let { toCommunityPost(it) }
    }

    suspend fun communityComments(postId: String): Result<List<CommunityComment>> = runCatching {
        val rows = supabase.from("community_comment_feed").select {
            filter { eq("post_id", postId) }
            order(column = "created_at", order = Order.ASCENDING)
        }.decodeList<CommunityCommentFeedRow>()
        coroutineScope { rows.map { row -> async { toCommunityComment(row) } }.awaitAll() }
    }

    suspend fun communityMedia(postId: String): Result<List<MediaItem>> = runCatching {
        val rows = supabase.from("community_media_feed").select {
            filter { eq("post_id", postId) }
            order(column = "position", order = Order.ASCENDING)
        }.decodeList<CommunityMediaFeedRow>()
        coroutineScope { rows.map { row -> async { toCommunityMedia(row) } }.awaitAll() }
    }

    @SuppressLint("UnsafeOptInUsageError")
    suspend fun createCommunityPost(text: String, mediaUris: List<Uri> = emptyList()): Result<String> = runCatching {
        val cleanText = text.trim()
        require(cleanText.length <= 280) { "A Community post must contain at most 280 characters." }
        require(mediaUris.size <= MAX_MEDIA_PER_POST) { "A Community post can contain at most 10 photos or videos." }
        require(cleanText.isNotBlank() || mediaUris.isNotEmpty()) { "Add text or at least one photo or video before publishing." }
        val authorId = authenticatedUserId()
        val draftId = supabase.postgrest.rpc(
            "create_community_post_draft",
            buildJsonObject { put("p_body", cleanText) },
        ).decodeSingle<String>()
        try {
            mediaUris.forEachIndexed { index, uri ->
                val prepared = mediaPreparation.prepare(uri)
                val extension = when (prepared.mimeType) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "video/webm" -> "webm"
                    else -> "mp4"
                }
                val storagePath = "$authorId/$draftId/${index + 1}.$extension"
                database.uploadOutboxDao().upsert(
                    UploadOutboxEntity(
                        id = "$draftId:${index + 1}",
                        ownerUserId = authorId,
                        draftId = draftId,
                        sourceUri = uri.toString(),
                        stagedPath = prepared.file.absolutePath,
                        storagePath = storagePath,
                        mediaKind = prepared.kind.name,
                        mimeType = prepared.mimeType,
                        byteSize = prepared.byteSize,
                        width = prepared.width,
                        height = prepared.height,
                        durationSeconds = prepared.durationSeconds,
                        position = index + 1,
                        state = "PENDING",
                        progress = 0,
                        attemptCount = 0,
                        lastError = null,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
            resumeCommunityUpload(draftId, allowEmptyMedia = mediaUris.isEmpty()).getOrThrow()
            draftId
        } catch (failure: Throwable) {
            database.uploadOutboxDao().forDraftForOwner(draftId, authorId).forEach { row ->
                val current = row.copy(state = "RETRY", attemptCount = row.attemptCount + 1, lastError = failure.message?.take(500), updatedAtEpochMillis = System.currentTimeMillis())
                database.uploadOutboxDao().upsert(current)
            }
            throw failure
        }
    }

    /**
     * Background recovery never permits an empty outbox. The initial author flow explicitly
     * permits it only for a legitimate text-only draft, which still finalizes server-side.
     */
    suspend fun resumeCommunityUpload(draftId: String, allowEmptyMedia: Boolean = false): Result<Unit> = runCatching {
        val ownerUserId = authenticatedUserId()
        val dao = database.uploadOutboxDao()
        val bucket = supabase.storage.from(COMMUNITY_MEDIA_BUCKET)
        val rows = dao.forDraftForOwner(draftId, ownerUserId)
        check(rows.isNotEmpty() || allowEmptyMedia) { "No resumable media belongs to the signed-in account." }
        rows.forEach { row ->
            val path = row.storagePath ?: error("Upload storage path is missing.")
            if (row.state != "DONE") {
                dao.upsert(row.copy(state = "UPLOADING", progress = 5, updatedAtEpochMillis = System.currentTimeMillis()))
                if (!bucket.exists(path)) {
                    val file = row.stagedPath?.let(::File)?.takeIf(File::exists)
                        ?: error("Prepared media is no longer available on this device.")
                    bucket.upload(
                        path,
                        UploadData(file.inputStream().toByteReadChannel(), file.length()),
                    ) {
                        upsert = false
                        contentType = ContentType.parse(row.mimeType)
                    }
                }
                dao.upsert(row.copy(state = "DONE", progress = 100, updatedAtEpochMillis = System.currentTimeMillis()))
            }
        }
        val finalRows = dao.forDraftForOwner(draftId, ownerUserId)
        check(finalRows.all { it.state == "DONE" }) { "Some media is still waiting to upload." }
        supabase.postgrest.rpc(
            "finalize_community_post",
            buildJsonObject {
                put("p_post_id", draftId)
                put("p_media", buildJsonArray {
                    finalRows.sortedBy { it.position }.forEach { row ->
                        add(buildJsonObject {
                            put("path", row.storagePath ?: error("Storage path missing"))
                            put("mediaKind", row.mediaKind)
                            row.width?.let { put("width", it) }
                            row.height?.let { put("height", it) }
                            row.durationSeconds?.let { put("durationSeconds", it) }
                        })
                    }
                })
                put("p_hashtags", buildJsonArray { })
            },
        )
        finalRows.forEach { it.stagedPath?.let(::File)?.delete() }
        dao.deleteDraftForOwner(draftId, ownerUserId)
    }

    suspend fun toggleCommunityPostLike(postId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "toggle_community_post_like",
            buildJsonObject { put("p_post_id", postId) },
        ).decodeSingle<CommunityPostLikeOutcome>()
        Unit
    }

    suspend fun refreshCommunityMediaUrl(mediaId: String): Result<String> = runCatching {
        val response = supabase.functions.invoke(
            "community-media-url",
            buildJsonObject { put("mediaId", mediaId) },
        )
        check(response.status.value in 200..299) { "Community media could not be refreshed." }
        json.decodeFromString<CommunityMediaUrlResponse>(response.bodyAsText()).url
    }

    suspend fun createAiProposal(command: String): Result<AiProposal> = runCatching {
        require(command.trim().isNotEmpty()) { "Describe the proposal you need." }
        val response = supabase.functions.invoke(
            "rtc-admin-ai",
            buildJsonObject {
                put("operation", "command")
                put("command", command.trim())
                put("recaptchaToken", recaptchaToken("rtc_admin_ai_command"))
            },
        )
        check(response.status.value in 200..299) { "RTC AI could not prepare a proposal." }
        val result = json.decodeFromString<AiCommandResponse>(response.bodyAsText())
        AiProposal(
            id = result.proposalId,
            summary = result.proposal.summary,
            affectedRecords = result.proposal.changes.map { change ->
                buildString {
                    append(change.operation.replace('_', ' '))
                    append(" · ")
                    append(change.entityType.replace('_', ' '))
                    change.entityId?.let { append(" · "); append(it) }
                }
            }.ifEmpty { listOf("Read-only response; no production record will be changed.") },
            status = if (result.proposal.kind == "PROPOSAL") "Awaiting review" else "Read-only completed",
            createdAt = Instant.now().toString(),
        )
    }

    suspend fun confirmAiProposal(proposalId: String): Result<Int> = runCatching {
        val response = supabase.functions.invoke(
            "rtc-admin-ai",
            buildJsonObject {
                put("operation", "confirm")
                put("proposalId", proposalId)
                put("recaptchaToken", recaptchaToken("rtc_admin_ai_confirm"))
            },
        )
        check(response.status.value in 200..299) { "RTC AI proposal confirmation failed. No content was changed." }
        json.decodeFromString<AiConfirmResponse>(response.bodyAsText()).draftsCreated
    }

    suspend fun reportCommunityPost(postId: String, reason: ModerationReason, detail: String): Result<Unit> = runCatching {
        val response = supabase.functions.invoke(
            "report-community-post",
            buildJsonObject {
                put("postId", postId)
                put("reasonCode", reason.wireValue)
                put("detail", detail.trim())
            }
        )
        check(response.status.value in 200..299) { "The report could not be sent. Please try again." }
    }

    suspend fun registerFcmDevice(token: String, appVersion: String?): Result<Unit> = runCatching {
        require(token.length in 20..512) { "The device notification token is not valid." }
        supabase.postgrest.rpc(
            "register_fcm_device",
            buildJsonObject {
                put("p_fcm_token", token)
                appVersion?.let { put("p_app_version", it) }
            }
        )
    }

    suspend fun communityAlertInbox(): Result<List<CommunityAlert>> = runCatching {
        supabase.from("community_alert_inbox").select {
            order(column = "inbox_created_at", order = Order.DESCENDING)
        }.decodeList<CommunityAlertInboxRow>().map(::toCommunityAlert)
    }

    suspend fun communityAlert(alertId: String): Result<CommunityAlert?> = runCatching {
        supabase.from("community_alert_inbox").select {
            filter { eq("id", alertId) }
            limit(1)
        }.decodeList<CommunityAlertInboxRow>().firstOrNull()?.let(::toCommunityAlert)
    }

    suspend fun markCommunityAlertRead(notificationId: String): Result<Unit> = runCatching {
        supabase.from("notification_events").update(NotificationReadUpdate(Instant.now().toString())) {
            filter { eq("id", notificationId) }
        }
    }

    suspend fun ownOrdinaryAlertPreference(): Result<Boolean> = runCatching {
        val userId = authenticatedUserId()
        supabase.from("community_alert_preferences").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<OrdinaryAlertPreferenceRow>().firstOrNull()?.enabled ?: true
    }

    suspend fun saveOrdinaryAlertPreference(enabled: Boolean): Result<Boolean> = runCatching {
        supabase.from("community_alert_preferences").upsert(
            CommunityAlertPreferencePayload(authenticatedUserId(), enabled)
        )
        ownOrdinaryAlertPreference().getOrElse { throw it }
    }

    suspend fun communityAlertDashboard(): Result<List<CommunityAlertDashboardItem>> = runCatching {
        supabase.postgrest.rpc("get_community_alert_dashboard")
            .decodeList<CommunityAlertDashboardRow>()
            .map(::toCommunityAlertDashboardItem)
    }

    suspend fun createCommunityAlert(
        category: CommunityAlertCategory,
        title: String,
        summary: String,
        body: String,
        scheduledAt: String? = null,
        expiresAt: String? = null,
        linkedNoticeId: String? = null,
        state: CommunityAlertState = CommunityAlertState.ORIGINAL,
        originalAlertId: String? = null,
        correctionReason: String? = null,
        publishConfirmation: String,
        emergencyConfirmation: String? = null,
        emergencyReason: String? = null,
    ): Result<String> = runCatching {
        require(title.trim().length in 3..120) { "An alert title must contain 3 to 120 characters." }
        require(summary.trim().length in 3..600) { "An alert summary must contain 3 to 600 characters." }
        require(body.trim().length in 3..12000) { "An alert detail must contain 3 to 12,000 characters." }
        supabase.postgrest.rpc(
            "create_confirmed_community_alert",
            buildJsonObject {
                put("p_confirmation", publishConfirmation.trim())
                put("p_category", category.wireValue)
                put("p_title", title.trim())
                put("p_summary", summary.trim())
                put("p_body", body.trim())
                scheduledAt?.let { put("p_scheduled_at", it) }
                expiresAt?.let { put("p_expires_at", it) }
                linkedNoticeId?.let { put("p_linked_notice_id", it) }
                put("p_message_state", state.wireValue)
                originalAlertId?.let { put("p_original_alert_id", it) }
                correctionReason?.let { put("p_correction_reason", it) }
                emergencyConfirmation?.let { put("p_emergency_confirmation", it) }
                emergencyReason?.let { put("p_emergency_reason", it) }
            }
        ).decodeSingle<String>()
    }

    suspend fun searchVerifiedAccessAccount(email: String): Result<AccessManagedAccount?> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.contains('@')) { "Enter a complete email address." }
        supabase.postgrest.rpc(
            "access_search_verified_account",
            buildJsonObject { put("email_query", normalizedEmail) },
        ).decodeList<AccessManagedAccount>().firstOrNull()
    }

    suspend fun saveAccessRoleAssignment(
        targetUserId: String,
        requestedRole: String,
        reason: String,
    ): Result<AccessRoleMutationResult> = runCatching {
        supabase.postgrest.rpc(
            "access_save_role_assignment",
            buildJsonObject {
                put("target_user", targetUserId)
                put("requested_role", requestedRole)
                put("change_reason", reason.trim())
            },
        ).decodeSingle<AccessRoleMutationResult>()
    }

    suspend fun decideAccessRoleChangeRequest(
        requestId: String,
        approve: Boolean,
        decisionReason: String? = null,
    ): Result<AccessRoleMutationResult> = runCatching {
        supabase.postgrest.rpc(
            "access_decide_role_change_request",
            buildJsonObject {
                put("request", requestId)
                put("approve", approve)
                decisionReason?.trim()?.takeIf(String::isNotEmpty)?.let { put("decision_reason", it) }
            },
        ).decodeSingle<AccessRoleMutationResult>()
    }

    suspend fun accessRoleChangeRequests(): Result<List<AccessRoleChangeRequest>> = runCatching {
        supabase.postgrest.rpc("access_list_role_change_requests")
            .decodeList<AccessRoleChangeRequest>()
    }

    suspend fun accessRoleAuditEvents(limit: Int = 100): Result<List<AccessRoleAuditEvent>> = runCatching {
        supabase.postgrest.rpc(
            "access_list_role_audit_events",
            buildJsonObject { put("maximum_rows", limit.coerceIn(1, 200)) },
        ).decodeList<AccessRoleAuditEvent>()
    }

    /** Only the two privacy-approved non-content event categories may be recorded. */
    suspend fun recordPrivacyAnalyticsActivity(eventType: String): Result<Unit> = runCatching {
        require(eventType in setOf("APP_ACTIVE", "DIRECTORY_SEARCH")) { "Unsupported activity signal." }
        supabase.postgrest.rpc(
            "record_privacy_analytics_activity",
            buildJsonObject { put("p_event_type", eventType) },
        )
    }

    suspend fun adminAnalyticsMetrics(period: String): Result<List<AdminAnalyticsMetric>> = runCatching {
        supabase.postgrest.rpc(
            "admin_privacy_analytics_dashboard",
            buildJsonObject { put("p_period", period) },
        ).decodeList<AdminAnalyticsMetric>()
    }

    suspend fun adminAnalyticsLocalities(period: String): Result<List<AdminLocalitySummary>> = runCatching {
        supabase.postgrest.rpc(
            "admin_privacy_analytics_location_summary",
            buildJsonObject { put("p_period", period) },
        ).decodeList<AdminLocalitySummary>()
    }

    suspend fun adminAnalyticsExactAccountLookup(
        email: String,
        purpose: String,
        explanation: String,
    ): Result<AdminAccountProfile> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.contains('@')) { "Enter a complete verified email address." }
        require(explanation.trim().length in 3..500) { "Provide an explanation between 3 and 500 characters." }
        supabase.postgrest.rpc(
            "admin_privacy_exact_account_lookup",
            buildJsonObject {
                put("p_email", normalizedEmail)
                put("p_purpose", purpose)
                put("p_explanation", explanation.trim())
            },
        ).decodeSingle<AdminAccountProfile>()
    }

    suspend fun adminAnalyticsAuditTrail(limit: Int = 200): Result<List<AdminAuditTrailEvent>> = runCatching {
        supabase.postgrest.rpc(
            "admin_privacy_analytics_audit_events",
            buildJsonObject { put("maximum_rows", limit.coerceIn(1, 500)) },
        ).decodeList<AdminAuditTrailEvent>()
    }

    suspend fun operationsWorkQueue(): Result<List<OperationsWorkItem>> = runCatching {
        supabase.postgrest.rpc("ops_list_work_queue").decodeList<OperationsWorkItem>()
    }

    suspend fun claimOperationsWorkItem(workItemId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("ops_claim_work_item", buildJsonObject { put("p_work_item_id", workItemId) })
    }

    suspend fun releaseOperationsWorkItem(workItemId: String, reason: String): Result<Unit> = runCatching {
        require(reason.trim().length in 3..500) { "Provide a release reason between 3 and 500 characters." }
        supabase.postgrest.rpc("ops_release_work_item", buildJsonObject {
            put("p_work_item_id", workItemId)
            put("p_reason", reason.trim())
        })
    }

    suspend fun markOperationsWorkReadyForReview(workItemId: String, note: String): Result<Unit> = runCatching {
        require(note.trim().length in 3..1000) { "Provide a review note between 3 and 1,000 characters." }
        supabase.postgrest.rpc("ops_mark_work_ready_for_review", buildJsonObject {
            put("p_work_item_id", workItemId)
            put("p_note", note.trim())
        })
    }

    suspend fun reassignOperationsWorkItem(workItemId: String, ownerId: String, reason: String): Result<Unit> = runCatching {
        require(reason.trim().length in 3..500) { "Provide a reassignment reason between 3 and 500 characters." }
        supabase.postgrest.rpc("ops_reassign_work_item", buildJsonObject {
            put("p_work_item_id", workItemId)
            put("p_new_owner", ownerId)
            put("p_reason", reason.trim())
        })
    }

    suspend fun activeOperationsControls(): Result<OperationsControlState> = runCatching {
        supabase.postgrest.rpc("ops_get_active_community_controls").decodeSingle<OperationsControlState>()
    }

    suspend fun systemHealth(): Result<List<SystemHealthStatus>> = runCatching {
        supabase.postgrest.rpc("ops_list_system_health").decodeList<SystemHealthStatus>()
    }

    suspend fun administrativeActivity(
        category: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<AdministrativeActivityEvent>> = runCatching {
        supabase.postgrest.rpc("ops_list_administrative_activity", buildJsonObject {
            put("p_limit", limit.coerceIn(1, 200))
            put("p_offset", offset.coerceAtLeast(0))
            category?.takeIf { it.isNotBlank() }?.let { put("p_category", it.trim().uppercase()) }
        }).decodeList<AdministrativeActivityEvent>()
    }

    suspend fun operationalIncidents(): Result<List<OperationalIncident>> = runCatching {
        supabase.postgrest.rpc("ops_list_incidents").decodeList<OperationalIncident>()
    }

    suspend fun createOperationalIncident(
        title: String,
        impactSummary: String,
        severity: String,
    ): Result<String> = runCatching {
        supabase.postgrest.rpc("ops_create_incident", buildJsonObject {
            put("p_title", title.trim())
            put("p_impact_summary", impactSummary.trim())
            put("p_severity", severity.trim().uppercase())
        }).decodeSingle<String>()
    }

    suspend fun updateOperationalIncident(
        incidentId: String,
        state: String,
        closingSummary: String? = null,
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc("ops_update_incident", buildJsonObject {
            put("p_incident_id", incidentId)
            put("p_state", state.trim().uppercase())
            closingSummary?.takeIf { it.isNotBlank() }?.let { put("p_closing_summary", it.trim()) }
        })
    }

    suspend fun setOperationalControl(
        controlType: String,
        enabled: Boolean,
        reason: String,
        displayMessage: String,
        expiresAt: String? = null,
        confirmation: String,
        auditNote: String,
    ): Result<String> = runCatching {
        supabase.postgrest.rpc("ops_set_operational_control", buildJsonObject {
            put("p_control_type", controlType.trim().uppercase())
            put("p_enabled", enabled)
            put("p_reason", reason.trim())
            put("p_display_message", displayMessage.trim())
            expiresAt?.takeIf { it.isNotBlank() }?.let { put("p_expires_at", it) }
            put("p_confirmation", confirmation.trim())
            put("p_audit_note", auditNote.trim())
        }).decodeSingle<String>()
    }

    suspend fun moderationQueue(state: String = "OPEN"): Result<List<ModerationQueueItem>> = runCatching {
        supabase.postgrest.rpc("moderation_list_queue", buildJsonObject {
            put("p_state", state.trim().uppercase())
            put("p_limit", 100)
        }).decodeList<ModerationQueueItem>()
    }

    suspend fun moderationDecideReport(reportId: String, decision: String, reason: String): Result<Unit> = runCatching {
        require(reason.trim().length in 3..1000) { "Provide a decision reason between 3 and 1,000 characters." }
        supabase.postgrest.rpc("moderation_decide_report", buildJsonObject {
            put("p_report_id", reportId)
            put("p_decision", decision.trim().uppercase())
            put("p_reason", reason.trim())
        })
    }

    suspend fun moderationAppeals(): Result<List<ModerationAppeal>> = runCatching {
        supabase.postgrest.rpc("moderation_list_appeals", buildJsonObject { put("p_limit", 100) })
            .decodeList<ModerationAppeal>()
    }

    suspend fun moderationDecideAppeal(appealId: String, decision: String, reason: String): Result<Unit> = runCatching {
        require(reason.trim().length in 3..2000) { "Provide an appeal reason between 3 and 2,000 characters." }
        supabase.postgrest.rpc("moderation_decide_appeal", buildJsonObject {
            put("p_appeal_id", appealId)
            put("p_decision", decision.trim().uppercase())
            put("p_reason", reason.trim())
        })
    }

    suspend fun editorialNotices(): Result<List<EditorialNoticeRecord>> = runCatching {
        supabase.from("official_notices").select {
            order(column = "updated_at", order = Order.DESCENDING)
        }.decodeList<EditorialNoticeRecord>()
    }

    suspend fun editorialCreateDraft(
        title: String,
        body: String,
        category: String,
        safetySensitive: Boolean,
        correctsNoticeId: String? = null,
    ): Result<String> = runCatching {
        supabase.postgrest.rpc("editorial_create_notice_draft", buildJsonObject {
            put("p_title", title.trim())
            put("p_body", body.trim())
            put("p_category", category.trim())
            put("p_safety_sensitive", safetySensitive)
            correctsNoticeId?.takeIf { it.isNotBlank() }?.let { put("p_corrects_notice_id", it) }
        }).decodeSingle<String>()
    }

    suspend fun editorialSubmitNotice(noticeId: String, note: String = "Submitted for review."): Result<Unit> = runCatching {
        supabase.postgrest.rpc("editorial_submit_notice", buildJsonObject {
            put("p_notice_id", noticeId)
            put("p_note", note.trim())
        })
    }

    suspend fun editorialReviewNotice(
        noticeId: String,
        outcome: String,
        note: String,
        publishMode: String = "PUBLISH",
        scheduledAt: String? = null,
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc("editorial_review_notice", buildJsonObject {
            put("p_notice_id", noticeId)
            put("p_outcome", outcome.trim().uppercase())
            put("p_note", note.trim())
            put("p_publish_mode", publishMode.trim().uppercase())
            scheduledAt?.takeIf { it.isNotBlank() }?.let { put("p_scheduled_at", it) }
        })
    }

    suspend fun editorialRetireNotice(noticeId: String, reason: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("editorial_retire_notice", buildJsonObject {
            put("p_notice_id", noticeId)
            put("p_reason", reason.trim())
        })
    }

    suspend fun communityGuidelinesAcceptedStatus(): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("community_guidelines_accepted_status").decodeSingle<Boolean>()
    }

    suspend fun acceptCommunityGuidelines(): Result<Unit> = runCatching {
        check(
            supabase.postgrest
                .rpc("accept_community_guidelines_v2")
                .decodeSingle<Boolean>()
        ) { "Community guideline acceptance was not confirmed by the server." }
    }

    suspend fun createCommunityComment(postId: String, body: String): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }
        supabase.postgrest.rpc(
            "create_community_comment",
            buildJsonObject {
                put("p_post_id", postId)
                put("p_body", cleanBody)
            },
        ).decodeSingle<String>()
    }.map { Unit }

    suspend fun updateCommunityComment(commentId: String, body: String): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }
        supabase.from("community_comments").update(CommunityCommentChangePayload(body = cleanBody)) {
            filter { eq("id", commentId) }
        }
    }

    suspend fun deleteCommunityComment(commentId: String): Result<Unit> = runCatching {
        supabase.from("community_comments").update(
            CommunityCommentChangePayload(
                state = "DELETED_BY_AUTHOR",
                deletedAt = Instant.now().toString(),
            )
        ) {
            filter { eq("id", commentId) }
        }
    }

    suspend fun getDashboardMetrics(): Result<DashboardMetrics> = runCatching {
        supabase.postgrest.rpc("get_public_directory_metrics").decodeSingle<DashboardMetrics>()
    }

    suspend fun listProjects(offset: Int, pageSize: Int = DIRECTORY_PAGE_SIZE): Result<DirectoryPage<ProjectRecord>> = runCatching {
        val rows = supabase.from("directory_projects").select {
            order(column = "display_order", order = Order.ASCENDING)
            range(offset.toLong()..(offset + pageSize).toLong())
        }.decodeList<ProjectRecord>()
        rows.toDirectoryPage(offset, pageSize)
    }

    suspend fun listCentres(offset: Int, pageSize: Int = DIRECTORY_PAGE_SIZE): Result<DirectoryPage<CentreRecord>> = runCatching {
        val rows = supabase.from("directory_centres").select {
            order(column = "display_order", order = Order.ASCENDING)
            range(offset.toLong()..(offset + pageSize).toLong())
        }.decodeList<CentreRecord>()
        rows.toDirectoryPage(offset, pageSize)
    }

    suspend fun listOpportunities(offset: Int, pageSize: Int = DIRECTORY_PAGE_SIZE): Result<DirectoryPage<OpportunityRecord>> = runCatching {
        val rows = supabase.from("directory_opportunities").select {
            order(column = "display_order", order = Order.ASCENDING)
            range(offset.toLong()..(offset + pageSize).toLong())
        }.decodeList<OpportunityRecord>()
        rows.toDirectoryPage(offset, pageSize)
    }

    suspend fun searchPublicContent(
        query: String,
        offset: Int = 0,
        pageSize: Int = SEARCH_PAGE_SIZE,
    ): Result<List<PublicSearchResult>> = runCatching {
        if (query.trim().length < 2) return@runCatching emptyList()
        supabase.postgrest.rpc(
            "search_public_directory",
            buildJsonObject {
                put("p_query", query.trim())
                put("p_limit", pageSize)
                put("p_offset", offset)
            }
        ).decodeList<PublicSearchResult>()
    }

    /** Returns a short-lived signed URL for the signed-in user's private profile-media object. */
    suspend fun resolveProfileAvatarUrl(userId: String): Result<String?> = runCatching {
        require(userId == authenticatedUserId()) { "A user may read only their own private profile-media path." }
        val avatarPath = supabase.from("profiles").select {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<ProfileAvatarRow>().firstOrNull()?.avatarUrl ?: return@runCatching null
        signedAvatarUrl(avatarPath)
    }

    /**
     * The image is normalised to JPEG before upload so its fixed user-scoped object path and MIME
     * type agree. The bucket’s RLS policies still independently enforce the caller’s UUID prefix.
     */
    suspend fun uploadProfilePhoto(uri: Uri, userId: String): Result<String> = runCatching {
        require(userId == authenticatedUserId()) { "A user may upload only to their own profile-media path." }
        val jpegBytes = encodeProfilePhoto(uri)
        val path = "$userId/avatar.jpg"
        supabase.storage.from(PROFILE_MEDIA_BUCKET).upload(path, jpegBytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        supabase.from("profiles").update(ProfileAvatarUpdate(path)) {
            filter { eq("id", userId) }
        }
        val persistedPath = supabase.from("profiles").select {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<ProfileAvatarRow>().firstOrNull()?.avatarUrl
        check(persistedPath == path) { "The profile photo metadata could not be saved. Please try again." }
        signedAvatarUrl(path)
    }

    suspend fun deleteProfilePhoto(userId: String): Result<Unit> = runCatching {
        require(userId == authenticatedUserId()) { "A user may delete only their own profile-media path." }
        val path = "$userId/avatar.jpg"
        supabase.storage.from(PROFILE_MEDIA_BUCKET).delete(path)
        supabase.from("profiles").update(ProfileAvatarUpdate(null)) {
            filter { eq("id", userId) }
        }
        val persistedPath = supabase.from("profiles").select {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<ProfileAvatarRow>().firstOrNull()?.avatarUrl
        check(persistedPath == null) { "The profile photo metadata could not be removed. Please try again." }
    }

    private fun toCommunityAlert(row: CommunityAlertInboxRow): CommunityAlert = CommunityAlert(
        id = row.id,
        notificationId = row.notificationId,
        category = runCatching { CommunityAlertCategory.valueOf(row.category) }.getOrDefault(CommunityAlertCategory.COMMUNITY_UPDATE),
        state = runCatching { CommunityAlertState.valueOf(row.messageState) }.getOrDefault(CommunityAlertState.ORIGINAL),
        originalAlertId = row.originalAlertId,
        title = row.title,
        summary = row.summary,
        body = row.body,
        linkedNoticeId = row.linkedNoticeId,
        publishedAt = row.publishedAt,
        expiresAt = row.expiresAt,
        createdAt = row.createdAt,
        readAt = row.readAt,
    )

    private fun toCommunityAlertDashboardItem(row: CommunityAlertDashboardRow): CommunityAlertDashboardItem = CommunityAlertDashboardItem(
        id = row.id,
        category = runCatching { CommunityAlertCategory.valueOf(row.category) }.getOrDefault(CommunityAlertCategory.COMMUNITY_UPDATE),
        state = runCatching { CommunityAlertState.valueOf(row.messageState) }.getOrDefault(CommunityAlertState.ORIGINAL),
        title = row.title,
        summary = row.summary,
        status = row.status,
        scheduledAt = row.scheduledAt,
        publishedAt = row.publishedAt,
        expiresAt = row.expiresAt,
        dispatchState = row.dispatchState,
        intendedRecipients = row.intendedRecipientCount,
        eligibleDevices = row.eligibleDeviceCount,
        fcmAccepted = row.fcmAcceptedCount,
        fcmFailed = row.fcmFailedCount,
        readCount = row.readCount,
        createdAt = row.createdAt,
    )

    private suspend fun toCommunityMedia(row: CommunityMediaFeedRow): MediaItem {
        val kind = runCatching { MediaKind.valueOf(row.mediaKind) }.getOrDefault(MediaKind.IMAGE)
        return MediaItem(
            id = row.id,
            targetType = MediaTargetType.COMMUNITY_POST,
            targetId = row.postId,
            storagePath = row.storagePath,
            kind = kind,
            mimeType = row.mimeType,
            byteSize = row.byteSize,
            width = row.width,
            height = row.height,
            durationSeconds = row.durationSeconds,
            position = row.position,
            caption = row.caption,
            signedUrl = signedCommunityMediaUrl(row.storagePath),
        )
    }

    private suspend fun toCommunityPost(row: CommunityPostFeedRow): CommunityPost {
        val media = coroutineScope { row.media.sortedBy { it.position }.map { embedded -> async { embedded.toMediaItem(row.id) } }.awaitAll() }
        return CommunityPost(
            id = row.id,
            author = row.authorName,
            handle = "@${row.authorHandle.removePrefix("@")}",
            content = row.body,
            category = row.categoryLabel ?: if (row.staffBadge) "Official Community" else "Community",
            createdAt = row.createdAt,
            reactions = row.reactionCount,
            comments = row.commentCount,
            viewerHasLiked = row.viewerHasLiked,
            trendingScore = row.trendingScore.coerceAtLeast(0),
            isFollowedTopic = row.isFollowedTopic,
            hasMedia = media.isNotEmpty(),
            media = media,
            isOfficial = row.staffBadge,
            authorId = row.authorId,
            authorAvatarUrl = row.avatarPath?.let { signedAvatarUrl(it, row.avatarUpdatedAt) },
            isLocked = row.isLocked,
            editedAt = row.editedAt,
        )
    }

    private suspend fun CommunityEmbeddedMediaRow.toMediaItem(postId: String): MediaItem {
        val kind = runCatching { MediaKind.valueOf(mediaKind) }.getOrDefault(MediaKind.IMAGE)
        return MediaItem(
            id = id, targetType = MediaTargetType.COMMUNITY_POST, targetId = postId, storagePath = storagePath,
            kind = kind, mimeType = mimeType, byteSize = byteSize, width = width, height = height,
            durationSeconds = durationSeconds, position = position, caption = caption, signedUrl = signedCommunityMediaUrl(storagePath),
        )
    }

    private suspend fun toCommunityComment(row: CommunityCommentFeedRow): CommunityComment = CommunityComment(
        id = row.id,
        postId = row.postId,
        authorId = row.authorId,
        author = row.authorName,
        handle = "@${row.authorHandle.removePrefix("@")}",
        authorAvatarUrl = row.avatarPath?.let { signedAvatarUrl(it, row.avatarUpdatedAt) },
        content = row.body,
        createdAt = row.createdAt,
        editedAt = row.editedAt,
        isStaff = row.staffBadge,
    )

    private suspend fun signedCommunityMediaUrl(path: String): String =
        supabase.storage.from(COMMUNITY_MEDIA_BUCKET).createSignedUrl(path, 15.minutes)

    private suspend fun signedAvatarUrl(path: String, revision: String? = null): String {
        val signed = supabase.storage.from(PROFILE_MEDIA_BUCKET).createSignedUrl(path, 15.minutes)
        val stableRevision = revision?.hashCode()?.toUInt()?.toString(16) ?: path.hashCode().toUInt().toString(16)
        return "$signed${if (signed.contains("?")) "&" else "?"}v=$stableRevision"
    }

    private fun encodeProfilePhoto(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error("The selected image could not be read.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image could not be decoded." }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > PROFILE_DECODE_MAX_DIMENSION || bounds.outHeight / sampleSize > PROFILE_DECODE_MAX_DIMENSION) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val source = applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: error("The selected image could not be decoded.")

        val orientation = applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        var bitmap = source.normalizedForExif(orientation)
        if (bitmap !== source) source.recycle()
        if (maxOf(bitmap.width, bitmap.height) > PROFILE_OUTPUT_MAX_DIMENSION) {
            val scale = PROFILE_OUTPUT_MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        return ByteArrayOutputStream(MAX_PROFILE_PHOTO_BYTES).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "The selected image could not be prepared."
            }
            bitmap.recycle()
            output.toByteArray().also { bytes ->
                require(bytes.size in 1..MAX_PROFILE_PHOTO_BYTES) { "Choose an image that can be prepared below 5 MB." }
            }
        }
    }

    private fun queryContentSize(uri: Uri): Long? = applicationContext.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it >= 0L } else null
    }

    private fun copyContentToBoundedFile(uri: Uri, suffix: String, byteLimit: Long): File {
        val directory = File(applicationContext.cacheDir, "rtc_feedback_uploads").apply { mkdirs() }
        val target = File.createTempFile("feedback_", suffix, directory)
        try {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= byteLimit) { "The screenshot must be no larger than 5 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("The selected screenshot could not be read.")
            require(target.length() in 1..byteLimit) { "The screenshot must be no larger than 5 MB." }
            return target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }


    private fun Bitmap.normalizedForExif(orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return this
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { preScale(-1f, 1f); postRotate(270f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { preScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun <T> List<T>.toDirectoryPage(offset: Int, pageSize: Int): DirectoryPage<T> =
        DirectoryPage(
            items = take(pageSize),
            offset = offset,
            canLoadMore = size > pageSize,
        )

    private companion object {
        const val PROFILE_MEDIA_BUCKET = "rtc-profile-media"
        const val COMMUNITY_MEDIA_BUCKET = "rtc-community-media"
        const val FEEDBACK_MEDIA_BUCKET = "rtc-feedback-media"
        const val DIRECTORY_PAGE_SIZE = 12
        const val SEARCH_PAGE_SIZE = 30
        const val MAX_PROFILE_PHOTO_BYTES = 5 * 1024 * 1024
        const val MAX_PROFILE_BIO_LENGTH = 600
        const val MAX_PROFILE_INTERESTS = 8
        const val MAX_FEEDBACK_SCREENSHOT_BYTES = 5L * 1024 * 1024
        const val PROFILE_DECODE_MAX_DIMENSION = 3072
        const val PROFILE_OUTPUT_MAX_DIMENSION = 2048
        const val MAX_MEDIA_PER_POST = 10
        const val MAX_IMAGE_MEDIA_BYTES = 5 * 1024 * 1024
        const val MAX_VIDEO_MEDIA_BYTES = 20 * 1024 * 1024
        const val MAX_VIDEO_DURATION_SECONDS = 180
        val IMAGE_MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val VIDEO_MEDIA_TYPES = setOf("video/mp4", "video/webm")
        const val JPEG_QUALITY = 90
    }
}
