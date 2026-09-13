package za.org.rtc.community.feature.dailypost.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.dailypost.domain.DailyPost
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlock
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlockType
import za.org.rtc.community.feature.dailypost.domain.DailyPostComment
import za.org.rtc.community.feature.dailypost.domain.DailyPostCursor
import za.org.rtc.community.feature.dailypost.domain.DailyPostDraft
import za.org.rtc.community.feature.dailypost.domain.DailyPostMedia
import za.org.rtc.community.feature.dailypost.domain.DailyPostMediaUpload
import za.org.rtc.community.feature.dailypost.domain.DailyPostPage
import za.org.rtc.community.feature.dailypost.domain.DailyPostRepository
import za.org.rtc.community.feature.dailypost.domain.DailyPostState
import za.org.rtc.community.feature.dailypost.domain.DailyPostTranslation
import za.org.rtc.community.feature.dailypost.domain.DailyPostType

@Singleton
class SupabaseDailyPostRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : DailyPostRepository {

    override suspend fun page(cursor: DailyPostCursor?, limit: Int): Result<DailyPostPage> = runCatching {
        val rows = supabase.postgrest.rpc("daily_post_page_v1", buildJsonObject {
            cursor?.let {
                put("p_cursor_published_at", it.publishedAt.toString())
                put("p_cursor_id", it.id)
            }
            put("p_limit", limit.coerceIn(1, 50))
        }).decodeSingle<JsonArray>().map { it.jsonObject.toPost() }
        DailyPostPage(rows, rows.lastOrNull()?.let { post ->
            post.publishedAt?.let { DailyPostCursor(it, post.id) }
        })
    }

    override suspend fun get(postId: String): Result<DailyPost?> = runCatching {
        requireUuid(postId)
        val element = supabase.postgrest.rpc("daily_post_get_v1", buildJsonObject { put("p_post_id", postId) })
            .decodeSingle<JsonElement>()
        if (element is JsonObject) element.toPost() else null
    }

    override suspend fun media(postId: String): Result<List<DailyPostMedia>> = runCatching {
        requireUuid(postId)
        supabase.from("daily_post_media")
            .select {
                filter { eq("post_id", postId) }
                order("sort_order", Order.ASCENDING)
                order("id", Order.ASCENDING)
            }
            .decodeList<DailyPostMediaRow>()
            .map { row -> row.toDomain(signedUrl = issueMediaUrl(row.storagePath)) }
    }

    override suspend fun comments(postId: String): Result<List<DailyPostComment>> = runCatching {
        requireUuid(postId)
        supabase.postgrest.rpc("daily_post_comments_page_v1", buildJsonObject {
            put("p_post_id", postId)
            put("p_limit", 200)
        }).decodeSingle<JsonArray>().map { it.jsonObject.toComment() }
    }

    override suspend fun addComment(postId: String, body: String, parentId: String?): Result<Unit> = runCatching {
        requireUuid(postId)
        parentId?.let(::requireUuid)
        val clean = body.trim()
        require(clean.length in 1..2_000) { "Comment must contain 1–2000 characters." }
        supabase.postgrest.rpc("daily_post_comment_create_v1", buildJsonObject {
            put("p_post_id", postId)
            put("p_body", clean)
            parentId?.let { put("p_parent_id", it) }
        })
        Unit
    }

    override suspend fun updateComment(commentId: String, body: String): Result<Unit> = runCatching {
        requireUuid(commentId)
        val clean = body.trim()
        require(clean.length in 1..2_000) { "Comment must contain 1–2000 characters." }
        supabase.postgrest.rpc("daily_post_comment_update_v1", buildJsonObject {
            put("p_comment_id", commentId)
            put("p_body", clean)
        })
        Unit
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        requireUuid(commentId)
        supabase.postgrest.rpc("daily_post_comment_delete_v1", buildJsonObject { put("p_comment_id", commentId) })
        Unit
    }

