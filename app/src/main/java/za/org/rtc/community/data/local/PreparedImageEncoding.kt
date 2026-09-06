package za.org.rtc.community.data.local

internal typealias PreparedImageEncoding = za.org.rtc.community.core.media.PreparedImageEncoding

internal fun preparedImageEncoding(hasAlphaChannel: Boolean): PreparedImageEncoding =
    za.org.rtc.community.core.media.preparedImageEncoding(hasAlphaChannel)
