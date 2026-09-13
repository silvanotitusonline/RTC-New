package za.org.rtc.community.feature.community

import android.content.Context
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost

/**
 * Legacy compatibility provider only.
 *
 * Production community content is authoritative from Supabase with Room used only as a durable
 * cache of real server records. Never add demonstration or fabricated resident content here.
 */
@Deprecated("Production community content must come from authoritative repositories")
object CommunityMockData {
    fun getSamplePosts(context: Context? = null): List<CommunityPost> = emptyList()
    fun getSampleComments(postId: String): List<CommunityComment> = emptyList()
}
