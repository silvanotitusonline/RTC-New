package za.org.rtc.community.feature.dailypost.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import javax.inject.Inject
import javax.inject.Singleton

interface DailyPostRepository {
    fun observePublishedArticles(): Flow<List<DailyPostArticle>>
    fun observeAllArticles(): Flow<List<DailyPostArticle>>
    suspend fun getArticle(id: String): DailyPostArticle?
    suspend fun saveArticle(article: DailyPostArticle)
    suspend fun publishArticle(article: DailyPostArticle)
    suspend fun deleteArticle(id: String)
    suspend fun toggleLike(id: String)
}

@Singleton
class RoomDailyPostRepository @Inject constructor(
    private val dao: DailyPostDao,
) : DailyPostRepository {

    override fun observePublishedArticles(): Flow<List<DailyPostArticle>> {
        return dao.observePublishedArticles().map { list -> list.map { it.toDomain() } }
    }

    override fun observeAllArticles(): Flow<List<DailyPostArticle>> {
        return dao.observeAllArticles().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getArticle(id: String): DailyPostArticle? {
        return dao.getArticleById(id)?.toDomain()
    }

    override suspend fun saveArticle(article: DailyPostArticle) {
        dao.insertArticle(DailyPostEntity.fromDomain(article))
    }

    override suspend fun publishArticle(article: DailyPostArticle) {
        dao.insertArticle(DailyPostEntity.fromDomain(article.copy(isPublished = true)))
    }

    override suspend fun deleteArticle(id: String) {
        dao.deleteArticle(id)
    }

    override suspend fun toggleLike(id: String) {
        dao.toggleLike(id)
    }
}
