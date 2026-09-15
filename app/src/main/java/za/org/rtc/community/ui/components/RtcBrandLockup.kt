package za.org.rtc.community.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.org.rtc.community.R
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun RtcLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = RtcSize.heroIcon * 2,
    contentDescription: String? = "RTC Community logo",
    logoRes: Int = R.drawable.rtc_brand_logo,
) {
    Image(
        painter = painterResource(id = logoRes),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

@Composable
fun RtcBrandLabel(
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    val style = textStyle ?: MaterialTheme.typography.headlineMedium
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("RTC", style = style, fontWeight = FontWeight.Black)
        Text("Community", style = style, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RtcBrandLockup(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    large: Boolean = false,
    logoSize: Dp? = null,
    logoRes: Int = R.drawable.rtc_brand_logo,
) {
    val markSize = logoSize ?: when {
        large -> 135.dp
        compact -> RtcSize.avatarLarge
        else -> RtcSize.heroIcon * 2
    }
    val textStyle = when {
        large -> MaterialTheme.typography.headlineLarge
        compact -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.headlineMedium
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (large) RtcSpacing.standard else RtcSpacing.compact),
    ) {
        RtcLogoMark(size = markSize, logoRes = logoRes)
        RtcBrandLabel(textStyle = textStyle)
    }
}
