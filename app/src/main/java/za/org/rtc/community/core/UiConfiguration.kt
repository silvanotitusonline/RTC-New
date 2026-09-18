package za.org.rtc.community.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val StrictUiConfigurationJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
enum class HomeSection {
    WELCOME,
    COMMUNITY_SNAPSHOT,
    QUICK_ACCESS,
    CONTINUE_DRAFT,
    PENDING_SYNC,
    NEXT_STEPS,
    LATEST_UPDATES,
    HELP,
}

@Serializable
enum class BrandShapePreset { SOFT, ROUNDED, SHARP }

@Serializable
enum class BrandDensityPreset { COMPACT, STANDARD, COMFORTABLE }

@Serializable
enum class BrandFontFamily { SYSTEM_SANS, SYSTEM_SERIF, SYSTEM_MONO }

@Serializable
enum class TypographyScalePreset(val factor: Float) {
    COMPACT(0.92f),
    STANDARD(1.00f),
    COMFORTABLE(1.08f),
    LARGE(1.15f),
}

@Serializable
enum class TypographyWeightPreset { LIGHTER, STANDARD, STRONG }

@Serializable
enum class LaunchTreatment { BRAND_PANEL, MINIMAL, IMMERSIVE }

@Serializable
enum class ScreenHeaderStyle { COMPACT, STANDARD, HERO }

@Serializable
enum class ScreenDensity { COMPACT, STANDARD, COMFORTABLE }

@Serializable
enum class HomeImagePlacement { BEFORE, AFTER }

@Serializable
enum class HomeImageAspectPreset { WIDE, STANDARD, SQUARE }

@Serializable
enum class HomeImageContentScale { CROP, FIT }

@Serializable
enum class LauncherIconId { DEFAULT, GOLD, EMERALD, MONOCHROME }

@Serializable
data class AppearanceConfiguration(
    val primarySeed: String = "#2EC27E",
    val accentSeed: String = "#D4AF37",
    val backgroundSeed: String = "#0C1013",
    val shapePreset: BrandShapePreset = BrandShapePreset.ROUNDED,
    val densityPreset: BrandDensityPreset = BrandDensityPreset.STANDARD,
)

@Serializable
data class TypographyConfiguration(
    val fontFamily: BrandFontFamily = BrandFontFamily.SYSTEM_SANS,
    val scalePreset: TypographyScalePreset = TypographyScalePreset.STANDARD,
    val weightPreset: TypographyWeightPreset = TypographyWeightPreset.STANDARD,
)

@Serializable
data class WelcomeConfiguration(
    val headline: String = "Rise Tsantsabane Communities",
    val supportingText: String = "Community services, information and participation in one place.",
    val primaryActionLabel: String = "Get started",
    val secondaryActionLabel: String = "Sign in",
    val launchTreatment: LaunchTreatment = LaunchTreatment.BRAND_PANEL,
)

@Serializable
data class HomeImageWidget(
    val assetId: String,
    val anchorSection: HomeSection,
    val placement: HomeImagePlacement = HomeImagePlacement.AFTER,
    val aspectPreset: HomeImageAspectPreset = HomeImageAspectPreset.WIDE,
    val contentScale: HomeImageContentScale = HomeImageContentScale.CROP,
    val altText: String,
    val caption: String? = null,
)

@Serializable
data class HomeLayout(
    val sections: List<HomeSection>,
    val imageWidget: HomeImageWidget? = null,
) {
    companion object {
        val mandatorySections = setOf(HomeSection.WELCOME, HomeSection.HELP)

        fun default(): HomeLayout = HomeLayout(
            sections = listOf(
                HomeSection.WELCOME,
                HomeSection.COMMUNITY_SNAPSHOT,
                HomeSection.QUICK_ACCESS,
                HomeSection.CONTINUE_DRAFT,
                HomeSection.PENDING_SYNC,
                HomeSection.LATEST_UPDATES,
                HomeSection.HELP,
            ),
        )

        fun isValid(candidate: HomeLayout?): Boolean {
            val sections = candidate?.sections ?: return false
            if (
                sections.size !in 3..HomeSection.entries.size ||
                sections.distinct().size != sections.size ||
                !sections.containsAll(mandatorySections)
            ) return false
            val widget = candidate.imageWidget
            return widget == null || widget.anchorSection in sections
        }

        fun validatedOrDefault(candidate: HomeLayout?): HomeLayout =
            candidate?.takeIf(::isValid) ?: default()
    }
}

