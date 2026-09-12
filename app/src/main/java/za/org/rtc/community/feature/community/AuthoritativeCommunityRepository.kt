package za.org.rtc.community.feature.community

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.data.local.CachedCommentDao
import za.org.rtc.community.data.local.CachedPostDao

/**
 * Production safety decorator for CommunityRepository.
 *
 * Mutations may be optimistic for responsiveness, but local state is never accepted as the final
 * result. A mutation succeeds only after the authoritative Supabase operation succeeds. Where an
 * optimistic snapshot is used it is reconciled to server truth, and restored when the server
 * rejects the operation.
 */
@Singleton
class AuthoritativeCommunityRepository @Inject constructor(
    private val delegate: SupabaseCommunityRepository,
    private val supabase: SupabaseClient,
    private val cachedPostDao: CachedPostDao,
    private val cachedCommentDao: CachedCommentDao,
) : CommunityRepository by delegate {

    override suspend fun createComment(
        postId: String,
        body: String,
        parentId: String?,
    ): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }

        supabase.postgrest.rpc(
            function = "create_community_comment",
            parameters = buildJsonObject {
                put("p_post_id", postId)
                put("p_body", cleanBody)
                parentId?.let { put("p_parent_comment_id", it) }
            },
        ).decodeSingle<String>()

        // The server owns comment identity and counts. Refresh only after acknowledgement so Room
        // cannot contain a comment that the server never accepted.
        delegate.loadPost(postId).getOrThrow()
        delegate.loadComments(postId).getOrThrow()
        Unit
    }

    override suspend fun updateComment(commentId: String, body: String): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }
        supabase.postgrest.rpc(
            function = "edit_community_comment",
            parameters = buildJsonObject {
                put("p_comment_id", commentId)
                put("p_body", cleanBody)
            },
        )
        Unit
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = "delete_community_comment",
            parameters = buildJsonObject { put("p_comment_id", commentId) },
        )
        cachedCommentDao.deleteComment(commentId)
        Unit
    }

    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = "delete_community_post",
            parameters = buildJsonObject { put("p_post_id", postId) },
        )
        cachedPostDao.deletePost(postId)
        cachedCommentDao.deleteCommentsForPost(postId)
        Unit
    }

    override suspend fun moderateComment(commentId: String, reason: String): Result<Unit> = runCatching {
        val cleanReason = reason.trim()
        require(cleanReason.length in 3..1_000) {
            "A moderation reason must contain 3 to 1,000 characters."
        }
        val accepted = supabase.postgrest.rpc(
            function = "moderate_community_comment_v1",
            parameters = buildJsonObject {
                put("p_comment_id", commentId)
                put("p_reason", cleanReason)
            },
        ).decodeSingle<Boolean>()
        check(accepted) { "The moderation action was not confirmed by the server." }
        cachedCommentDao.deleteComment(commentId)
        Unit
    }

    override suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome> = runCatching {
        val original = cachedPostDao.getPostById(postId)

        runAuthoritativeOptimisticMutation(
            applyOptimistic = {
                original?.let { snapshot ->
                    cachedPostDao.insertPost(
                        snapshot.toCommunityPost().optimisticLikeToggle().toCachedEntity(),
                    )
                }
            },
            executeRemote = {
                val row = supabase.postgrest.rpc(
                    function = "toggle_community_post_like",
                    parameters = buildJsonObject {
                        put("p_post_id", postId)
                    },
                ).decodeList<AuthoritativeLikeOutcomeRow>().singleOrNull()
                    ?: error("toggle_community_post_like returned no authoritative outcome.")

                CommunityLikeOutcome(
                    liked = row.liked,
                    reactionCount = row.likeCount.coerceAtLeast(0),
                )
            },
            applyAuthoritative = { outcome ->
                val current = cachedPostDao.getPostById(postId)?.toCommunityPost()
                    ?: original?.toCommunityPost()
                current?.withLikeOutcome(outcome)?.let { post ->
                    cachedPostDao.insertPost(post.toCachedEntity())
                }
            },
            rollback = {
                original?.let { cachedPostDao.insertPost(it) }
            },
        )
    }

    override suspend fun toggleReaction(
        postId: String,
        emoji: String,
    ): Result<CommunityLikeOutcome> = runCatching {
        val original = cachedPostDao.getPostById(postId)

        runAuthoritativeOptimisticMutation(
            applyOptimistic = {
                original?.let { snapshot ->
                    cachedPostDao.insertPost(
                        snapshot.toCommunityPost().optimisticReactionToggle(emoji).toCachedEntity(),
                    )
                }
            },
            executeRemote = {
                val row = supabase.postgrest.rpc(
                    function = "toggle_community_post_reaction",
                    parameters = buildJsonObject {
                        put("p_post_id", postId)
                        put("p_emoji", emoji)
                    },
                ).decodeList<AuthoritativeLikeOutcomeRow>().singleOrNull()
                    ?: error("toggle_community_post_reaction returned no authoritative outcome.")

                CommunityLikeOutcome(
                    liked = row.liked,
                    reactionCount = row.likeCount.coerceAtLeast(0),
                )
            },
            applyAuthoritative = { outcome ->
                val current = cachedPostDao.getPostById(postId)?.toCommunityPost()
                    ?: original?.toCommunityPost()
                current?.withLikeOutcome(outcome)?.let { post ->
                    cachedPostDao.insertPost(post.toCachedEntity())
                }
            },
            rollback = {
                original?.let { cachedPostDao.insertPost(it) }
            },
        )
    }

    override suspend fun repostPost(postId: String): Result<Pair<Boolean, Int>> = runCatching {
        val original = cachedPostDao.getPostById(postId)

        runAuthoritativeOptimisticMutation(
            applyOptimistic = {
                original?.let { snapshot ->
                    cachedPostDao.insertPost(
                        snapshot.toCommunityPost().optimisticRepostToggle().toCachedEntity(),
                    )
                }
            },
            executeRemote = {
                val row = supabase.postgrest.rpc(
                    function = "repost_community_post",
                    parameters = buildJsonObject {
                        put("p_post_id", postId)
                    },
                ).decodeList<AuthoritativeRepostOutcomeRow>().firstOrNull()
                    ?: error("repost_community_post returned no authoritative outcome.")

                row.reposted to row.repostCount.coerceAtLeast(0)
            },
            applyAuthoritative = { (reposted, count) ->
                val current = cachedPostDao.getPostById(postId)?.toCommunityPost()
                    ?: original?.toCommunityPost()
                current?.copy(
                    isRepostedByViewer = reposted,
                    repostCount = count.coerceAtLeast(0),
                )?.let { post ->
                    cachedPostDao.insertPost(post.toCachedEntity())
                }
            },
            rollback = {
                original?.let { cachedPostDao.insertPost(it) }
            },
        )
    }

    override suspend fun toggleBookmark(postId: String): Result<Pair<Boolean, Int>> = runCatching {
        val original = cachedPostDao.getPostById(postId)
        val shouldBookmark = original?.isBookmarkedByViewer != true

        runAuthoritativeOptimisticMutation(
            applyOptimistic = {
                original?.let { snapshot ->
                    cachedPostDao.insertPost(
                        snapshot.toCommunityPost().optimisticBookmarkToggle().toCachedEntity(),
                    )
                }
            },
            executeRemote = {
                val rpcName = if (shouldBookmark) {
                    "bookmark_community_post"
                } else {
                    "unbookmark_community_post"
                }
                val row = supabase.postgrest.rpc(
                    function = rpcName,
                    parameters = buildJsonObject {
                        put("p_post_id", postId)
                    },
                ).decodeList<AuthoritativeBookmarkOutcomeRow>().firstOrNull()
                    ?: error("$rpcName returned no authoritative outcome.")

                row.bookmarked to row.bookmarkCount.coerceAtLeast(0)
            },
            applyAuthoritative = { (bookmarked, count) ->
                val current = cachedPostDao.getPostById(postId)?.toCommunityPost()
                    ?: original?.toCommunityPost()
                current?.copy(
                    isBookmarkedByViewer = bookmarked,
                    bookmarkCount = count.coerceAtLeast(0),
                )?.let { post ->
                    cachedPostDao.insertPost(post.toCachedEntity())
                }
            },
            rollback = {
                original?.let { cachedPostDao.insertPost(it) }
            },
        )
    }
}

@Serializable
private data class AuthoritativeLikeOutcomeRow(
    val liked: Boolean,
    @SerialName("like_count") val likeCount: Int,
)

@Serializable
private data class AuthoritativeRepostOutcomeRow(
    val reposted: Boolean,
    @SerialName("repost_count") val repostCount: Int = 0,
)

@Serializable
private data class AuthoritativeBookmarkOutcomeRow(
    val bookmarked: Boolean,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
)
