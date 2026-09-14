package za.org.rtc.community.feature.community

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.CommunityRealtimeNotification
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.core.MediaTargetType
import za.org.rtc.community.data.local.CachedCommentDao
import za.org.rtc.community.data.local.CachedCommentEntity
import za.org.rtc.community.data.local.CachedPostDao
import za.org.rtc.community.data.local.CachedPostEntity

@Singleton
class SupabaseCommunityRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val cachedPostDao: CachedPostDao,
    private val cachedCommentDao: CachedCommentDao,
) : CommunityRepository {
    private val clock = Clock.systemUTC()
    private val signedUrlCache = SignedUrlCache(
        capacity = SIGNED_URL_CACHE_CAPACITY,
        refreshSkew = Duration.ofSeconds(SIGNED_URL_REFRESH_SKEW_SECONDS),
        clock = clock,
    )
    private val mediaPaths = ConcurrentHashMap<String, String>()

    suspend fun addLocalPost(post: CommunityPost) {
        cachedPostDao.insertPost(post.toCachedEntity())
    }

    override suspend fun loadFeedPage(
        cursor: CommunityCursor?,
        limit: Int,
    ): Result<CommunityFeedPage> = runCatching {
        val visibleLimit = limit.coerceIn(1, MAX_VISIBLE_PAGE_SIZE)
        val serverLimit = (visibleLimit + 1).coerceAtMost(MAX_SERVER_PAGE_SIZE)

        val remotePosts = runCatching {
            val rows = supabase.postgrest.rpc(
                function = "community_post_page_v3",
                parameters = buildJsonObject {
                    cursor?.let {
                        put("p_before_created_at", it.createdAt)
                        put("p_before_id", it.id)
                    }
                    put("p_limit", serverLimit)
                },
            ).decodeList<CommunityFeedRow>()
            coroutineScope { rows.map { row -> async { row.toCommunityPost() } }.awaitAll() }
        }.recoverCatching {
            val rows = supabase.postgrest.rpc(
                function = "community_post_page_v2",
                parameters = buildJsonObject {
                    cursor?.let {
                        put("p_before_created_at", it.createdAt)
                        put("p_before_id", it.id)
                    }
                    put("p_limit", serverLimit)
                },
            ).decodeList<CommunityFeedRow>()
            coroutineScope { rows.map { row -> async { row.toCommunityPost() } }.awaitAll() }
        }

        val pagePosts = remotePosts.fold(
            onSuccess = { posts ->
                if (posts.isNotEmpty()) {
                    cachedPostDao.insertPosts(posts.map { it.toCachedEntity() })
                }
                posts
            },
            onFailure = {
                postsAfterCommunityCursor(
                    posts = cachedPostDao.getAllPosts().map { it.toCommunityPost() },
                    cursor = cursor,
                )
            },
        )
        buildCommunityFeedPage(posts = pagePosts, visibleLimit = visibleLimit)
    }

    override suspend fun loadPost(postId: String): Result<CommunityPost?> = runCatching {
        val row = supabase.from("community_post_feed").select {
            filter { eq("id", postId) }
            limit(1)
        }.decodeList<CommunityFeedRow>().firstOrNull()
        val remotePost = row?.toCommunityPost()
        if (remotePost != null) cachedPostDao.insertPost(remotePost.toCachedEntity())
        remotePost
    }

    override suspend fun loadComments(postId: String): Result<List<CommunityComment>> = runCatching {
        val rows = supabase.from("community_comment_feed").select {
            filter { eq("post_id", postId) }
            order(column = "created_at", order = Order.ASCENDING)
        }.decodeList<CommunityCommentRow>()
        val remoteComments = coroutineScope { rows.map { row -> async { row.toCommunityComment() } }.awaitAll() }
        if (remoteComments.isNotEmpty()) cachedCommentDao.insertComments(remoteComments.map { it.toCachedEntity() })
        remoteComments
    }

    override suspend fun createComment(postId: String, body: String, parentId: String?): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }
        val createdId = supabase.postgrest.rpc(
            function = "create_community_comment",
            parameters = buildJsonObject {
                put("p_post_id", postId)
                put("p_body", cleanBody)
                parentId?.let { put("p_parent_id", it) }
            },
        ).decodeSingle<String>()
        require(createdId.isNotBlank()) { "The server did not confirm the new comment." }
        Unit
    }

    override suspend fun updateComment(commentId: String, body: String): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }
        supabase.from("community_comments").update(CommunityCommentChangePayload(body = cleanBody)) {
            filter { eq("id", commentId) }
        }
        Unit
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        runCatching {
            supabase.from("community_comments").delete { filter { eq("id", commentId) } }
        }.recoverCatching {
            supabase.from("community_comments").update(
                CommunityCommentChangePayload(
                    state = "DELETED_BY_AUTHOR",
                    deletedAt = Instant.now(clock).toString(),
                )
            ) { filter { eq("id", commentId) } }
        }.getOrThrow()
        cachedCommentDao.deleteComment(commentId)
        Unit
    }

    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        runCatching {
            supabase.from("community_posts").delete { filter { eq("id", postId) } }
        }.recoverCatching {
            supabase.from("community_posts").update(
                buildJsonObject {
                    put("state", "DELETED_BY_AUTHOR")
                    put("deleted_at", Instant.now(clock).toString())
                }
            ) { filter { eq("id", postId) } }
        }.getOrThrow()
        cachedPostDao.deletePost(postId)
        cachedCommentDao.deleteCommentsForPost(postId)
        Unit
    }

    override suspend fun moderateComment(commentId: String, reason: String): Result<Unit> = runCatching {
        val cleanReason = reason.trim()
        require(cleanReason.length in 3..1_000) { "A moderation reason must contain 3 to 1,000 characters." }
        val confirmed = supabase.postgrest.rpc(
            function = "moderate_community_comment_v1",
            parameters = buildJsonObject {
                put("p_comment_id", commentId)
                put("p_reason", cleanReason)
            },
        ).decodeSingle<Boolean>()
        require(confirmed) { "The server did not confirm comment moderation." }
        cachedCommentDao.deleteComment(commentId)
        Unit
    }

    override suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome> = runCatching {
        val outcome = supabase.postgrest.rpc(
            function = "toggle_community_post_like",
            parameters = buildJsonObject { put("p_post_id", postId) },
        ).decodeList<CommunityPostLikeOutcomeRow>().singleOrNull()
            ?: error("The server did not return a like outcome.")
        CommunityLikeOutcome(liked = outcome.liked, reactionCount = outcome.likeCount.coerceAtLeast(0))
    }

    override suspend fun toggleReaction(postId: String, emoji: String): Result<CommunityLikeOutcome> = runCatching {
        val outcome = supabase.postgrest.rpc(
            function = "toggle_community_post_reaction",
            parameters = buildJsonObject {
                put("p_post_id", postId)
                put("p_emoji", emoji)
            },
        ).decodeList<CommunityPostLikeOutcomeRow>().singleOrNull()
            ?: error("The server did not return a reaction outcome.")
        CommunityLikeOutcome(liked = outcome.liked, reactionCount = outcome.likeCount.coerceAtLeast(0))
    }

    override suspend fun repostPost(postId: String): Result<Pair<Boolean, Int>> = runCatching {
        val outcome = supabase.postgrest.rpc(
            function = "repost_community_post",
            parameters = buildJsonObject { put("p_post_id", postId) },
        ).decodeList<CommunityRepostOutcomeRow>().singleOrNull()
            ?: error("The server did not return a repost outcome.")
        outcome.reposted to outcome.repostCount.coerceAtLeast(0)
    }

    override suspend fun toggleBookmark(postId: String): Result<Pair<Boolean, Int>> = runCatching {
        val shouldBookmark = cachedPostDao.getPostById(postId)?.isBookmarkedByViewer != true
        val outcome = supabase.postgrest.rpc(
            function = if (shouldBookmark) "bookmark_community_post" else "unbookmark_community_post",
            parameters = buildJsonObject { put("p_post_id", postId) },
        ).decodeList<CommunityBookmarkOutcomeRow>().singleOrNull()
            ?: error("The server did not return a bookmark outcome.")
        outcome.bookmarked to outcome.bookmarkCount.coerceAtLeast(0)
    }

    override suspend fun searchPosts(
        query: String,
        lastRank: Float?,
        lastId: String?,
        limit: Int,
    ): Result<List<CommunityPost>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        runCatching {
            val rows = supabase.postgrest.rpc(
                function = "search_community_posts_cursor",
                parameters = buildJsonObject {
                    put("p_query", query.trim())
                    lastRank?.let { put("p_last_rank", it) }
                    lastId?.let { put("p_last_id", it) }
                    put("p_limit", limit)
                }
            ).decodeList<CommunityFeedRow>()
            coroutineScope { rows.map { row -> async { row.toCommunityPost() } }.awaitAll() }
        }.recoverCatching {
            val rows = supabase.postgrest.rpc(
                function = "search_community_posts",
                parameters = buildJsonObject {
                    put("p_query", query.trim())
                    put("p_limit", limit)
                }
            ).decodeList<CommunityFeedRow>()
            coroutineScope { rows.map { row -> async { row.toCommunityPost() } }.awaitAll() }
        }.getOrThrow()
    }

    override fun observeNotificationEvents(userId: String): Flow<CommunityRealtimeNotification> = callbackFlow {
        val channel = supabase.realtime.channel("user-notifications-$userId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "notification_events"
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            changeFlow.collect { action ->
                if (action is PostgresAction.Insert) {
                    runCatching {
                        val row = Json { ignoreUnknownKeys = true }.decodeFromString<RealtimeNotificationPayload>(action.record.toString())
                        if (row.recipientId == userId || userId.isBlank()) {
                            trySend(
                                CommunityRealtimeNotification(
                                    id = row.id,
                                    recipientId = row.recipientId,
                                    type = row.notificationType,
                                    title = row.title,
                                    body = row.body,
                                    createdAt = row.createdAt,
                                )
                            )
                        }
                    }
                }
            }
        }
        channel.subscribe()
        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                channel.unsubscribe()
            }
        }
    }

    override fun observeCommunityFeedRealtime(): Flow<String> = callbackFlow {
        val channel = supabase.realtime.channel("public-feed-stream")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "community_posts"
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            changeFlow.collect { action ->
                if (action is PostgresAction.Insert) {
                    val id = action.record["id"]?.toString()?.trim('"') ?: ""
                    if (id.isNotBlank()) {
                        trySend(id)
                    }
                }
            }
        }
        channel.subscribe()
        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                channel.unsubscribe()
            }
        }
    }

    override suspend fun getHashtagAutocomplete(prefix: String): Result<List<String>> = runCatching {
        val cleanPrefix = prefix.trim().removePrefix("#")
        supabase.postgrest.rpc(
            function = "autocomplete_hashtags",
            parameters = buildJsonObject {
                put("p_prefix", cleanPrefix)
                put("p_limit", 10)
            }
        ).decodeList<HashtagRow>().map { "#${it.tag}" }
    }

    override suspend fun getMentionAutocomplete(prefix: String): Result<List<String>> = runCatching {
        val cleanPrefix = prefix.trim().removePrefix("@")
        supabase.postgrest.rpc(
            function = "autocomplete_mentions",
            parameters = buildJsonObject {
                put("p_prefix", cleanPrefix)
                put("p_limit", 10)
            }
        ).decodeList<MentionRow>().map { "@${it.handle}" }
    }

    override suspend fun refreshMediaUrl(mediaId: String): Result<String?> = runCatching {
        val path = mediaPaths[mediaId] ?: return@runCatching null
        signedUrlCache.invalidate(mediaCacheKey(mediaId))
        issueMediaUrl(mediaId, path)
    }

    private suspend fun CommunityFeedRow.toCommunityPost(): CommunityPost {
        val mappedMedia = coroutineScope {
            media.sortedBy(CommunityEmbeddedMediaRow::position).map { row ->
                async { row.toMediaItem(postId = id) }
            }.awaitAll()
        }
        return CommunityPost(
            id = id,
            author = authorName,
            handle = "@${authorHandle.removePrefix("@")}",
            content = body,
            category = categoryLabel ?: if (staffBadge) "Official Community" else "Community",
            createdAt = createdAt,
            reactions = reactionCount,
            comments = commentCount,
            viewerHasLiked = viewerHasLiked,
            trendingScore = trendingScore.coerceAtLeast(0),
            isFollowedTopic = isFollowedTopic,
            hasMedia = mappedMedia.isNotEmpty(),
            media = mappedMedia,
            isOfficial = staffBadge,
            authorId = authorId,
            authorAvatarUrl = avatarPath?.let { signedAvatarUrl(it, avatarUpdatedAt) },
            isLocked = isLocked,
            editedAt = editedAt,
            repostOfId = repostOfId,
            quotePostId = quotePostId,
            repostCount = repostCount,
            bookmarkCount = bookmarkCount,
            isRepostedByViewer = isRepostedByViewer,
            isBookmarkedByViewer = isBookmarkedByViewer,
        )
    }

    private suspend fun CommunityCommentRow.toCommunityComment(): CommunityComment = CommunityComment(
        id = id,
        postId = postId,
        authorId = authorId,
        author = authorName,
        handle = "@${authorHandle.removePrefix("@")}",
        authorAvatarUrl = avatarPath?.let { signedAvatarUrl(it, avatarUpdatedAt) },
        content = body,
        createdAt = createdAt,
        editedAt = editedAt,
        isStaff = staffBadge,
        parentId = parentCommentId ?: parentId,
        replyCount = replyCount,
        depth = depth,
    )

    private suspend fun CommunityEmbeddedMediaRow.toMediaItem(postId: String): MediaItem {
        mediaPaths[id] = storagePath
        return MediaItem(
            id = id,
            targetType = MediaTargetType.COMMUNITY_POST,
            targetId = postId,
            storagePath = storagePath,
            kind = runCatching { MediaKind.valueOf(mediaKind) }.getOrDefault(MediaKind.IMAGE),
            mimeType = mimeType,
            byteSize = byteSize,
            width = width,
            height = height,
            durationSeconds = durationSeconds,
            position = position,
            caption = caption,
            signedUrl = issueMediaUrl(id, storagePath),
        )
    }

    private suspend fun issueMediaUrl(mediaId: String, storagePath: String): String? =
        signedUrlCache.getOrIssue(mediaCacheKey(mediaId)) {
            val url = supabase.storage.from(COMMUNITY_MEDIA_BUCKET).createSignedUrl(storagePath, SIGNED_URL_TTL)
            SignedUrlValue(
                url = url,
                expiresAt = clock.instant().plusSeconds(SIGNED_URL_TTL.inWholeSeconds),
            )
        }

    private suspend fun signedAvatarUrl(path: String, revision: String?): String {
        val stableRevision = revision?.hashCode()?.toUInt()?.toString(16)
            ?: path.hashCode().toUInt().toString(16)
        val key = "avatar:$path:$stableRevision"
        return requireNotNull(
            signedUrlCache.getOrIssue(key) {
                val signed = supabase.storage.from(PROFILE_MEDIA_BUCKET).createSignedUrl(path, SIGNED_URL_TTL)
                SignedUrlValue(
                    url = "$signed${if (signed.contains("?")) "&" else "?"}v=$stableRevision",
                    expiresAt = clock.instant().plusSeconds(SIGNED_URL_TTL.inWholeSeconds),
                )
            }
        )
    }

    private fun mediaCacheKey(mediaId: String): String = "media:$mediaId"

    private companion object {
        const val COMMUNITY_MEDIA_BUCKET = "rtc-community-media"
        const val PROFILE_MEDIA_BUCKET = "rtc-profile-media"
        const val MAX_VISIBLE_PAGE_SIZE = 49
        const val MAX_SERVER_PAGE_SIZE = 50
        const val SIGNED_URL_CACHE_CAPACITY = 192
        const val SIGNED_URL_REFRESH_SKEW_SECONDS = 90L
        val SIGNED_URL_TTL = 60.minutes
    }
}

