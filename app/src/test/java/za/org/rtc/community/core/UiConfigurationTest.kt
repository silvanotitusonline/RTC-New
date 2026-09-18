package za.org.rtc.community.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiConfigurationTest {
    @Test
    fun defaultLayoutContainsEveryCompiledSectionInReferenceOrder() {
        assertEquals(
            listOf(
                HomeSection.WELCOME,
                HomeSection.COMMUNITY_SNAPSHOT,
                HomeSection.QUICK_ACCESS,
                HomeSection.CONTINUE_DRAFT,
                HomeSection.PENDING_SYNC,
                HomeSection.LATEST_UPDATES,
                HomeSection.HELP,
            ),
            HomeLayout.default().sections,
        )
    }

    @Test
    fun validMinimalLayoutPreservesOnlyApprovedMandatorySections() {
        val layout = HomeLayout(
            listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.HELP),
        )

        assertTrue(HomeLayout.isValid(layout))
        assertEquals(layout, HomeLayout.validatedOrDefault(layout))
    }

    @Test
    fun duplicateOrIncompleteLayoutFallsBackToCompiledDefault() {
        val duplicate = HomeLayout(
            listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.QUICK_ACCESS, HomeSection.HELP),
        )
        val incomplete = HomeLayout(
            listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.NEXT_STEPS),
        )

        assertFalse(HomeLayout.isValid(duplicate))
        assertFalse(HomeLayout.isValid(incomplete))
        assertEquals(HomeLayout.default(), HomeLayout.validatedOrDefault(duplicate))
        assertEquals(HomeLayout.default(), HomeLayout.validatedOrDefault(incomplete))
    }

    @Test
    fun malformedOrFuturePayloadUsesDefaultButValidPayloadRoundTrips() {
        val valid = """{"schemaVersion":1,"home":{"sections":["WELCOME","QUICK_ACCESS","HELP"]}}"""
        val future = """{"schemaVersion":3,"home":{"sections":["WELCOME","QUICK_ACCESS","HELP"]}}"""
        val malformed = """{"schemaVersion":1,"home":{"sections":["WELCOME","UNKNOWN","HELP"]}}"""

        assertEquals(
            listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.HELP),
            GlobalUiConfiguration.decodeOrNull(valid)?.home?.sections,
        )
        assertEquals(GlobalUiConfiguration.default(), GlobalUiConfiguration.decodeOrDefault(future))
        assertEquals(GlobalUiConfiguration.default(), GlobalUiConfiguration.decodeOrDefault(malformed))
    }
}
