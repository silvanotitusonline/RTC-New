package za.org.rtc.community.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import za.org.rtc.community.core.AppearanceConfiguration

/** Deterministic semantic colour derivation for validated administrator brand seeds. */
object BrandPaletteEngine {
    val SEMANTIC_ERROR = Color(0xFFE95B5B)
    val SEMANTIC_ERROR_LIGHT = Color(0xFFBA1A1A)

    data class Schemes(val light: ColorScheme, val dark: ColorScheme)

    fun parseHex(value: String): Color {
        require(value.matches(Regex("^#[0-9A-Fa-f]{6}$"))) { "Expected #RRGGBB colour." }
        val rgb = value.drop(1).toInt(16)
        return Color(
            red = ((rgb shr 16) and 0xFF) / 255f,
            green = ((rgb shr 8) and 0xFF) / 255f,
            blue = (rgb and 0xFF) / 255f,
            alpha = 1f,
        )
    }

    fun contrastRatio(first: Color, second: Color): Double {
        val l1 = relativeLuminance(first)
        val l2 = relativeLuminance(second)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    fun derive(configuration: AppearanceConfiguration): Schemes {
        val primary = parseHex(configuration.primarySeed)
        val secondary = parseHex(configuration.accentSeed)
        val background = parseHex(configuration.backgroundSeed)
        val darkOnPrimary = accessibleForeground(primary)
        val darkOnSecondary = accessibleForeground(secondary)
        val darkOnBackground = accessibleForeground(background)

        val lightPrimary = darken(primary, 0.26f)
        val lightSecondary = darken(secondary, 0.32f)
        val lightBackground = lighten(background, 0.93f)
        val lightSurface = lighten(background, 0.985f)
        val darkSurface = lighten(background, 0.035f)
        val darkVariant = lighten(background, 0.08f)
        val lightVariant = darken(lightBackground, 0.06f)

        val dark = darkColorScheme(
            primary = primary,
            onPrimary = darkOnPrimary,
            primaryContainer = mix(primary, background, 0.64f),
            onPrimaryContainer = accessibleForeground(mix(primary, background, 0.64f)),
            secondary = secondary,
            onSecondary = darkOnSecondary,
            secondaryContainer = mix(secondary, background, 0.68f),
            onSecondaryContainer = accessibleForeground(mix(secondary, background, 0.68f)),
            tertiary = lighten(primary, 0.24f),
            onTertiary = accessibleForeground(lighten(primary, 0.24f)),
            background = background,
            onBackground = darkOnBackground,
            surface = darkSurface,
            onSurface = accessibleForeground(darkSurface),
            surfaceVariant = darkVariant,
            onSurfaceVariant = accessibleForeground(darkVariant),
            outline = lighten(background, 0.28f),
            outlineVariant = lighten(background, 0.14f),
            error = SEMANTIC_ERROR,
            onError = accessibleForeground(SEMANTIC_ERROR),
        )

        val light = lightColorScheme(
            primary = lightPrimary,
            onPrimary = accessibleForeground(lightPrimary),
            primaryContainer = lighten(primary, 0.72f),
            onPrimaryContainer = accessibleForeground(lighten(primary, 0.72f)),
            secondary = lightSecondary,
            onSecondary = accessibleForeground(lightSecondary),
            secondaryContainer = lighten(secondary, 0.74f),
            onSecondaryContainer = accessibleForeground(lighten(secondary, 0.74f)),
            tertiary = darken(primary, 0.12f),
            onTertiary = accessibleForeground(darken(primary, 0.12f)),
            background = lightBackground,
            onBackground = accessibleForeground(lightBackground),
            surface = lightSurface,
            onSurface = accessibleForeground(lightSurface),
            surfaceVariant = lightVariant,
            onSurfaceVariant = accessibleForeground(lightVariant),
            outline = darken(lightBackground, 0.38f),
            outlineVariant = darken(lightBackground, 0.18f),
            error = SEMANTIC_ERROR_LIGHT,
            onError = accessibleForeground(SEMANTIC_ERROR_LIGHT),
        )
        return Schemes(light = light, dark = dark)
    }

    fun hasPublishableContrast(configuration: AppearanceConfiguration): Boolean {
        val schemes = derive(configuration)
        return listOf(
            contrastRatio(schemes.dark.primary, schemes.dark.onPrimary),
            contrastRatio(schemes.dark.background, schemes.dark.onBackground),
            contrastRatio(schemes.light.primary, schemes.light.onPrimary),
            contrastRatio(schemes.light.background, schemes.light.onBackground),
        ).all { it >= 4.5 }
    }

    private fun accessibleForeground(background: Color): Color {
        val black = Color.Black
        val white = Color.White
        return if (contrastRatio(black, background) >= contrastRatio(white, background)) black else white
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble().coerceIn(0.0, 1.0)
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun lighten(color: Color, amount: Float): Color = mix(color, Color.White, amount)
    private fun darken(color: Color, amount: Float): Color = mix(color, Color.Black, amount)

    private fun mix(from: Color, to: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = from.red + (to.red - from.red) * t,
            green = from.green + (to.green - from.green) * t,
            blue = from.blue + (to.blue - from.blue) * t,
            alpha = 1f,
        )
    }
}
