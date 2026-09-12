package za.org.rtc.community.feature.dailypost.domain

import java.time.Instant

enum class DailyPostState { DRAFT, SCHEDULED, PUBLISHED, ARCHIVED }
enum class DailyPostType { NEWS, BREAKING }
enum class DailyPostBlockType {
    HEADLINE, PARAGRAPH, SUBHEADING, IMAGE, VIDEO, MEDIA_GALLERY,
    PULL_QUOTE, INFO_CALLOUT, DIVIDER, QUOTED_PUBLICATION, CTA,
}

data class DailyPostBlock(
    val id: String,
    val type: DailyPostBlockType,
    val text: String = "",
    val mediaIds: List<String> = emptyList(),
    val quotedPostId: String? = null,
    val actionLabel: String? = null,
    val actionUrl: String? = null,
)

data class DailyPostMedia(
    val id: String,
    val postId: String,
    val storagePath: String,
    val mediaType: String,
    val mimeType: String,
    val altText: String,
    val sortOrder: Int,
    val signedUrl: String? = null,
)

data class DailyPost(
    val id: String,
    val authorId: String,
    val state: DailyPostState,
    val publicationType: DailyPostType,
    val templateKey: String,
    val canonicalLanguage: String,
    val headline: String,
    val excerpt: String,
    val blocks: List<DailyPostBlock>,
    val quotedPostId: String? = null,
    val scheduledFor: Instant? = null,
    val publishedAt: Instant? = null,
    val pushEnabled: Boolean = false,
    val previewPopupEnabled: Boolean = true,
    val revision: Int = 1,
    val commentCount: Int = 0,
)

data class DailyPostComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val parentId: String? = null,
    val depth: Int = 0,
    val body: String,
    val state: String = "VISIBLE",
    val authorDisplayName: String = "Resident",
    val authorAvatarUrl: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DailyPostTranslation(
    val postId: String,
    val revision: Int,
    val targetLanguage: String,
    val headline: String,
    val excerpt: String,
    val blocks: List<DailyPostBlock>,
    val audioUrl: String? = null,
)

data class DailyPostCursor(val publishedAt: Instant, val id: String)
data class DailyPostPage(val items: List<DailyPost>, val nextCursor: DailyPostCursor?)

data class DailyPostDraft(
    val id: String? = null,
    val publicationType: DailyPostType = DailyPostType.NEWS,
    val templateKey: String = "standard_news",
    val canonicalLanguage: String = "en",
    val headline: String = "",
    val excerpt: String = "",
    val blocks: List<DailyPostBlock> = emptyList(),
    val quotedPostId: String? = null,
    val pushEnabled: Boolean = false,
    val previewPopupEnabled: Boolean = true,
    val scheduledFor: Instant? = null,
)

data class DailyPostTemplate(
    val key: String,
    val name: String,
    val description: String,
    val starterBlocks: List<DailyPostBlockType>,
)

object DailyPostTemplates {
    val all = listOf(
        DailyPostTemplate("breaking_news", "Breaking News", "Urgent headline-led broadcast.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.IMAGE, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.INFO_CALLOUT)),
        DailyPostTemplate("hero_story", "Hero Story", "Large visual lead for major stories.", listOf(DailyPostBlockType.IMAGE, DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.PULL_QUOTE)),
        DailyPostTemplate("standard_news", "Standard News", "Balanced editorial story layout.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.IMAGE, DailyPostBlockType.PARAGRAPH)),
        DailyPostTemplate("photo_lead", "Photo Lead", "Image-first publication.", listOf(DailyPostBlockType.IMAGE, DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH)),
        DailyPostTemplate("gallery_story", "Gallery Story", "Multi-image visual narrative.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.MEDIA_GALLERY, DailyPostBlockType.PARAGRAPH)),
        DailyPostTemplate("video_lead", "Video Lead", "Video-first update.", listOf(DailyPostBlockType.VIDEO, DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH)),
        DailyPostTemplate("community_briefing", "Community Briefing", "Structured civic briefing.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.INFO_CALLOUT, DailyPostBlockType.SUBHEADING, DailyPostBlockType.PARAGRAPH)),
        DailyPostTemplate("announcement", "Announcement", "Concise official announcement.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.CTA)),
        DailyPostTemplate("feature_story", "Feature Story", "Long-form editorial feature.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.IMAGE, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.SUBHEADING, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.PULL_QUOTE)),
        DailyPostTemplate("minimal_update", "Minimal Update", "Fast text-forward update.", listOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH)),
    )
}
