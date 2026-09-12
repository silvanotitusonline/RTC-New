
package za.org.rtc.community.core

import android.content.Context
import android.content.Intent
import android.net.Uri

object DeepLinkManager {
    private const val BASE_URL = "https://rtc.community"

    fun createPostLink(postId: String): String {
        return "$BASE_URL/post/$postId"
    }

    fun createMarketplaceLink(businessId: String): String {
        return "$BASE_URL/biz/$businessId"
    }

    fun openDeepLink(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
