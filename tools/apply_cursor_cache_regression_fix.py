from pathlib import Path

PATH = Path("app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt")
text = PATH.read_text(encoding="utf-8")
start = text.index("    override suspend fun loadFeedPage(")
end = text.index("    override suspend fun loadPost(", start)
replacement = '''    override suspend fun loadFeedPage(
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

'''
PATH.write_text(text[:start] + replacement + text[end:], encoding="utf-8")
