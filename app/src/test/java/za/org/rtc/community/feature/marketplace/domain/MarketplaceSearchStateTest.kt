package za.org.rtc.community.feature.marketplace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceSearchStateTest {
    @Test
    fun replacePageResetsItemsAndCarriesNextOffset() {
        val previous = MarketplaceSearchUiState(
            filters = MarketplaceSearchFilters(query = "old"),
            items = listOf(card("old")),
            nextOffset = 20,
            hasMore = true,
        )
        val page = MarketplaceSearchPage(
            items = listOf(card("new-1"), card("new-2")),
            nextOffset = 2,
            hasMore = true,
        )

        val replaced = previous.replaceWith(page, MarketplaceSearchFilters(query = "new"))

        assertEquals(listOf("new-1", "new-2"), replaced.items.map { it.id })
        assertEquals(2, replaced.nextOffset)
        assertTrue(replaced.hasMore)
        assertFalse(replaced.isRefreshing)
        assertFalse(replaced.isLoadingMore)
    }

    @Test
    fun appendPagePreservesExistingOrderAndStopsAtLastPage() {
        val state = MarketplaceSearchUiState(
            filters = MarketplaceSearchFilters(query = "market"),
            items = listOf(card("1"), card("2")),
            nextOffset = 2,
            hasMore = true,
            isLoadingMore = true,
        )
        val lastPage = MarketplaceSearchPage(
            items = listOf(card("3")),
            nextOffset = null,
            hasMore = false,
        )

        val appended = state.append(lastPage)

        assertEquals(listOf("1", "2", "3"), appended.items.map { it.id })
        assertEquals(null, appended.nextOffset)
        assertFalse(appended.hasMore)
        assertFalse(appended.isLoadingMore)
    }

    @Test
    fun appendPageDeduplicatesBusinessIdsWithoutReorderingExistingItems() {
        val state = MarketplaceSearchUiState(
            items = listOf(card("1"), card("2")),
            nextOffset = 2,
            hasMore = true,
        )
        val page = MarketplaceSearchPage(
            items = listOf(card("2"), card("3"), card("3")),
            nextOffset = 5,
            hasMore = true,
        )

        val appended = state.append(page)

        assertEquals(listOf("1", "2", "3"), appended.items.map { it.id })
        assertEquals(5, appended.nextOffset)
        assertTrue(appended.hasMore)
    }

    @Test
    fun replacePageDeduplicatesMalformedDuplicateIds() {
        val page = MarketplaceSearchPage(
            items = listOf(card("1"), card("1"), card("2")),
            nextOffset = 3,
            hasMore = true,
        )

        val replaced = MarketplaceSearchUiState().replaceWith(page, MarketplaceSearchFilters())

        assertEquals(listOf("1", "2"), replaced.items.map { it.id })
    }

    @Test
    fun radiusIsBoundedToMarketplaceDiscoveryMaximum() {
        assertEquals(100, MarketplaceSearchFilters(radiusMetres = 1).boundedRadiusMetres)
        assertEquals(25_000, MarketplaceSearchFilters(radiusMetres = 25_000).boundedRadiusMetres)
        assertEquals(50_000, MarketplaceSearchFilters(radiusMetres = 75_000).boundedRadiusMetres)
    }

    private fun card(id: String) = MarketplaceBusinessCard(
        id = id,
        slug = id,
        displayName = id,
        tagline = "",
        category = "",
        locality = "",
        ratingAverage = 0.0,
        reviewCount = 0,
        weightedScore = 0.0,
        distanceMetres = null,
        logoPath = null,
        verified = false,
        featured = false,
    )
}
