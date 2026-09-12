
package za.org.rtc.community.core.media

import android.util.Log
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RtcMediaEngine {
    private const val CDN_BASE = "https://your-supabase-cdn.com" // Placeholder for actual CDN

    enum class Resolution(val width: Int, val quality: Int) {
        THUMBNAIL(100, 60),
        FEED(400, 80),
        FULL(1080, 90)
    }

    fun optimizeUrl(url: String, resolution: Resolution): String {
        if (url.isBlank() || url.contains("placeholder")) return url
        
        return try {
            // Append optimization parameters for the CDN/Supabase Image Transformation
            // Format: url?width=X&height=Y&quality=Z
            "$url?width=${resolution.width}&quality=${resolution.quality}&auto=format"
        } catch (e: Exception) {
            url
        }
    }

    fun getAvatarUrl(url: String?): String {
        return url ?: "https://via.placeholder.com/100?text=User"
    }
}
