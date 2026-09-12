package za.org.rtc.community.ui.theme

import za.org.rtc.community.core.ThemePreference

/** Pure preference resolution kept separate from Compose for deterministic unit coverage. */
internal fun resolveRtcUseDarkTheme(
    preference: ThemePreference,
    systemInDarkTheme: Boolean,
): Boolean = when (preference) {
    ThemePreference.DARK -> true
    ThemePreference.LIGHT -> false
    ThemePreference.SYSTEM -> systemInDarkTheme
}
