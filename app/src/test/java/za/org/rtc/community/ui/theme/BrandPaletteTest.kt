package za.org.rtc.community.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.AppearanceConfiguration
import za.org.rtc.community.core.BrandFontFamily
import za.org.rtc.community.core.BrandShapePreset
import za.org.rtc.community.core.TypographyConfiguration
import za.org.rtc.community.core.TypographyScalePreset
import za.org.rtc.community.core.TypographyWeightPreset

class BrandPaletteTest {
    @Test
    fun validHexColoursProduceDeterministicAccessibleSchemes() {
        val configuration = AppearanceConfiguration()
        val first = BrandPaletteEngine.derive(configuration)
        val second = BrandPaletteEngine.derive(configuration)

        assertEquals(first.light.primary, second.light.primary)
        assertEquals(first.light.background, second.light.background)
        assertEquals(first.dark.primary, second.dark.primary)
        assertEquals(first.dark.background, second.dark.background)
        assertEquals(Color(0xFFE95B5B), first.dark.error)
        assertTrue(BrandPaletteEngine.hasPublishableContrast(configuration))
        assertTrue(BrandPaletteEngine.contrastRatio(first.dark.primary, first.dark.onPrimary) >= 4.5)
        assertTrue(BrandPaletteEngine.contrastRatio(first.light.background, first.light.onBackground) >= 4.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedHexColourIsRejected() {
        BrandPaletteEngine.parseHex("green")
    }

    @Test
    fun deliberatelyLowContrastInputStillGetsReadableDerivedForegrounds() {
        val configuration = AppearanceConfiguration(
            primarySeed = "#777777",
            accentSeed = "#777777",
            backgroundSeed = "#777777",
        )
        val schemes = BrandPaletteEngine.derive(configuration)

        assertTrue(BrandPaletteEngine.contrastRatio(schemes.dark.primary, schemes.dark.onPrimary) >= 4.5)
        assertTrue(BrandPaletteEngine.contrastRatio(schemes.light.primary, schemes.light.onPrimary) >= 4.5)
    }

    @Test
    fun typographyChoicesStayInsideApprovedFamiliesAndBoundedScale() {
        val compact = BrandTypographyEngine.derive(
            TypographyConfiguration(
                fontFamily = BrandFontFamily.SYSTEM_MONO,
                scalePreset = TypographyScalePreset.COMPACT,
                weightPreset = TypographyWeightPreset.LIGHTER,
            ),
        )
        val large = BrandTypographyEngine.derive(
            TypographyConfiguration(scalePreset = TypographyScalePreset.LARGE),
        )

        assertTrue(compact.bodyLarge.fontSize.value > 0f)
        assertTrue(large.bodyLarge.fontSize.value > compact.bodyLarge.fontSize.value)
        assertTrue(TypographyScalePreset.entries.all { it.factor in 0.90f..1.15f })
    }

    @Test
    fun shapePresetsAreFiniteAndSharpUsesNoRoundedCorners() {
        val rounded = BrandShapeEngine.derive(BrandShapePreset.ROUNDED)
        val sharp = BrandShapeEngine.derive(BrandShapePreset.SHARP)

        assertFalse(rounded.medium == sharp.medium)
        assertEquals(RoundedCornerShape(0.dp), sharp.medium)
    }
}
