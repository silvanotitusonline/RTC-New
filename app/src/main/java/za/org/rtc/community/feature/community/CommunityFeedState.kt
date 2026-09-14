package za.org.rtc.community.feature.community

import java.time.Instant
import za.org.rtc.community.core.CommunityPost

internal const val COMMUNITY_FEED_PAGE_SIZE = 20

data class CommunityCursor(
    val createdAt: String,
    val id: String,
)

data class CommunityFeedPage(
    val items: List<CommunityPost>,
    val nextCursor: CommunityCursor?,
    val hasMore: Boolean,
)

internal fun buildCommunityFeedPage(
    posts: List<CommunityPost>,
    visibleLimit: Int,
): CommunityFeedPage {
    require(visibleLimit > 0) { "Community feed page size must be positive." }
    val visible = posts.take(visibleLimit).distinctBy(CommunityPost::id)
    val hasMore = posts.size > visibleLimit
    val cursor = if (hasMore) {
        visible.lastOrNull()?.let { CommunityCursor(createdAt = it.createdAt, id = it.id) }
    } else {
        null
    }
    return CommunityFeedPage(
        items = visible,
        nextCursor = cursor,
        hasMore = hasMore,
    )
}

/**
 * Applies the same composite keyset boundary used by `community_post_page_v2/v3` to cached posts.
 *
 * PostgreSQL pages by `(created_at, id) < (cursor.created_at, cursor.id)` while ordering both
 * columns descending. Keeping the identical boundary in the Room fallback prevents an append
 * request from restarting at page one when the network RPC is temporarily unavailable.
 */
internal fun postsAfterCommunityCursor(
    posts: List<CommunityPost>,
    cursor: CommunityCursor?,
): List<CommunityPost> {
    val ordered = posts.sortedWith { left, right ->
        val createdAtComparison = compareCommunityCreatedAt(right.createdAt, left.createdAt)
        if (createdAtComparison != 0) {
            createdAtComparison
        } else {
            right.id.compareTo(left.id)
        }
    }

    if (cursor == null) return ordered

    return ordered.filter { post ->
        val createdAtComparison = compareCommunityCreatedAt(post.createdAt, cursor.createdAt)
        createdAtComparison < 0 ||
            (createdAtComparison == 0 && post.id < cursor.id)
    }
}

private fun compareCommunityCreatedAt(left: String, right: String): Int {
    val leftInstant = runCatching { Instant.parse(left) }.getOrNull()
    val rightInstant = runCatching { Instant.parse(right) }.getOrNull()
    return if (leftInstant != null && rightInstant != null) {
        leftInstant.compareTo(rightInstant)
    } else {
        left.compareTo(right)
    }
}

data class CommunityFeedState(
    val items: List<CommunityPost> = emptyList(),
    val nextCursor: CommunityCursor? = null,
    val hasMore: Boolean = false,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val appendLoading: Boolean = false,
    val initialError: String? = null,
    val appendError: String? = null,
    val mutationError: String? = null,
    val searchQuery: String = "",
    val searchResults: List<CommunityPost>? = null,
    val isSearching: Boolean = false,
    val hasNewPosts: Boolean = false,
) {
    fun withPage(page: CommunityFeedPage, append: Boolean): CommunityFeedState {
        val merged = if (append) {
            buildList {
                val seen = HashSet<String>(items.size + page.items.size)
                items.forEach { post -> if (seen.add(post.id)) add(post) }
                page.items.forEach { post -> if (seen.add(post.id)) add(post) }
            }
        } else {
            page.items.distinctBy(CommunityPost::id)
        }

        return copy(
            items = merged,
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            initialLoading = false,
            refreshing = false,
            appendLoading = false,
            initialError = null,
            appendError = null,
        )
    }
}
