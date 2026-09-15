package za.org.rtc.community.feature.dailypost.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.DailyPostTemplateStyle

@Entity(tableName = "daily_post_articles")
data class DailyPostEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val content: String,
    val category: String,
    val authorName: String,
    val authorRole: String,
    val templateStyle: String,
    val accentColorHex: String,
    val coverImageUrl: String?,
    val keyHighlightsJson: String,
    val quoteText: String?,
    val quoteAuthor: String?,
    val publishedAtEpochMillis: Long,
    val readTimeMinutes: Int,
    val reactionsCount: Int,
    val viewerHasLiked: Boolean,
    val isPublished: Boolean,
) {
    fun toDomain(): DailyPostArticle {
        val highlights = runCatching {
            Json.decodeFromString<List<String>>(keyHighlightsJson)
        }.getOrDefault(emptyList())

        return DailyPostArticle(
            id = id,
            title = title,
            subtitle = subtitle,
            content = content,
            category = category,
            authorName = authorName,
            authorRole = authorRole,
            templateStyle = DailyPostTemplateStyle.fromName(templateStyle),
            accentColorHex = accentColorHex,
            coverImageUrl = coverImageUrl,
            keyHighlights = highlights,
            quoteText = quoteText,
            quoteAuthor = quoteAuthor,
            publishedAtEpochMillis = publishedAtEpochMillis,
            readTimeMinutes = readTimeMinutes,
            reactionsCount = reactionsCount,
            viewerHasLiked = viewerHasLiked,
            isPublished = isPublished,
        )
    }

    companion object {
        fun fromDomain(article: DailyPostArticle): DailyPostEntity {
            val highlightsJson = Json.encodeToString(article.keyHighlights)
            return DailyPostEntity(
                id = article.id,
                title = article.title,
                subtitle = article.subtitle,
                content = article.content,
                category = article.category,
                authorName = article.authorName,
                authorRole = article.authorRole,
                templateStyle = article.templateStyle.name,
                accentColorHex = article.accentColorHex,
                coverImageUrl = article.coverImageUrl,
                keyHighlightsJson = highlightsJson,
                quoteText = article.quoteText,
                quoteAuthor = article.quoteAuthor,
                publishedAtEpochMillis = article.publishedAtEpochMillis,
                readTimeMinutes = article.readTimeMinutes,
                reactionsCount = article.reactionsCount,
                viewerHasLiked = article.viewerHasLiked,
                isPublished = article.isPublished,
            )
        }
    }
}

@Dao
interface DailyPostDao {
    @Query("SELECT * FROM daily_post_articles WHERE isPublished = 1 ORDER BY publishedAtEpochMillis DESC")
    fun observePublishedArticles(): Flow<List<DailyPostEntity>>

    @Query("SELECT * FROM daily_post_articles ORDER BY publishedAtEpochMillis DESC")
    fun observeAllArticles(): Flow<List<DailyPostEntity>>

    @Query("SELECT * FROM daily_post_articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): DailyPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: DailyPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<DailyPostEntity>)

    @Query("DELETE FROM daily_post_articles WHERE id = :id")
    suspend fun deleteArticle(id: String)

    @Query("UPDATE daily_post_articles SET reactionsCount = reactionsCount + CASE WHEN viewerHasLiked = 1 THEN -1 ELSE 1 END, viewerHasLiked = CASE WHEN viewerHasLiked = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleLike(id: String)
}
