package za.org.rtc.community.core

import java.util.UUID

object UiConfigurationValidator {
    private val hex = Regex("^#[0-9A-Fa-f]{6}$")

    fun isValid(configuration: GlobalUiConfiguration): Boolean {
        if (configuration.schemaVersion != 2) return false
        if (!isAppearanceValid(configuration.appearance)) return false
        if (!isWelcomeValid(configuration.welcome)) return false
        if (!HomeLayout.isValid(configuration.home)) return false
        if (!isImageWidgetValid(configuration.home.imageWidget)) return false
        return true
    }

    private fun isAppearanceValid(appearance: AppearanceConfiguration): Boolean =
        hex.matches(appearance.primarySeed) &&
            hex.matches(appearance.accentSeed) &&
            hex.matches(appearance.backgroundSeed)

    private fun isWelcomeValid(welcome: WelcomeConfiguration): Boolean =
        welcome.headline.trim().length in 3..100 &&
            welcome.supportingText.trim().length in 3..280 &&
            welcome.primaryActionLabel.trim().length in 1..40 &&
            welcome.secondaryActionLabel.trim().length in 1..40

    private fun isImageWidgetValid(widget: HomeImageWidget?): Boolean {
        if (widget == null) return true
        if (!isUuid(widget.assetId)) return false
        if (widget.altText.trim().length !in 3..180) return false
        if (widget.caption != null && widget.caption.trim().length > 240) return false
        return true
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
}
