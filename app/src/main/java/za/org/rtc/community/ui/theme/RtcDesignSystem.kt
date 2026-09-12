
package za.org.rtc.community.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RtcDesignSystem {
    // --- Enterprise Color Palette (High Contrast) ---
    val PrimaryBrand = Color(0xFF1DA1F2) // X-style Blue
    val BackgroundDark = Color(0xFF000000) // True Black for OLED
    val SurfaceDark = Color(0xFF15181C)    // Deep Grey for Cards
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF71767B)
    val AccentBorder = Color(0xFF2F3336)
    
    val BackgroundLight = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF7F9F9)
    val TextPrimaryLight = Color(0xFF0F1419)
    val TextSecondaryLight = Color(0xFF536471)
    val AccentBorderLight = Color(0xFFEFF3F4)

    // --- Spacing & Density ---
    val FeedPadding = 12.dp
    val ComponentSpacing = 8.dp
    val BorderWidth = 1.dp
    
    // --- Typography ---
    val BodyFontSize = 15.sp
    val HandleFontSize = 14.sp
    val HeaderFontSize = 18.sp
}
