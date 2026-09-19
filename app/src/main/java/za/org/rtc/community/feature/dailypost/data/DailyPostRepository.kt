package za.org.rtc.community.feature.dailypost.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.DailyPostComment
import za.org.rtc.community.feature.dailypost.domain.DailyPostCommentPage
import za.org.rtc.community.feature.dailypost.domain.DailyPostTemplateStyle
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DailyPostRepository {
    fun observePublishedArticles(): Flow<List<DailyPostArticle>>
    fun observeAllArticles(): Flow<List<DailyPostArticle>>
    suspend fun refreshPublishedArticles()
    suspend fun refreshAllArticles()
    suspend fun getArticle(id: String): DailyPostArticle?
    suspend fun saveArticle(article: DailyPostArticle): DailyPostArticle
    suspend fun publishArticle(article: DailyPostArticle): DailyPostArticle
    suspend fun deleteArticle(id: String)
    suspend fun toggleLike(id: String)
    suspend fun loadComments(
        articleId: String,
        afterCreatedAt: String? = null,
        afterId: String? = null,
        limit: Int = 100,
    ): DailyPostCommentPage
    fun observeCommentChanges(articleId: String): Flow<Unit>
    suspend fun createComment(articleId: String, body: String, parentId: String? = null): String
    suspend fun updateComment(commentId: String, body: String)
    suspend fun deleteComment(commentId: String)
    suspend fun moderateComment(commentId: String, reason: String)
    suspend fun reportComment(commentId: String, reasonCode: String, detail: String)
}

