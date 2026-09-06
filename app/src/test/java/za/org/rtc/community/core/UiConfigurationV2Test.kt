package za.org.rtc.community.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiConfigurationV2Test {
    @Test
    fun validV2PayloadDecodesBroaderBrandAndScreenConfiguration() {
        val raw = """
            {
              "schemaVersion":2,
              "appearance":{"primarySeed":"#2EC27E","accentSeed":"#D4AF37","backgroundSeed":"#0C1013","shapePreset":"ROUNDED","densityPreset":"STANDARD"},
              "typography":{"fontFamily":"SYSTEM_SANS","scalePreset":"COMFORTABLE","weightPreset":"STANDARD"},
              "welcome":{"headline":"Rise Tsantsabane Communities","supportingText":"Community services in one place.","primaryActionLabel":"Get started","secondaryActionLabel":"Sign in","launchTreatment":"BRAND_PANEL"},
              "home":{"sections":["WELCOME","COMMUNITY_SNAPSHOT","QUICK_ACCESS","LATEST_UPDATES","HELP"],"imageWidget":{"assetId":"123e4567-e89b-12d3-a456-426614174000","anchorSection":"QUICK_ACCESS","placement":"AFTER","aspectPreset":"WIDE","contentScale":"CROP","altText":"RTC community banner","caption":"Community first"}},
              "screens":{"community":{"headerStyle":"HERO","density":"STANDARD"},"explore":{"headerStyle":"HERO","density":"COMPACT"},"support":{"headerStyle":"STANDARD","density":"STANDARD"},"account":{"headerStyle":"COMPACT","density":"COMPACT"},"notifications":{"headerStyle":"STANDARD","density":"COMPACT"},"search":{"headerStyle":"STANDARD","density":"STANDARD"},"help":{"headerStyle":"STANDARD","density":"COMFORTABLE"},"marketplace":{"headerStyle":"HERO","density":"STANDARD"},"marketplaceSearch":{"headerStyle":"STANDARD","density":"STANDARD"},"marketplaceMap":{"headerStyle":"COMPACT","density":"STANDARD"},"marketplaceAccount":{"headerStyle":"STANDARD","density":"STANDARD"}},
              "launcher":{"recommendedIconId":"GOLD"}
            }
        """.trimIndent()

        val decoded = GlobalUiConfiguration.decodeOrNull(raw)

        assertNotNull(decoded)
        assertEquals(2, decoded?.schemaVersion)
        assertEquals("#2EC27E", decoded?.appearance?.primarySeed)
        assertEquals(TypographyScalePreset.COMFORTABLE, decoded?.typography?.scalePreset)
        assertEquals(ScreenHeaderStyle.HERO, decoded?.screens?.community?.headerStyle)
        assertEquals(ScreenHeaderStyle.HERO, decoded?.screens?.marketplace?.headerStyle)
        assertEquals(HomeImagePlacement.AFTER, decoded?.home?.imageWidget?.placement)
        assertEquals(LauncherIconId.GOLD, decoded?.launcher?.recommendedIconId)
        assertEquals(decoded, GlobalUiConfiguration.decodeOrNull(GlobalUiConfiguration.encode(decoded!!)))
    }

    @Test
    fun legacyV1PayloadUpgradesWithoutInventingAnImageWidget() {
        val raw = """{"schemaVersion":1,"home":{"sections":["WELCOME","QUICK_ACCESS","HELP"]}}"""

        val decoded = GlobalUiConfiguration.decodeOrNull(raw)

        assertNotNull(decoded)
        assertEquals(2, decoded?.schemaVersion)
        assertEquals(listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.HELP), decoded?.home?.sections)
        assertNull(decoded?.home?.imageWidget)
        assertEquals(AppearanceConfiguration(), decoded?.appearance)
    }

    @Test
    fun invalidColourOrImageWidgetCannotReplaceSafeConfiguration() {
        val invalidColour = GlobalUiConfiguration.default().copy(
            appearance = AppearanceConfiguration(primarySeed = "green"),
        )
        val invalidImage = GlobalUiConfiguration.default().copy(
            home = HomeLayout.default().copy(
                imageWidget = HomeImageWidget(
                    assetId = "not-a-uuid",
                    anchorSection = HomeSection.QUICK_ACCESS,
                    altText = "",
                ),
            ),
        )

        assertFalse(UiConfigurationValidator.isValid(invalidColour))
        assertFalse(UiConfigurationValidator.isValid(invalidImage))
    }

    @Test
    fun imageWidgetPlacementRequiresExistingAnchorAndReadableAltText() {
        val widget = HomeImageWidget(
            assetId = "123e4567-e89b-12d3-a456-426614174000",
            anchorSection = HomeSection.LATEST_UPDATES,
            placement = HomeImagePlacement.BEFORE,
            altText = "Community event artwork",
        )
        val valid = GlobalUiConfiguration.default().copy(
            home = HomeLayout(
                sections = listOf(HomeSection.WELCOME, HomeSection.LATEST_UPDATES, HomeSection.HELP),
                imageWidget = widget,
            ),
        )
        val invalidAnchor = valid.copy(
            home = valid.home.copy(
                sections = listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.HELP),
            ),
        )

        assertTrue(UiConfigurationValidator.isValid(valid))
        assertFalse(UiConfigurationValidator.isValid(invalidAnchor))
    }

    @Test
    fun unsupportedOrMalformedPayloadFailsClosed() {
        val invalidScreen = """{"schemaVersion":2,"screens":{"community":{"headerStyle":"EXPLOSIVE","density":"STANDARD"}}}"""
        val future = """{"schemaVersion":3}"""
        val unknownField = """{"schemaVersion":2,"unexpected":true}"""

        assertNull(GlobalUiConfiguration.decodeOrNull(invalidScreen))
        assertNull(GlobalUiConfiguration.decodeOrNull(future))
        assertNull(GlobalUiConfiguration.decodeOrNull(unknownField))
        assertEquals(GlobalUiConfiguration.default(), GlobalUiConfiguration.decodeOrDefault(future))
    }

    @Test
    fun publishableConfigurationKeepsMandatoryHomeSectionsAndBoundedCopy() {
        val tooLongHeadline = GlobalUiConfiguration.default().copy(
            welcome = WelcomeConfiguration(headline = "x".repeat(121)),
        )
        val missingHelp = GlobalUiConfiguration.default().copy(
            home = HomeLayout(listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.NEXT_STEPS)),
        )

        assertFalse(UiConfigurationValidator.isValid(tooLongHeadline))
        assertFalse(UiConfigurationValidator.isValid(missingHelp))
        assertTrue(UiConfigurationValidator.isValid(GlobalUiConfiguration.default()))
    }
}
