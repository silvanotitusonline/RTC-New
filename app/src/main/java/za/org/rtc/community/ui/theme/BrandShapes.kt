package za.org.rtc.community.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.BrandShapePreset

/** Precompiled shape choices only; no remotely supplied radius values are interpreted. */
object BrandShapeEngine {
    fun derive(preset: BrandShapePreset): Shapes = when (preset) {
        BrandShapePreset.SOFT -> Shapes(
            extraSmall = RoundedCornerShape(RtcRadius.medium),
            small = RoundedCornerShape(RtcRadius.large),
            medium = RoundedCornerShape(RtcRadius.hero),
            large = RoundedCornerShape(RtcRadius.hero),
            extraLarge = RoundedCornerShape(RtcRadius.hero),
        )
        BrandShapePreset.ROUNDED -> Shapes(
            extraSmall = RoundedCornerShape(RtcRadius.small),
            small = RoundedCornerShape(RtcRadius.small),
            medium = RoundedCornerShape(RtcRadius.medium),
            large = RoundedCornerShape(RtcRadius.large),
            extraLarge = RoundedCornerShape(RtcRadius.hero),
        )
        BrandShapePreset.SHARP -> Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp),
        )
    }
}
