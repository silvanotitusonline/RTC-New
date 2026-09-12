package za.org.rtc.community.feature.community

import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost

data class CommunityDetailState(
    val post: CommunityPost? = null,
    val comments: List<CommunityComment> = emptyList(),
    val isLoading: Boolean = false,
    val pendingCommentIds: Set<String> = emptySet(),
    val isCreatingComment: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)