@Serializable
private data class DailyPostRemoteRow(
    val id: String,
    val state: String = "DRAFT",
    @SerialName("publication_type") val publicationType: String = "NEWS",
    @SerialName("template_key") val templateKey: String = "standard_news",
    @SerialName("canonical_language") val canonicalLanguage: String = "en",
    val headline: String = "",
    val excerpt: String = "",
    @SerialName("content_blocks") val contentBlocks: JsonElement = JsonArray(emptyList()),
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("push_enabled") val pushEnabled: Boolean = false,
    @SerialName("preview_popup_enabled") val previewPopupEnabled: Boolean = true,
    @SerialName("comment_count") val commentCount: Int = 0,
    val revision: Int = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class DailyPostCommentRemoteRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val depth: Int = 0,
    val body: String = "",
    val state: String = "VISIBLE",
    @SerialName("moderation_reason") val moderationReason: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/**
 * Supabase is authoritative for editorial content and publication state. Room remains a
 * cache so the resident feed can render the last known publication during a transient outage.
 * All mutations use the production Daily Post RPCs; no service-role credential is used here.
 */
@Singleton
class RoomDailyPostRepository @Inject constructor(
    private val dao: DailyPostDao,
    private val supabase: SupabaseClient,
) : DailyPostRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observePublishedArticles(): Flow<List<DailyPostArticle>> =
        dao.observePublishedArticles().map { list -> list.map { it.toDomain() } }

    override fun observeAllArticles(): Flow<List<DailyPostArticle>> =
        dao.observeAllArticles().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshPublishedArticles() {
        val rows = supabase.postgrest.rpc(
            "daily_post_page_v1",
            buildJsonObject { put("p_limit", 50) },
        ).decodeList<DailyPostRemoteRow>()
        dao.insertArticles(rows.map { it.toEntity() })
        val liveIds = rows.map { it.id }.toSet()
        if (liveIds.isEmpty()) dao.deleteAllPublished() else dao.deletePublishedNotIn(liveIds.toList())
    }

    override suspend fun refreshAllArticles() {
        val rows = supabase.postgrest.rpc(
            "daily_post_admin_page_v1",
            buildJsonObject { put("p_limit", 200) },
        ).decodeList<DailyPostRemoteRow>()
        dao.insertArticles(rows.map { it.toEntity() })
    }

    override suspend fun getArticle(id: String): DailyPostArticle? {
        val cached = dao.getArticleById(id)?.toDomain()
        val remote = runCatching {
            supabase.postgrest.rpc(
                "daily_post_get_v1",
                buildJsonObject { put("p_post_id", id) },
            ).decodeSingle<DailyPostRemoteRow>()
        }.getOrNull()
        return remote?.toEntity()?.also { dao.insertArticle(it) }?.toDomain() ?: cached
    }

    override suspend fun saveArticle(article: DailyPostArticle): DailyPostArticle {
        val existing = runCatching {
            supabase.postgrest.rpc(
                "daily_post_admin_page_v1",
                buildJsonObject { put("p_limit", 200) },
            ).decodeList<DailyPostRemoteRow>().firstOrNull { it.id == article.id }
        }.getOrNull()
        val remoteId = supabase.postgrest.rpc(
            "daily_post_save_draft_v1",
            article.toDraftPayload(existing?.id),
        ).decodeSingle<String>()
        val saved = article.copy(id = remoteId, isPublished = false)
        dao.insertArticle(saved.toEntity())
        return saved
    }

    override suspend fun publishArticle(article: DailyPostArticle): DailyPostArticle {
        val draft = saveArticle(article)
        supabase.postgrest.rpc(
            "daily_post_publish_v1",
            buildJsonObject {
                put("p_post_id", draft.id)
                put("p_mode", "NOW")
                put("p_push_enabled", draft.pushEnabled)
                put("p_preview_popup_enabled", draft.previewPopupEnabled)
            },
        )
        val published = draft.copy(isPublished = true, publishedAtEpochMillis = System.currentTimeMillis())
        dao.insertArticle(published.toEntity())
        return published
    }

    override suspend fun deleteArticle(id: String) {
        // The production lifecycle is archive, not destructive delete, so publication history
        // and audit records remain intact for administration and resident-link safety.
        supabase.postgrest.rpc(
            "daily_post_archive_v1",
            buildJsonObject { put("p_post_id", id) },
        )
        dao.deleteArticle(id)
    }

    override suspend fun toggleLike(id: String) {
        // Daily Post likes are not part of the current production contract. Keep the cache
        // behavior for offline UI compatibility without claiming that a remote like persisted.
        dao.toggleLike(id)
    }

    override suspend fun loadComments(
        articleId: String,
        afterCreatedAt: String?,
        afterId: String?,
        limit: Int,
    ): DailyPostCommentPage {
        val boundedLimit = limit.coerceIn(1, 200)
        val rows = supabase.postgrest.rpc(
            "daily_post_comments_page_v1",
            buildJsonObject {
                put("p_post_id", articleId)
                afterCreatedAt?.let { put("p_after_created_at", it) }
                afterId?.let { put("p_after_id", it) }
                put("p_limit", boundedLimit)
            },
        ).decodeList<DailyPostCommentRemoteRow>()
        return DailyPostCommentPage(
            comments = rows.map { it.toDomain() },
            hasMore = rows.size == boundedLimit,
        )
    }

    override fun observeCommentChanges(articleId: String): Flow<Unit> = callbackFlow {
        val channel = supabase.realtime.channel("daily-post-comments-$articleId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "daily_post_comments"
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            changeFlow.collect { action ->
                val postId = when (action) {
                    is PostgresAction.Insert -> action.record["post_id"]?.jsonPrimitive?.content
                    is PostgresAction.Update -> action.record["post_id"]?.jsonPrimitive?.content
                    is PostgresAction.Delete -> action.oldRecord["post_id"]?.jsonPrimitive?.content
                    else -> null
                }
                if (postId == articleId) trySend(Unit).isSuccess
            }
        }
        runCatching { channel.subscribe() }
            .onFailure { close(it) }
        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    override suspend fun createComment(articleId: String, body: String, parentId: String?): String =
        supabase.postgrest.rpc(
            "daily_post_comment_create_v1",
            buildJsonObject {
                put("p_post_id", articleId)
                put("p_body", body.trim())
                parentId?.let { put("p_parent_id", it) }
            },
        ).decodeSingle<String>()

    override suspend fun updateComment(commentId: String, body: String) {
        supabase.postgrest.rpc(
            "daily_post_comment_update_v1",
            buildJsonObject {
                put("p_comment_id", commentId)
                put("p_body", body.trim())
            },
        )
    }

    override suspend fun deleteComment(commentId: String) {
        supabase.postgrest.rpc(
            "daily_post_comment_delete_v1",
            buildJsonObject { put("p_comment_id", commentId) },
        )
    }

    override suspend fun moderateComment(commentId: String, reason: String) {
        supabase.postgrest.rpc(
            "daily_post_comment_moderate_v1",
            buildJsonObject {
                put("p_comment_id", commentId)
                put("p_reason", reason.trim())
            },
        )
    }

    override suspend fun reportComment(commentId: String, reasonCode: String, detail: String) {
        supabase.postgrest.rpc(
            "daily_post_comment_report_v1",
            buildJsonObject {
                put("p_comment_id", commentId)
                put("p_reason_code", reasonCode)
                put("p_detail", detail.trim())
            },
        )
    }

    private fun DailyPostArticle.toDraftPayload(existingId: String?) = buildJsonObject {
        if (existingId != null) put("p_post_id", existingId)
        put("p_publication_type", if (templateStyle == DailyPostTemplateStyle.BREAKING_BULLETIN) "BREAKING" else "NEWS")
        put("p_template_key", templateStyle.name.lowercase())
        put("p_canonical_language", "en")
        put("p_headline", title.trim())
        put("p_excerpt", subtitle.trim())
        put("p_content_blocks", contentBlocks())
        put("p_push_enabled", pushEnabled)
        put("p_preview_popup_enabled", previewPopupEnabled)
    }

    private fun DailyPostArticle.contentBlocks(): JsonArray = buildJsonArray {
        add(block("HEADLINE", title))
        if (subtitle.isNotBlank()) add(block("SUBHEADING", subtitle))
        add(block("PARAGRAPH", content))
        keyHighlights.forEach { add(block("INFO_CALLOUT", it)) }
        quoteText?.takeIf { it.isNotBlank() }?.let { add(block("PULL_QUOTE", it, quoteAuthor)) }
    }

    private fun block(type: String, text: String, author: String? = null) = buildJsonObject {
        put("id", UUID.randomUUID().toString())
        put("type", type)
        put("text", text)
        author?.takeIf { it.isNotBlank() }?.let { put("author", it) }
    }

    private fun DailyPostRemoteRow.toEntity(): DailyPostEntity =
        toArticle().toEntity()

    private fun DailyPostArticle.toEntity(): DailyPostEntity =
        DailyPostEntity.fromDomain(this)

    private fun DailyPostRemoteRow.toArticle(): DailyPostArticle {
        val blocks = contentBlocks.jsonArrayOrEmpty()
        val paragraph = blocks.firstText("PARAGRAPH")
        val headline = headline.ifBlank { blocks.firstText("HEADLINE") }
        val subheading = excerpt.ifBlank { blocks.firstText("SUBHEADING") }
        val highlights = blocks.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "INFO_CALLOUT" }
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
        val quote = blocks.firstText("PULL_QUOTE").ifBlank { null }
        return DailyPostArticle(
            id = id,
            title = headline,
            subtitle = subheading,
            content = paragraph,
            category = if (publicationType == "BREAKING") "Public Safety" else "Community News",
            authorName = "Office of the Administrator",
            authorRole = "Content Editor",
            templateStyle = DailyPostTemplateStyle.fromName(templateKey),
            keyHighlights = highlights,
            quoteText = quote,
            quoteAuthor = blocks.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "PULL_QUOTE" }
                ?.jsonObject?.get("author")?.jsonPrimitive?.content,
            publishedAtEpochMillis = parseEpoch(publishedAt ?: updatedAt ?: createdAt),
            readTimeMinutes = maxOf(1, (paragraph.split(Regex("\\s+")).count { it.isNotBlank() } / 200) + 1),
            commentCount = commentCount,
            isPublished = state == "PUBLISHED",
            pushEnabled = pushEnabled,
            previewPopupEnabled = previewPopupEnabled,
        )
    }

    private fun JsonElement.jsonArrayOrEmpty(): List<JsonElement> =
        (this as? JsonArray)?.toList() ?: emptyList()

    private fun List<JsonElement>.firstText(type: String): String =
        firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == type }
            ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

    private fun parseEpoch(value: String?): Long =
        runCatching { Instant.parse(value ?: "").toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private fun DailyPostCommentRemoteRow.toDomain(): DailyPostComment = DailyPostComment(
        id = id,
        postId = postId,
        authorId = authorId,
        body = body,
        parentId = parentId,
        depth = depth,
        state = state,
        moderationReason = moderationReason,
        createdAtEpochMillis = parseEpoch(createdAt),
        updatedAtEpochMillis = parseEpoch(updatedAt),
    )
}
