from pathlib import Path

COMMUNITY = Path("app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt")
RTC = Path("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt")


def replace_between(text: str, start: str, end: str, replacement: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"missing start marker: {start}")
    j = text.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f"missing end marker after {start}: {end}")
    return text[:i] + replacement.rstrip() + "\n\n    " + text[j:]


community = COMMUNITY.read_text(encoding="utf-8")
for obsolete in (
    "import android.content.Context\n",
    "import dagger.hilt.android.qualifiers.ApplicationContext\n",
    "import java.util.UUID\n",
    "    @ApplicationContext private val context: Context,\n",
):
    community = community.replace(obsolete, "")

community = replace_between(
    community,
    "override suspend fun loadFeedPage(",
    "override suspend fun loadPost(",
    '''override suspend fun loadFeedPage(
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
        }.getOrThrow()

        if (remotePosts.isNotEmpty()) {
            cachedPostDao.insertPosts(remotePosts.map { it.toCachedEntity() })
        }
        buildCommunityFeedPage(posts = remotePosts, visibleLimit = visibleLimit)
    }''',
)

community = replace_between(
    community,
    "override suspend fun loadPost(",
    "override suspend fun loadComments(",
    '''override suspend fun loadPost(postId: String): Result<CommunityPost?> = runCatching {
        val row = supabase.from("community_post_feed").select {
            filter { eq("id", postId) }
            limit(1)
        }.decodeList<CommunityFeedRow>().firstOrNull()
        val remotePost = row?.toCommunityPost()
        if (remotePost != null) cachedPostDao.insertPost(remotePost.toCachedEntity())
        remotePost
    }''',
)

community = replace_between(
    community,
    "override suspend fun loadComments(",
    "override suspend fun createComment(",
    '''override suspend fun loadComments(postId: String): Result<List<CommunityComment>> = runCatching {
        val rows = supabase.from("community_comment_feed").select {
            filter { eq("post_id", postId) }
            order(column = "created_at", order = Order.ASCENDING)
        }.decodeList<CommunityCommentRow>()
        val remoteComments = coroutineScope { rows.map { row -> async { row.toCommunityComment() } }.awaitAll() }
        if (remoteComments.isNotEmpty()) cachedCommentDao.insertComments(remoteComments.map { it.toCachedEntity() })
        remoteComments
    }''',
)

community = replace_between(
    community,
    "override suspend fun createComment(",
    "override suspend fun updateComment(",
    '''override suspend fun createComment(postId: String, body: String, parentId: String?): Result<Unit> = runCatching {
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
    }''',
)

community = replace_between(
    community,
    "override suspend fun updateComment(",
    "override suspend fun deleteComment(",
    '''override suspend fun updateComment(commentId: String, body: String): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }
        supabase.from("community_comments").update(CommunityCommentChangePayload(body = cleanBody)) {
            filter { eq("id", commentId) }
        }
        Unit
    }''',
)

community = replace_between(
    community,
    "override suspend fun deleteComment(",
    "override suspend fun deletePost(",
    '''override suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
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
    }''',
)

community = replace_between(
    community,
    "override suspend fun deletePost(",
    "override suspend fun moderateComment(",
    '''override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
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
    }''',
)

community = replace_between(
    community,
    "override suspend fun moderateComment(",
    "override suspend fun toggleLike(",
    '''override suspend fun moderateComment(commentId: String, reason: String): Result<Unit> = runCatching {
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
    }''',
)

community = replace_between(
    community,
    "override suspend fun toggleLike(",
    "override suspend fun toggleReaction(",
    '''override suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome> = runCatching {
        val outcome = supabase.postgrest.rpc(
            function = "toggle_community_post_like",
            parameters = buildJsonObject { put("p_post_id", postId) },
        ).decodeList<CommunityPostLikeOutcomeRow>().singleOrNull()
            ?: error("The server did not return a like outcome.")
        CommunityLikeOutcome(liked = outcome.liked, reactionCount = outcome.likeCount.coerceAtLeast(0))
    }''',
)

community = replace_between(
    community,
    "override suspend fun toggleReaction(",
    "override suspend fun repostPost(",
    '''override suspend fun toggleReaction(postId: String, emoji: String): Result<CommunityLikeOutcome> = runCatching {
        val outcome = supabase.postgrest.rpc(
            function = "toggle_community_post_reaction",
            parameters = buildJsonObject {
                put("p_post_id", postId)
                put("p_emoji", emoji)
            },
        ).decodeList<CommunityPostLikeOutcomeRow>().singleOrNull()
            ?: error("The server did not return a reaction outcome.")
        CommunityLikeOutcome(liked = outcome.liked, reactionCount = outcome.likeCount.coerceAtLeast(0))
    }''',
)