    override suspend fun markPreviewPresented(postId: String): Result<Unit> = runCatching {
        requireUuid(postId)
        supabase.postgrest.rpc("daily_post_preview_mark_v1", buildJsonObject { put("p_post_id", postId) })
        Unit
    }

    override suspend fun nextUnseenPreview(): Result<DailyPost?> = runCatching {
        val element = supabase.postgrest.rpc("daily_post_preview_next_v1").decodeSingle<JsonElement>()
        if (element is JsonObject) element.toPost() else null
    }

    override suspend fun translate(postId: String, targetLanguage: String): Result<DailyPostTranslation> = runCatching {
        requireUuid(postId)
        requireLanguage(targetLanguage)
        val response = supabase.functions.invoke("daily-post-language", buildJsonObject {
            put("action", "translate")
            put("postId", postId)
            put("targetLanguage", targetLanguage)
        })
        check(response.status.value in 200..299) { "Translation service rejected the request." }
        response.body<JsonObject>().toTranslation()
    }

    override suspend fun narration(postId: String, targetLanguage: String?): Result<String> = runCatching {
        requireUuid(postId)
        targetLanguage?.let(::requireLanguage)
        val response = supabase.functions.invoke("daily-post-language", buildJsonObject {
            put("action", "narrate")
            put("postId", postId)
            targetLanguage?.let { put("targetLanguage", it) }
        })
        check(response.status.value in 200..299) { "Narration service rejected the request." }
        response.body<JsonObject>().requiredString("audioUrl")
    }

    override suspend fun adminPage(state: DailyPostState?, limit: Int): Result<List<DailyPost>> = runCatching {
        supabase.postgrest.rpc("daily_post_admin_page_v1", buildJsonObject {
            state?.let { put("p_state", it.name) }
            put("p_limit", limit.coerceIn(1, 200))
        }).decodeSingle<JsonArray>().map { it.jsonObject.toPost() }
    }

    override suspend fun saveDraft(draft: DailyPostDraft): Result<String> = runCatching {
        val cleanHeadline = draft.headline.trim()
        require(cleanHeadline.length <= 180) { "Headline is too long." }
        require(draft.excerpt.length <= 600) { "Excerpt is too long." }
        requireLanguage(draft.canonicalLanguage)
        val result = supabase.postgrest.rpc("daily_post_save_draft_v1", buildJsonObject {
            draft.id?.let { requireUuid(it); put("p_post_id", it) }
            put("p_publication_type", draft.publicationType.name)
            put("p_template_key", draft.templateKey)
            put("p_canonical_language", draft.canonicalLanguage)
            put("p_headline", cleanHeadline)
            put("p_excerpt", draft.excerpt.trim())
            put("p_content_blocks", draft.blocks.toJson())
            draft.quotedPostId?.let { requireUuid(it); put("p_quoted_post_id", it) }
            put("p_push_enabled", draft.pushEnabled)
            put("p_preview_popup_enabled", draft.previewPopupEnabled)
        }).decodeSingle<JsonElement>()
        result.jsonPrimitive.content
    }

    override suspend fun uploadMedia(
        postId: String,
        upload: DailyPostMediaUpload,
        sortOrder: Int,
    ): Result<DailyPostMedia> = runCatching {
        requireUuid(postId)
        require(upload.bytes.isNotEmpty() && upload.bytes.size <= MAX_UPLOAD_BYTES) { "Media exceeds the 50 MB publication limit." }
        val type = when {
            upload.mimeType.startsWith("image/") -> "IMAGE"
            upload.mimeType.startsWith("video/") -> "VIDEO"
            else -> error("Only approved image and video media can be uploaded.")
        }
        require(upload.mimeType in ALLOWED_MIME_TYPES) { "This media format is not supported." }
        val extension = upload.fileExtension.lowercase().removePrefix(".")
        require(extension in ALLOWED_EXTENSIONS) { "This file extension is not supported." }
        require(upload.altText.length <= 600) { "Alternative text is too long." }
        val path = "$postId/${UUID.randomUUID()}.$extension"
        val bucket = supabase.storage.from(MEDIA_BUCKET)
        bucket.upload(path, upload.bytes) { upsert = false }
        try {
            val payload = DailyPostMediaInsert(
                postId = postId,
                storagePath = path,
                mediaType = type,
                mimeType = upload.mimeType,
                altText = upload.altText.trim(),
                sortOrder = sortOrder.coerceIn(0, 100),
            )
            val row = supabase.from("daily_post_media").insert(payload) { select() }.decodeSingle<DailyPostMediaRow>()
            row.toDomain(issueMediaUrl(path))
        } catch (error: Throwable) {
            runCatching { bucket.delete(path) }
            throw error
        }
    }

