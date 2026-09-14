package za.org.rtc.community.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.HomeImagePlacement
import za.org.rtc.community.core.HomeImageWidget
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.HomeSection

class HomeRenderingTest {
    private val widget = HomeImageWidget(
        assetId = "550e8400-e29b-41d4-a716-446655440000",
        anchorSection = HomeSection.QUICK_ACCESS,
        placement = HomeImagePlacement.AFTER,
        altText = "Residents meeting at a community event",
    )

    @Test
    fun imageIsRenderedImmediatelyAfterConfiguredAnchor() {
        val layout = HomeLayout(
            sections = listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.HELP),
            imageWidget = widget,
        )
        assertEquals(
            listOf(
                HomeRenderItem.Section(HomeSection.WELCOME),
                HomeRenderItem.Section(HomeSection.QUICK_ACCESS),
                HomeRenderItem.Image(widget),
                HomeRenderItem.Section(HomeSection.HELP),
            ),
            layout.renderItems(),
        )
    }

    @Test
    fun imageIsRenderedImmediatelyBeforeConfiguredAnchor() {
        val before = widget.copy(placement = HomeImagePlacement.BEFORE)
        val layout = HomeLayout(
            sections = listOf(HomeSection.WELCOME, HomeSection.QUICK_ACCESS, HomeSection.HELP),
            imageWidget = before,
        )
        val items = layout.renderItems()
        assertEquals(HomeRenderItem.Image(before), items[1])
        assertEquals(HomeRenderItem.Section(HomeSection.QUICK_ACCESS), items[2])
    }

    @Test
    fun invalidHomeLayoutFallsBackToCompiledSafeCatalogue() {
        val invalid = HomeLayout(sections = listOf(HomeSection.WELCOME, HomeSection.WELCOME))
        val items = invalid.renderItems()
        assertTrue(items.filterIsInstance<HomeRenderItem.Image>().isEmpty())
        assertEquals(HomeLayout.default().sections, items.filterIsInstance<HomeRenderItem.Section>().map { it.section })
    }
}
