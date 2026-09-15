package za.org.rtc.community.feature.community

import java.time.Instant
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard

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
    val lastInteractionEpochMillis: Long = System.currentTimeMillis() - 300_000L,
    val unreadPostIds: Set<String> = emptySet(),
    val publicReports: List<PublicReport> = emptyList(),
    val publicReportsDashboard: PublicReportDashboard? = null,
    val publicReportsLoading: Boolean = false,
    val publicReportsLastFetched: Instant? = null,
    val publicReportsError: String? = null,
) {
    val unreadCount: Int get() = unreadPostIds.size

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

        val updatedUnread = computeUnreadPostIds(merged, lastInteractionEpochMillis, unreadPostIds)

        return copy(
            items = merged,
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            initialLoading = false,
            refreshing = false,
            appendLoading = false,
            initialError = null,
            appendError = null,
            unreadPostIds = updatedUnread,
            hasNewPosts = updatedUnread.isNotEmpty(),
        )
    }
}

internal fun computeUnreadPostIds(
    posts: List<CommunityPost>,
    lastInteractionEpochMillis: Long,
    existingUnreadPostIds: Set<String> = emptySet(),
): Set<String> {
    val unread = existingUnreadPostIds.toMutableSet()
    for (post in posts) {
        val postCreatedEpoch = za.org.rtc.community.core.TimeFormatters.parseToEpochMillis(post.createdAt)
        if (postCreatedEpoch > lastInteractionEpochMillis) {
            unread.add(post.id)
        }
    }
    return unread
}
