package za.org.rtc.community.ui.config

import za.org.rtc.community.core.HomeImagePlacement
import za.org.rtc.community.core.HomeImageWidget
import za.org.rtc.community.core.HomeLayout
import za.org.rtc.community.core.HomeSection

/** Renderable Home items remain a finite compiled catalogue; remote configuration only reorders/selects them. */
sealed interface HomeRenderItem {
    data class Section(val section: HomeSection) : HomeRenderItem
    data class Image(val widget: HomeImageWidget) : HomeRenderItem
}

/**
 * Converts a validated Home layout into a deterministic render sequence. Invalid layouts fall
 * back to the compiled Home catalogue before any item reaches Compose.
 */
fun HomeLayout.renderItems(): List<HomeRenderItem> {
    val resolved = HomeLayout.validatedOrDefault(this)
    val widget = resolved.imageWidget
    return buildList {
        resolved.sections.forEach { section ->
            if (widget?.anchorSection == section && widget.placement == HomeImagePlacement.BEFORE) {
                add(HomeRenderItem.Image(widget))
            }
            add(HomeRenderItem.Section(section))
            if (widget?.anchorSection == section && widget.placement == HomeImagePlacement.AFTER) {
                add(HomeRenderItem.Image(widget))
            }
        }
    }
}

/**
 * Keeps the configured Home content that remains above the Daily Post snapshot. Filtering by
 * section identity, rather than by the former Quick Access position, preserves these sections
 * even when an administrator reorders the legacy layout configuration.
 */
fun List<HomeRenderItem>.retainedForDailyPostSnapshot(): List<HomeRenderItem> {
    val retainedSections = setOf(HomeSection.WELCOME, HomeSection.COMMUNITY_SNAPSHOT)
    return filter { item ->
        when (item) {
            is HomeRenderItem.Section -> item.section in retainedSections
            is HomeRenderItem.Image -> item.widget.anchorSection in retainedSections
        }
    }
}
