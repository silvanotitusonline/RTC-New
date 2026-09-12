package za.org.rtc.community.feature.community

import java.time.Instant
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.data.local.CachedCommentEntity
import za.org.rtc.community.data.local.CachedPostEntity

fun CommunityPost.toCachedEntity(): CachedPostEntity = CachedPostEntity(
    id = id,
    author = author,
    handle = handle,
    content = content,
    category = category,
    createdAt = createdAt,
    reactions = reactions,
    comments = comments,
    viewerHasLiked = viewerHasLiked,
    userReactionsJson = if (userReactions.isEmpty()) null else userReactions.joinToString(","),
    reactionCountsJson = if (reactionCounts.isEmpty()) null else reactionCounts.entries.joinToString(";") { "${it.key}:${it.value}" },
    trendingScore = trendingScore,
    isOfficial = isOfficial,
    authorId = authorId,
    authorAvatarUrl = authorAvatarUrl,
    mediaJson = null,
    createdAtEpochMillis = try { Instant.parse(createdAt).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() },
    repostOfId = repostOfId,
    quotePostId = quotePostId,
    repostCount = repostCount,
    bookmarkCount = bookmarkCount,
    isRepostedByViewer = isRepostedByViewer,
    isBookmarkedByViewer = isBookmarkedByViewer,
)

fun CachedPostEntity.toCommunityPost(): CommunityPost {
    val reactionsSet = userReactionsJson?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    val countsMap = reactionCountsJson?.split(";")?.mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
    }?.toMap() ?: emptyMap()

    return CommunityPost(
        id = id,
        author = author,
        handle = handle,
        content = content,
        category = category,
        createdAt = createdAt,
        reactions = reactions,
        comments = comments,
        viewerHasLiked = viewerHasLiked,
        userReactions = reactionsSet,
        reactionCounts = countsMap,
        trendingScore = trendingScore,
        isFollowedTopic = true,
        hasMedia = false,
        media = emptyList(),
        isOfficial = isOfficial,
        authorId = authorId ?: "",
        authorAvatarUrl = authorAvatarUrl,
        repostOfId = repostOfId,
        quotePostId = quotePostId,
        repostCount = repostCount,
        bookmarkCount = bookmarkCount,
        isRepostedByViewer = isRepostedByViewer,
        isBookmarkedByViewer = isBookmarkedByViewer,
    )
}

fun CommunityComment.toCachedEntity(): CachedCommentEntity = CachedCommentEntity(
    id = id,
    postId = postId,
    authorId = authorId,
    author = author,
    handle = handle,
    content = content,
    createdAt = createdAt,
    authorAvatarUrl = authorAvatarUrl,
    createdAtEpochMillis = try { Instant.parse(createdAt).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() },
    parentId = parentId,
    replyCount = replyCount,
    depth = depth,
)

fun CachedCommentEntity.toCommunityComment(): CommunityComment = CommunityComment(
    id = id,
    postId = postId,
    authorId = authorId,
    author = author,
    handle = handle,
    authorAvatarUrl = authorAvatarUrl,
    content = content,
    createdAt = createdAt,
    isStaff = false,
    parentId = parentId,
    replyCount = replyCount,
    depth = depth,
)