    override suspend fun deleteMedia(mediaId: String): Result<Unit> = runCatching {
        requireUuid(mediaId)
        val row = supabase.from("daily_post_media").select {
            filter { eq("id", mediaId) }
            limit(1)
        }.decodeList<DailyPostMediaRow>().firstOrNull() ?: error("Media is no longer available.")
        supabase.storage.from(MEDIA_BUCKET).delete(row.storagePath)
        supabase.from("daily_post_media").delete { filter { eq("id", mediaId) } }
        Unit
    }

    override suspend fun publishNow(postId: String, pushEnabled: Boolean, previewPopupEnabled: Boolean): Result<Unit> = runCatching {
        requireUuid(postId)
        supabase.postgrest.rpc("daily_post_publish_v1", buildJsonObject {
            put("p_post_id", postId)
            put("p_mode", "NOW")
            put("p_push_enabled", pushEnabled)
            put("p_preview_popup_enabled", previewPopupEnabled)
        })
        Unit
    }

    override suspend fun schedule(postId: String, scheduledForIso: String, pushEnabled: Boolean, previewPopupEnabled: Boolean): Result<Unit> = runCatching {
        requireUuid(postId)
        val scheduled = Instant.parse(scheduledForIso)
        require(scheduled.isAfter(Instant.now())) { "Scheduled time must be in the future." }
        supabase.postgrest.rpc("daily_post_publish_v1", buildJsonObject {
            put("p_post_id", postId)
            put("p_mode", "SCHEDULE")
            put("p_scheduled_for", scheduled.toString())
            put("p_push_enabled", pushEnabled)
            put("p_preview_popup_enabled", previewPopupEnabled)
        })
        Unit
    }

    override suspend fun archive(postId: String): Result<Unit> = runCatching {
        requireUuid(postId)
        supabase.postgrest.rpc("daily_post_archive_v1", buildJsonObject { put("p_post_id", postId) })
        Unit
    }

    private suspend fun issueMediaUrl(path: String): String =
        supabase.storage.from(MEDIA_BUCKET).createSignedUrl(path, SIGNED_URL_TTL)

    private fun JsonObject.toPost(): DailyPost = DailyPost(
        id = requiredString("id"),
        authorId = requiredString("author_id"),
        state = enumValue(optionalString("state"), DailyPostState.DRAFT),
        publicationType = enumValue(optionalString("publication_type"), DailyPostType.NEWS),
        templateKey = optionalString("template_key") ?: "standard_news",
        canonicalLanguage = optionalString("canonical_language") ?: "en",
        headline = optionalString("headline").orEmpty(),
        excerpt = optionalString("excerpt").orEmpty(),
        blocks = (get("content_blocks") as? JsonArray)?.mapNotNull { it.jsonObject.toBlockOrNull() }.orEmpty(),
        quotedPostId = optionalString("quoted_post_id"),
        scheduledFor = optionalString("scheduled_for")?.let(::parseInstant),
        publishedAt = optionalString("published_at")?.let(::parseInstant),
        pushEnabled = optionalBoolean("push_enabled"),
        previewPopupEnabled = optionalBoolean("preview_popup_enabled", true),
        revision = get("revision")?.jsonPrimitive?.intOrNull ?: 1,
        commentCount = get("comment_count")?.jsonPrimitive?.intOrNull ?: 0,
    )

