package za.org.rtc.community.feature.community

import za.org.rtc.community.R

/**
 * Deterministic portrait resource for residents who have not uploaded an avatar.
 *
 * The drawable pool is intentionally small; selection is a stable hash of the
 * author name so a given resident always renders the same portrait across the
 * feed, detail screens and search results.
 */
internal fun syntheticAvatarResource(authorName: String): Int {
    val pool = SYNTHETIC_AVATAR_POOL
    if (authorName.isBlank()) return pool.first()
    val hash = authorName.fold(0) { acc, ch -> (acc * 31 + ch.code) and 0x7FFFFFFF }
    return pool[hash % pool.size]
}

private val SYNTHETIC_AVATAR_POOL: IntArray = intArrayOf(
    R.drawable.rtc_launcher_emerald,
)