@Serializable
private data class CommunityPostLikeOutcomeRow(
    val liked: Boolean,
    @SerialName("like_count") val likeCount: Int,
)

@Serializable
private data class CommunityRepostOutcomeRow(
    val reposted: Boolean,
    @SerialName("repost_count") val repostCount: Int = 0,
)

@Serializable
private data class CommunityBookmarkOutcomeRow(
    val bookmarked: Boolean,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
)

@Serializable
private data class HashtagRow(
    val tag: String,
    @SerialName("post_count") val postCount: Long = 0,
)

@Serializable
private data class MentionRow(
    val id: String,
    val handle: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
)

@Serializable
private data class CommunityFeedRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_handle") val authorHandle: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("avatar_updated_at") val avatarUpdatedAt: String? = null,
    @SerialName("staff_badge") val staffBadge: Boolean = false,
    val body: String,
    @SerialName("category_label") val categoryLabel: String? = null,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("viewer_has_liked") val viewerHasLiked: Boolean = false,
    @SerialName("trending_score") val trendingScore: Int = 0,
    @SerialName("is_followed_topic") val isFollowedTopic: Boolean = false,
    @SerialName("repost_of_id") val repostOfId: String? = null,
    @SerialName("quote_post_id") val quotePostId: String? = null,
    @SerialName("repost_count") val repostCount: Int = 0,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
    @SerialName("is_reposted_by_viewer") val isRepostedByViewer: Boolean = false,
    @SerialName("is_bookmarked_by_viewer") val isBookmarkedByViewer: Boolean = false,
    val media: List<CommunityEmbeddedMediaRow> = emptyList(),
)

@Serializable
private data class CommunityCommentRow(
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
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("parent_comment_id") val parentCommentId: String? = null,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("depth") val depth: Int = 0,
)

@Serializable
private data class CommunityCommentChangePayload(
    val body: String? = null,
    val state: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
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
private data class RealtimeNotificationPayload(
    val id: String = "",
    @SerialName("recipient_id") val recipientId: String = "",
    @SerialName("notification_type") val notificationType: String = "",
    val title: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
)