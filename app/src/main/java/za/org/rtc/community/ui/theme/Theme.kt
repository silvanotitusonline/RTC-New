package za.org.rtc.community.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.ThemePreference

// Stable semantic exports retained for existing screens, code paths and compiled defaults.
val RtcInk = Color(0xFF0C1013)
val RtcInkRaised = Color(0xFF11171C)
val RtcGraphite = Color(0xFF171D23)
val RtcGraphiteHigh = Color(0xFF222A32)
val RtcMint = Color(0xFF2EC27E)
val RtcEmerald = Color(0xFF197B52)
val RtcCivicGold = Color(0xFFD4AF37)
val RtcDanger = Color(0xFFE95B5B)
val RtcTextPrimary = Color(0xFFF5F7F8)
val RtcTextSecondary = Color(0xFFAAB3BA)
val RtcOutline = Color(0xFF2B343D)

@Composable
fun RtcCommunityTheme(
    preference: ThemePreference,
    configuration: GlobalUiConfiguration = GlobalUiConfiguration.default(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDark = resolveRtcUseDarkTheme(preference, isSystemInDarkTheme())
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val palette = BrandPaletteEngine.derive(configuration.appearance)
            if (useDark) palette.dark else palette.light
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrandTypographyEngine.derive(configuration.typography),
        shapes = BrandShapeEngine.derive(configuration.appearance.shapePreset),
        content = content,
    )
}
