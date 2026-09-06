package za.org.rtc.community.feature.inbox.domain

import java.time.Instant

enum class ResidentInboxTab { UPDATES, MESSAGES }

enum class ResidentInboxSourceType { NOTIFICATION, SUPPORT_MESSAGE, SERVICE_CENTRE_MESSAGE }

data class ResidentInboxItem(
    val sourceType: ResidentInboxSourceType,
    val sourceId: String,
    val category: String,
    val title: String,
    val body: String,
    val occurredAt: Instant,
    val route: String,
    val isRead: Boolean,
)

interface ResidentInboxRepository {
    suspend fun page(tab: ResidentInboxTab, offset: Int = 0, limit: Int = 30): Result<List<ResidentInboxItem>>
    suspend fun markRead(item: ResidentInboxItem): Result<Unit>
}
