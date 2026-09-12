package za.org.rtc.community.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.ThemePreference

class ThemeModeResolverTest {
    @Test
    fun `light preference remains light when system is dark`() {
        assertFalse(resolveRtcUseDarkTheme(ThemePreference.LIGHT, systemInDarkTheme = true))
    }

    @Test
    fun `dark preference remains dark when system is light`() {
        assertTrue(resolveRtcUseDarkTheme(ThemePreference.DARK, systemInDarkTheme = false))
    }

    @Test
    fun `system preference follows a dark system`() {
        assertTrue(resolveRtcUseDarkTheme(ThemePreference.SYSTEM, systemInDarkTheme = true))
    }

    @Test
    fun `system preference follows a light system`() {
        assertFalse(resolveRtcUseDarkTheme(ThemePreference.SYSTEM, systemInDarkTheme = false))
    }
}