@Serializable
data class ScreenPresentation(
    val headerStyle: ScreenHeaderStyle = ScreenHeaderStyle.STANDARD,
    val density: ScreenDensity = ScreenDensity.STANDARD,
)

@Serializable
data class ScreenPresentationConfiguration(
    val community: ScreenPresentation = ScreenPresentation(headerStyle = ScreenHeaderStyle.HERO),
    val explore: ScreenPresentation = ScreenPresentation(headerStyle = ScreenHeaderStyle.HERO),
    val support: ScreenPresentation = ScreenPresentation(),
    val account: ScreenPresentation = ScreenPresentation(),
    val notifications: ScreenPresentation = ScreenPresentation(),
    val search: ScreenPresentation = ScreenPresentation(),
    val help: ScreenPresentation = ScreenPresentation(),
    val marketplace: ScreenPresentation = ScreenPresentation(headerStyle = ScreenHeaderStyle.HERO),
    val marketplaceSearch: ScreenPresentation = ScreenPresentation(),
    val marketplaceMap: ScreenPresentation = ScreenPresentation(headerStyle = ScreenHeaderStyle.COMPACT),
    val marketplaceAccount: ScreenPresentation = ScreenPresentation(),
)

@Serializable
data class LauncherConfiguration(
    val recommendedIconId: LauncherIconId = LauncherIconId.DEFAULT,
)

@Serializable
private data class LegacyUiConfigurationV1(
    val schemaVersion: Int = 1,
    val home: HomeLayout,
)

@Serializable
data class GlobalUiConfiguration(
    val schemaVersion: Int = 2,
    val appearance: AppearanceConfiguration = AppearanceConfiguration(),
    val typography: TypographyConfiguration = TypographyConfiguration(),
    val welcome: WelcomeConfiguration = WelcomeConfiguration(),
    val home: HomeLayout = HomeLayout.default(),
    val screens: ScreenPresentationConfiguration = ScreenPresentationConfiguration(),
    val launcher: LauncherConfiguration = LauncherConfiguration(),
) {
    companion object {
        fun default(): GlobalUiConfiguration = GlobalUiConfiguration()

        fun decodeOrNull(rawConfiguration: String?): GlobalUiConfiguration? {
            if (rawConfiguration.isNullOrBlank()) return null
            return runCatching {
                val element = StrictUiConfigurationJson.parseToJsonElement(rawConfiguration)
                val version = element.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
                val decoded = when (version) {
                    1 -> {
                        val legacy = StrictUiConfigurationJson.decodeFromString<LegacyUiConfigurationV1>(rawConfiguration)
                        if (!HomeLayout.isValid(legacy.home)) return@runCatching null
                        default().copy(home = legacy.home.copy(imageWidget = null))
                    }
                    2 -> StrictUiConfigurationJson.decodeFromString<GlobalUiConfiguration>(rawConfiguration)
                    else -> null
                }
                decoded?.takeIf(UiConfigurationValidator::isValid)
            }.getOrNull()
        }

        fun decodeOrDefault(rawConfiguration: String?): GlobalUiConfiguration =
            decodeOrNull(rawConfiguration) ?: default()

        fun encode(configuration: GlobalUiConfiguration): String {
            require(UiConfigurationValidator.isValid(configuration)) { "UI configuration is not valid." }
            return StrictUiConfigurationJson.encodeToString(configuration)
        }
    }
}
