package za.org.rtc.community.feature.community

import za.org.rtc.community.core.CommunityPost

data class CommunityLikeOutcome(
    val liked: Boolean,
    val reactionCount: Int,
)

internal fun CommunityPost.optimisticLikeToggle(): CommunityPost {
    val nextLiked = !viewerHasLiked
    val nextCount = if (nextLiked) reactions + 1 else (reactions - 1).coerceAtLeast(0)
    val nextUserReactions = if (nextLiked) userReactions + "❤️" else userReactions - "❤️"
    val nextReactionCounts = reactionCounts.toMutableMap().apply {
        val current = get("❤️") ?: 0
        if (nextLiked) put("❤️", current + 1) else {
            val updated = (current - 1).coerceAtLeast(0)
            if (updated > 0) put("❤️", updated) else remove("❤️")
        }
    }
    return copy(
        viewerHasLiked = nextLiked,
        userReactions = nextUserReactions,
        reactionCounts = nextReactionCounts,
        reactions = nextCount,
    )
}

internal fun CommunityPost.optimisticReactionToggle(emoji: String): CommunityPost {
    val isCurrentlyActive = emoji in userReactions
    val nextUserReactions = if (isCurrentlyActive) userReactions - emoji else userReactions + emoji
    val nextCounts = reactionCounts.toMutableMap().apply {
        val current = get(emoji) ?: 0
        if (isCurrentlyActive) {
            val updated = (current - 1).coerceAtLeast(0)
            if (updated > 0) put(emoji, updated) else remove(emoji)
        } else {
            put(emoji, current + 1)
        }
    }
    val totalReactions = nextCounts.values.sum()
    val isHeartLiked = "❤️" in nextUserReactions || "👍" in nextUserReactions
    return copy(
        viewerHasLiked = isHeartLiked,
        userReactions = nextUserReactions,
        reactionCounts = nextCounts,
        reactions = totalReactions,
    )
}

internal fun CommunityPost.withLikeOutcome(outcome: CommunityLikeOutcome): CommunityPost = copy(
    viewerHasLiked = outcome.liked,
    reactions = outcome.reactionCount.coerceAtLeast(0),
)

internal fun CommunityPost.optimisticRepostToggle(): CommunityPost {
    val nextReposted = !isRepostedByViewer
    val nextCount = if (nextReposted) repostCount + 1 else (repostCount - 1).coerceAtLeast(0)
    return copy(
        isRepostedByViewer = nextReposted,
        repostCount = nextCount,
    )
}

internal fun CommunityPost.optimisticBookmarkToggle(): CommunityPost {
    val nextBookmarked = !isBookmarkedByViewer
    val nextCount = if (nextBookmarked) bookmarkCount + 1 else (bookmarkCount - 1).coerceAtLeast(0)
    return copy(
        isBookmarkedByViewer = nextBookmarked,
        bookmarkCount = nextCount,
    )
}

