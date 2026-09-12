package za.org.rtc.community.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.ui.navigation.RtcCommunityApp
import za.org.rtc.community.ui.theme.RtcCommunityTheme

/**
 * Single application rendering boundary for theme + published UI configuration.
 * Runtime branding is deliberately resolved outside MainActivity so Android bootstrap stays thin.
 */
@Composable
fun RtcConfiguredAppRoot(
    rtcViewModel: RtcViewModel,
    runtimeViewModel: UiConfigurationRuntimeViewModel = hiltViewModel(),
) {
    val session by rtcViewModel.session.collectAsStateWithLifecycle()
    val configuration by runtimeViewModel.configuration.collectAsStateWithLifecycle()
    val assetUrls by runtimeViewModel.assetUrls.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalRtcUiConfiguration provides configuration,
        LocalRtcUiAssetUrls provides assetUrls,
    ) {
        RtcCommunityTheme(
            preference = session.darkMode,
            configuration = configuration,
            dynamicColor = session.dynamicColor,
        ) {
            RtcCommunityApp(rtcViewModel)
        }
    }
}
