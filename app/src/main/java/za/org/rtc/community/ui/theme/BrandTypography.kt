package za.org.rtc.community.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import za.org.rtc.community.core.BrandFontFamily
import za.org.rtc.community.core.TypographyConfiguration
import za.org.rtc.community.core.TypographyWeightPreset

/** Uses only packaged/system font families and bounded scale presets. Android fontScale still applies at render time. */
object BrandTypographyEngine {
    fun derive(configuration: TypographyConfiguration): Typography {
        val factor = configuration.scalePreset.factor
        val family = when (configuration.fontFamily) {
            BrandFontFamily.SYSTEM_SANS -> FontFamily.SansSerif
            BrandFontFamily.SYSTEM_SERIF -> FontFamily.Serif
            BrandFontFamily.SYSTEM_MONO -> FontFamily.Monospace
        }
        val headingWeight = when (configuration.weightPreset) {
            TypographyWeightPreset.LIGHTER -> FontWeight.SemiBold
            TypographyWeightPreset.STANDARD -> FontWeight.Bold
            TypographyWeightPreset.STRONG -> FontWeight.ExtraBold
        }
        val bodyWeight = when (configuration.weightPreset) {
            TypographyWeightPreset.LIGHTER -> FontWeight.Normal
            TypographyWeightPreset.STANDARD -> FontWeight.Normal
            TypographyWeightPreset.STRONG -> FontWeight.Medium
        }

        fun style(size: Float, line: Float, weight: FontWeight = bodyWeight) = TextStyle(
            fontFamily = family,
            fontSize = scaled(size, factor),
            lineHeight = scaled(line, factor),
            fontWeight = weight,
        )

        return Typography(
            displaySmall = style(34f, 42f, headingWeight),
            headlineLarge = style(29f, 36f, headingWeight),
            headlineMedium = style(26f, 32f, headingWeight),
            headlineSmall = style(23f, 29f, headingWeight),
            titleLarge = style(21f, 27f, headingWeight),
            titleMedium = style(18f, 24f, FontWeight.SemiBold),
            titleSmall = style(16f, 21f, FontWeight.SemiBold),
            bodyLarge = style(17f, 25f),
            bodyMedium = style(15f, 22f),
            bodySmall = style(13f, 19f),
            labelLarge = style(14f, 20f, FontWeight.SemiBold),
            labelMedium = style(12f, 17f, FontWeight.Medium),
            labelSmall = style(11f, 16f, FontWeight.Medium),
        )
    }

    private fun scaled(value: Float, factor: Float): TextUnit = (value * factor).sp
}
