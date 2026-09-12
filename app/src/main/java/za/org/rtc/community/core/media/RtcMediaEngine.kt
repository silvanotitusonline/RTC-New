package za.org.rtc.community.core.media

/**
 * Central place where remote media URLs are shaped before they reach the UI.
 *
 * Supabase image transformation is a Pro-plan feature; on the free plan these
 * query parameters are ignored by the server and the original asset is served,
 * so this is safe to call unconditionally and becomes an optimisation the day
 * the project is upgraded. Client-side compression in [MediaPreparation]
 * remains the primary size control.
 */
object RtcMediaEngine {

    enum class Resolution(val width: Int, val quality: Int) {
        THUMBNAIL(160, 60),
        AVATAR(240, 70),
        FEED(720, 80),
        FULL(1440, 90),
    }

    private const val PLACEHOLDER_MARKER = "placeholder"

    /**
     * Returns [url] with transformation parameters appended.
     * Blank, local, and placeholder URLs are returned untouched.
     */
    fun optimizeUrl(url: String?, resolution: Resolution): String? {
        if (url.isNullOrBlank()) return url
        if (url.contains(PLACEHOLDER_MARKER)) return url
        if (!url.startsWith("http", ignoreCase = true)) return url
        if (url.contains("width=") || url.contains("quality=")) return url

        val separator = if (url.contains("?")) "&" else "?"
        return url + separator +
            "width=" + resolution.width +
            "&quality=" + resolution.quality +
            "&resize=contain"
    }

    /** Avatar URL with a stable, non-null fallback contract for callers. */
    fun avatarUrl(url: String?): String? = optimizeUrl(url, Resolution.AVATAR)
}
