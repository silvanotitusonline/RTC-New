package za.org.rtc.community.feature.dailypost.domain

import kotlinx.serialization.Serializable

@Serializable
enum class DailyPostTemplateStyle(val displayName: String, val description: String, val iconName: String) {
    MODERN_BLOG(
        displayName = "Modern Blog",
        description = "Clean editorial styling with structured sections, pull quotes, and key takeaway badges.",
        iconName = "Article"
    ),
    CANVA_HERO(
        displayName = "Canva Hero Graphic",
        description = "Vibrant social banner with gradient hero card, bold headline, and visual highlight chips.",
        iconName = "Palette"
    ),
    CIVIC_SPOTLIGHT(
        displayName = "Civic Spotlight",
        description = "Official municipal bulletin frame with administrative stamp, structured briefing, and signatory.",
        iconName = "Shield"
    ),
    MAGAZINE_STORY(
        displayName = "Magazine Feature",
        description = "Photo-forward visual magazine layout with stylish typography, hero cover, and quote highlight.",
        iconName = "AutoStories"
    ),
    BREAKING_BULLETIN(
        displayName = "Breaking Bulletin",
        description = "High-impact urgent administration announcement with alert banner and rapid takeaways.",
        iconName = "Campaign"
    ),
    MINIMALIST_EDITORIAL(
        displayName = "Minimalist Essay",
        description = "Elegant distraction-free typographic essay layout with refined spacing and quiet luxury aesthetic.",
        iconName = "MenuBook"
    );

    companion object {
        fun fromName(name: String?): DailyPostTemplateStyle {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MODERN_BLOG
        }
    }
}

@Serializable
data class DailyPostArticle(
    val id: String,
    val title: String,
    val subtitle: String,
    val content: String,
    val category: String,
    val authorName: String,
    val authorRole: String,
    val templateStyle: DailyPostTemplateStyle,
    val accentColorHex: String = "#1A365D", // Default Civic Navy
    val coverImageUrl: String? = null,
    val keyHighlights: List<String> = emptyList(),
    val quoteText: String? = null,
    val quoteAuthor: String? = null,
    val publishedAtEpochMillis: Long = System.currentTimeMillis(),
    val readTimeMinutes: Int = 3,
    val reactionsCount: Int = 0,
    val commentCount: Int = 0,
    val viewerHasLiked: Boolean = false,
    val isPublished: Boolean = true,
    val pushEnabled: Boolean = false,
    val previewPopupEnabled: Boolean = true,
)

@Serializable
data class DailyPostComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String = "Community member",
    val body: String,
    val parentId: String? = null,
    val depth: Int = 0,
    val state: String = "VISIBLE",
    val moderationReason: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

data class DailyPostCommentPage(
    val comments: List<DailyPostComment>,
    val hasMore: Boolean,
)

fun calculateEstimatedReadingTimeMinutes(
    title: String,
    subtitle: String,
    content: String,
    keyHighlights: List<String> = emptyList(),
    wordsPerMinute: Int = 200
): Int {
    val fullText = buildString {
        append(title)
        append(" ")
        append(subtitle)
        append(" ")
        append(content)
        if (keyHighlights.isNotEmpty()) {
            append(" ")
            append(keyHighlights.joinToString(" "))
        }
    }.trim()
    if (fullText.isBlank()) return 1
    val wordCount = fullText.split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, Math.ceil(wordCount.toDouble() / wordsPerMinute).toInt())
}

data class DailyPostColorTheme(
    val name: String,
    val hex: String,
    val darkHex: String,
)

object DailyPostPresets {
    val Categories = listOf(
        "Community News",
        "Municipal Update",
        "Infrastructure & Services",
        "Public Safety",
        "Youth & Development",
        "Local Economy",
        "Civic Spotlight",
        "Health & Wellness",
    )

    val ColorPalettes = listOf(
        DailyPostColorTheme("Civic Navy", "#1A365D", "#0F2440"),
        DailyPostColorTheme("Emerald Green", "#065F46", "#022C22"),
        DailyPostColorTheme("Vibrant Indigo", "#3730A3", "#1E1B4B"),
        DailyPostColorTheme("Crimson Alert", "#991B1B", "#450A0A"),
        DailyPostColorTheme("Sunset Terracotta", "#C2410C", "#431407"),
        DailyPostColorTheme("Royal Violet", "#6D28D9", "#2E1065"),
        DailyPostColorTheme("Teal Ocean", "#0F766E", "#134E4A"),
        DailyPostColorTheme("Golden Amber", "#B45309", "#451A03"),
    )
}
