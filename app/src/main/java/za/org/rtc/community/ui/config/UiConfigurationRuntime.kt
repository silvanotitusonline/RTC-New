package za.org.rtc.community.ui.config

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.LauncherIconId
import za.org.rtc.community.data.ui_config.UiConfigurationRepository

val LocalRtcUiConfiguration = staticCompositionLocalOf { GlobalUiConfiguration.default() }
val LocalRtcUiAssetUrls = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

@HiltViewModel
class UiConfigurationRuntimeViewModel @Inject constructor(
    private val uiRepository: UiConfigurationRepository,
    private val launcherIconManager: LauncherIconManager,
) : ViewModel() {
    val configuration: StateFlow<GlobalUiConfiguration> = uiRepository.effectiveConfiguration
    val versionId: StateFlow<String?> = uiRepository.effectiveVersionId

    private val _assetUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val assetUrls: StateFlow<Map<String, String>> = _assetUrls.asStateFlow()

    init {
        viewModelScope.launch { uiRepository.refreshEffectiveConfiguration() }
        viewModelScope.launch {
            configuration.collectLatest { config ->
                launcherIconManager.apply(config.launcher.recommendedIconId)
                val assetId = config.home.imageWidget?.assetId ?: return@collectLatest
                while (true) {
                    val refreshed = refreshAssetUrl(assetId)
                    delay(if (refreshed) ASSET_REFRESH_INTERVAL else ASSET_RETRY_INTERVAL)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { uiRepository.refreshEffectiveConfiguration() }
    }

    fun resolveAssetUrl(assetId: String) {
        if (_assetUrls.value.containsKey(assetId)) return
        viewModelScope.launch { refreshAssetUrl(assetId) }
    }

    private suspend fun refreshAssetUrl(assetId: String): Boolean {
        var refreshed = false
        uiRepository.signedUiImageUrl(assetId).onSuccess { url ->
            _assetUrls.update { current ->
                current.filterKeys { it == assetId } + (assetId to url)
            }
            refreshed = true
        }
        return refreshed
    }

    companion object {
        private val ASSET_REFRESH_INTERVAL = 5.minutes
        private val ASSET_RETRY_INTERVAL = 30.seconds
    }
}

@Singleton
class LauncherIconManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val aliases = mapOf(
        LauncherIconId.DEFAULT to "${context.packageName}.LauncherDefault",
        LauncherIconId.GOLD to "${context.packageName}.LauncherGold",
        LauncherIconId.EMERALD to "${context.packageName}.LauncherEmerald",
        LauncherIconId.MONOCHROME to "${context.packageName}.LauncherMonochrome",
    )

    fun apply(icon: LauncherIconId) {
        val manager = context.packageManager
        val selected = aliases[icon] ?: aliases.getValue(LauncherIconId.DEFAULT)

        // Always enable the target first so launcher switching never creates a zero-launcher gap.
        if (!setAliasState(manager, selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)) return
        aliases.values.filterNot { it == selected }.forEach { className ->
            setAliasState(manager, className, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    private fun setAliasState(manager: PackageManager, className: String, desired: Int): Boolean {
        val component = ComponentName(context, className)
        if (manager.getComponentEnabledSetting(component) == desired) return true
        return runCatching {
            manager.setComponentEnabledSetting(
                component,
                desired,
                PackageManager.DONT_KILL_APP,
            )
        }.isSuccess
    }
}
