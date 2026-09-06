package za.org.rtc.community.feature.marketplace.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.feature.marketplace.presentation.components.MarketplaceDescriptionSuggestionsData
import za.org.rtc.community.feature.marketplace.presentation.components.SuggestionStyle

class MarketplaceDescriptionSuggestionsTest {

    @Test
    fun getSuggestionsForCategory_returnsSpecificAndGeneralSuggestions() {
        val servicesSuggestions = MarketplaceDescriptionSuggestionsData.getSuggestionsForCategory("services")
        assertTrue("Should contain at least 2 suggestions for services", servicesSuggestions.size >= 2)
        assertTrue(servicesSuggestions.any { it.categoryId == "services" })
        assertTrue(servicesSuggestions.any { it.categoryId == "general" })
    }

    @Test
    fun getSuggestionsForCategory_foodCategoryContainsRelevantTemplates() {
        val foodSuggestions = MarketplaceDescriptionSuggestionsData.getSuggestionsForCategory("food")
        assertTrue(foodSuggestions.any { it.categoryId == "food" })
        val bulletsSuggestion = foodSuggestions.firstOrNull { it.style == SuggestionStyle.BULLETS && it.categoryId == "food" }
        assertTrue(bulletsSuggestion != null)
    }

    @Test
    fun templateInterpolation_includesBusinessNameWhenPresent() {
        val suggestion = MarketplaceDescriptionSuggestionsData.suggestions.first { it.id == "services_overview" }
        val resultWithName = suggestion.template("Khayelitsha Fixers")
        assertTrue("Should interpolate business name into result", resultWithName.contains("At Khayelitsha Fixers, we provide"))

        val resultWithoutName = suggestion.template("")
        assertTrue("Should fallback gracefully when name is empty", resultWithoutName.startsWith("We provide"))
    }

    @Test
    fun templateInterpolation_bulletsStructureFormatsCorrectly() {
        val bulletsSuggestion = MarketplaceDescriptionSuggestionsData.suggestions.first { it.id == "tech_bullets" }
        val output = bulletsSuggestion.template("Phakamisa Tech")
        assertTrue(output.contains("Phakamisa Tech - Tech Services:"))
        assertTrue(output.contains("• Screen & battery replacements"))
    }

    @Test
    fun fallbackForUnknownCategory_returnsGeneralTemplates() {
        val unknownSuggestions = MarketplaceDescriptionSuggestionsData.getSuggestionsForCategory("non_existent_category_123")
        assertTrue(unknownSuggestions.isNotEmpty())
        assertTrue(unknownSuggestions.all { it.categoryId == "general" })
    }
}
