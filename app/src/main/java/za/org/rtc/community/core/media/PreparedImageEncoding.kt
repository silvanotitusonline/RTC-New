package za.org.rtc.community.core.media

/**
 * Keeps prepared image output compatible with the source image's alpha capability.
 * JPEG is reserved for opaque images because it has no alpha channel.
 */
internal enum class PreparedImageEncoding(
    val extension: String,
    val mimeType: String,
) {
    JPEG(".jpg", "image/jpeg"),
    PNG(".png", "image/png"),
}

internal fun preparedImageEncoding(hasAlphaChannel: Boolean): PreparedImageEncoding =
    if (hasAlphaChannel) PreparedImageEncoding.PNG else PreparedImageEncoding.JPEG
