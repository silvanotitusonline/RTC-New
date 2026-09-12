package za.org.rtc.community.feature.dailypost.domain

interface DailyPostRepository {
    suspend fun page(cursor: DailyPostCursor? = null, limit: Int = 20): Result<DailyPostPage>
    suspend fun get(postId: String): Result<DailyPost?>
    suspend fun comments(postId: String): Result<List<DailyPostComment>>
    suspend fun addComment(postId: String, body: String, parentId: String? = null): Result<Unit>
    suspend fun updateComment(commentId: String, body: String): Result<Unit>
    suspend fun deleteComment(commentId: String): Result<Unit>
    suspend fun markPreviewPresented(postId: String): Result<Unit>
    suspend fun nextUnseenPreview(): Result<DailyPost?>
    suspend fun translate(postId: String, targetLanguage: String): Result<DailyPostTranslation>
    suspend fun narration(postId: String, targetLanguage: String? = null): Result<String>

    suspend fun adminPage(state: DailyPostState? = null, limit: Int = 100): Result<List<DailyPost>>
    suspend fun saveDraft(draft: DailyPostDraft): Result<String>
    suspend fun publishNow(postId: String, pushEnabled: Boolean, previewPopupEnabled: Boolean): Result<Unit>
    suspend fun schedule(postId: String, scheduledForIso: String, pushEnabled: Boolean, previewPopupEnabled: Boolean): Result<Unit>
    suspend fun archive(postId: String): Result<Unit>
}