community = replace_between(
    community,
    "override suspend fun repostPost(",
    "override suspend fun toggleBookmark(",
    '''override suspend fun repostPost(postId: String): Result<Pair<Boolean, Int>> = runCatching {
        val outcome = supabase.postgrest.rpc(
            function = "repost_community_post",
            parameters = buildJsonObject { put("p_post_id", postId) },
        ).decodeList<CommunityRepostOutcomeRow>().singleOrNull()
            ?: error("The server did not return a repost outcome.")
        outcome.reposted to outcome.repostCount.coerceAtLeast(0)
    }''',
)

community = replace_between(
    community,
    "override suspend fun toggleBookmark(",
    "override suspend fun searchPosts(",
    '''override suspend fun toggleBookmark(postId: String): Result<Pair<Boolean, Int>> = runCatching {
        val shouldBookmark = cachedPostDao.getPostById(postId)?.isBookmarkedByViewer != true
        val outcome = supabase.postgrest.rpc(
            function = if (shouldBookmark) "bookmark_community_post" else "unbookmark_community_post",
            parameters = buildJsonObject { put("p_post_id", postId) },
        ).decodeList<CommunityBookmarkOutcomeRow>().singleOrNull()
            ?: error("The server did not return a bookmark outcome.")
        outcome.bookmarked to outcome.bookmarkCount.coerceAtLeast(0)
    }''',
)

community = replace_between(
    community,
    "override suspend fun searchPosts(",
    "override fun observeNotificationEvents(",
    '''override suspend fun searchPosts(
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
    }''',
)

community = replace_between(
    community,
    "override suspend fun getHashtagAutocomplete(",
    "override suspend fun getMentionAutocomplete(",
    '''override suspend fun getHashtagAutocomplete(prefix: String): Result<List<String>> = runCatching {
        val cleanPrefix = prefix.trim().removePrefix("#")
        supabase.postgrest.rpc(
            function = "autocomplete_hashtags",
            parameters = buildJsonObject {
                put("p_prefix", cleanPrefix)
                put("p_limit", 10)
            }
        ).decodeList<HashtagRow>().map { "#${it.tag}" }
    }''',
)

community = replace_between(
    community,
    "override suspend fun getMentionAutocomplete(",
    "override suspend fun refreshMediaUrl(",
    '''override suspend fun getMentionAutocomplete(prefix: String): Result<List<String>> = runCatching {
        val cleanPrefix = prefix.trim().removePrefix("@")
        supabase.postgrest.rpc(
            function = "autocomplete_mentions",
            parameters = buildJsonObject {
                put("p_prefix", cleanPrefix)
                put("p_limit", 10)
            }
        ).decodeList<MentionRow>().map { "@${it.handle}" }
    }''',
)

if "CommunityMockData" in community or "UUID.randomUUID" in community or "@ApplicationContext" in community:
    raise SystemExit("community production source still contains fabricated-data dependencies")
COMMUNITY.write_text(community, encoding="utf-8")

rtc = RTC.read_text(encoding="utf-8")
rtc = replace_between(
    rtc,
    "private suspend fun administratorMfaStatus(",
    "private fun requireStrongPassword(",
    '''private suspend fun administratorMfaStatus(role: UserRole): AdministratorMfaStatus {
        if (role != UserRole.SYSTEM_ADMIN) return AdministratorMfaStatus.NOT_REQUIRED
        val factors = try {
            supabase.auth.mfa.retrieveFactorsForCurrentUser()
        } catch (_: Throwable) {
            return AdministratorMfaStatus.VERIFICATION_REQUIRED
        }
        if (factors.none { it.isVerified }) return AdministratorMfaStatus.ENROLLMENT_REQUIRED
        val accessToken = supabase.auth.currentSessionOrNull()?.accessToken
            ?: return AdministratorMfaStatus.VERIFICATION_REQUIRED
        val assurance = try {
            supabase.auth.mfa.getAuthenticatorAssuranceLevel(accessToken)
        } catch (_: Throwable) {
            return AdministratorMfaStatus.VERIFICATION_REQUIRED
        }
        return if (assurance.current == AuthenticatorAssuranceLevel.AAL2) {
            AdministratorMfaStatus.VERIFIED
        } else {
            AdministratorMfaStatus.VERIFICATION_REQUIRED
        }
    }''',
)
RTC.write_text(rtc, encoding="utf-8")