    private fun JsonObject.toComment(): DailyPostComment = DailyPostComment(
        id = requiredString("id"),
        postId = requiredString("post_id"),
        authorId = requiredString("author_id"),
        parentId = optionalString("parent_id"),
        depth = get("depth")?.jsonPrimitive?.intOrNull ?: 0,
        body = optionalString("body").orEmpty(),
        state = optionalString("state") ?: "VISIBLE",
        authorDisplayName = optionalString("author_display_name") ?: "Resident",
        authorAvatarUrl = optionalString("author_avatar_url"),
        createdAt = parseInstant(requiredString("created_at")),
        updatedAt = parseInstant(requiredString("updated_at")),
    )

    private fun JsonObject.toTranslation(): DailyPostTranslation = DailyPostTranslation(
        postId = requiredString("postId"),
        revision = get("revision")?.jsonPrimitive?.intOrNull ?: 1,
        targetLanguage = requiredString("targetLanguage"),
        headline = requiredString("headline"),
        excerpt = optionalString("excerpt").orEmpty(),
        blocks = (get("blocks") as? JsonArray)?.mapNotNull { it.jsonObject.toBlockOrNull() }.orEmpty(),
        audioUrl = optionalString("audioUrl"),
    )

    private fun JsonObject.toBlockOrNull(): DailyPostBlock? = runCatching {
        DailyPostBlock(
            id = requiredString("id"),
            type = DailyPostBlockType.valueOf(requiredString("type")),
            text = optionalString("text").orEmpty(),
            mediaIds = (get("mediaIds") as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            quotedPostId = optionalString("quotedPostId"),
            actionLabel = optionalString("actionLabel"),
            actionUrl = optionalString("actionUrl"),
        )
    }.getOrNull()

    private fun List<DailyPostBlock>.toJson(): JsonArray = buildJsonArray {
        forEach { block -> add(buildJsonObject {
            put("id", block.id)
            put("type", block.type.name)
            put("text", block.text)
            put("mediaIds", buildJsonArray { block.mediaIds.forEach { add(JsonPrimitive(it)) } })
            block.quotedPostId?.let { put("quotedPostId", it) }
            block.actionLabel?.let { put("actionLabel", it) }
            block.actionUrl?.let { put("actionUrl", it) }
        }) }
    }

    private fun DailyPostMediaRow.toDomain(signedUrl: String?) = DailyPostMedia(
        id = id,
        postId = postId,
        storagePath = storagePath,
        mediaType = mediaType,
        mimeType = mimeType,
        altText = altText,
        sortOrder = sortOrder,
        signedUrl = signedUrl,
    )

    private fun JsonObject.requiredString(key: String): String = optionalString(key) ?: error("Missing Daily Post field: $key")
    private fun JsonObject.optionalString(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.optionalBoolean(key: String, default: Boolean = false): Boolean = optionalString(key)?.toBooleanStrictOrNull() ?: default
    private fun parseInstant(value: String): Instant = Instant.parse(value)
    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    private fun requireUuid(value: String) { require(UUID_REGEX.matches(value)) { "Invalid identifier." } }
    private fun requireLanguage(value: String) { require(LANGUAGE_REGEX.matches(value)) { "Invalid language code." } }

    private companion object {
        const val MEDIA_BUCKET = "daily-post-media"
        const val MAX_UPLOAD_BYTES = 52_428_800
        val SIGNED_URL_TTL = 2.hours
        val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm")
        val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "mp4", "webm")
        val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        val LANGUAGE_REGEX = Regex("^[a-z]{2,3}(-[A-Z]{2})?$")
    }
}

@Serializable
private data class DailyPostMediaRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("alt_text") val altText: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
private data class DailyPostMediaInsert(
    @SerialName("post_id") val postId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("alt_text") val altText: String,
    @SerialName("sort_order") val sortOrder: Int,
)
