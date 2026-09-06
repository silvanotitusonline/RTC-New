package za.org.rtc.community.feature.community

import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost

interface CommunityRepository {
    suspend fun loadFeedPage(
        cursor: CommunityCursor? = null,
        limit: Int = COMMUNITY_FEED_PAGE_SIZE,
    ): Result<CommunityFeedPage>

    suspend fun loadPost(postId: String): Result<CommunityPost?>

    suspend fun loadComments(postId: String): Result<List<CommunityComment>>

    suspend fun createComment(postId: String, body: String, parentId: String? = null): Result<Unit>

    suspend fun updateComment(commentId: String, body: String): Result<Unit>

    suspend fun deleteComment(commentId: String): Result<Unit>

    suspend fun deletePost(postId: String): Result<Unit>

    suspend fun moderateComment(commentId: String, reason: String): Result<Unit>

    suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome>

    suspend fun toggleReaction(postId: String, emoji: String): Result<CommunityLikeOutcome>

    suspend fun refreshMediaUrl(mediaId: String): Result<String?>
}
