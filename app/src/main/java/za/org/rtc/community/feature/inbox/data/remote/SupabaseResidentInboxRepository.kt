package za.org.rtc.community.feature.inbox.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.core.CaseStage
import za.org.rtc.community.feature.inbox.domain.ResidentInboxItem
import za.org.rtc.community.feature.inbox.domain.ResidentInboxRepository
import za.org.rtc.community.feature.inbox.domain.ResidentInboxSourceType
import za.org.rtc.community.feature.inbox.domain.ResidentInboxTab
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.supabase.ProductionUxRepository

@Singleton
class SupabaseResidentInboxRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val productionUxRepository: ProductionUxRepository,
) : ResidentInboxRepository {
    override suspend fun page(tab: ResidentInboxTab, offset: Int, limit: Int): Result<List<ResidentInboxItem>> = runCatching {
        require(offset >= 0) { "Inbox offset cannot be negative." }
        val rpcResult = runCatching {
            supabase.postgrest.rpc("resident_inbox_page", buildJsonObject {
                put("p_tab", tab.name)
                put("p_offset", offset.coerceAtMost(1000))
                put("p_limit", limit.coerceIn(1, 50))
            }).decodeSingle<JsonArray>()
                .map { it as JsonObject }
                .map { item -> item.toResidentInboxItem() }
        }

        if (rpcResult.isSuccess && rpcResult.getOrNull()?.isNotEmpty() == true) {
            return rpcResult
        }

        // Resilient fallback aggregation from live production feeds
        when (tab) {
            ResidentInboxTab.UPDATES -> {
                val alerts = productionUxRepository.communityAlertInbox().getOrDefault(emptyList())
                alerts.map { alert ->
                    val dateInstant = try {
                        Instant.parse(alert.publishedAt ?: alert.createdAt)
                    } catch (_: Exception) {
                        Instant.now()
                    }
                    ResidentInboxItem(
                        sourceType = ResidentInboxSourceType.NOTIFICATION,
                        sourceId = alert.id,
                        category = alert.category.name,
                        title = alert.title,
                        body = alert.summary,
                        occurredAt = dateInstant,
                        route = RtcRoute.alertDetail(alert.id),
                        isRead = !alert.unread,
                    )
                }.sortedByDescending { it.occurredAt }
            }
            ResidentInboxTab.MESSAGES -> {
                val cases = productionUxRepository.mySupportCases().getOrDefault(emptyList())
                cases.map { caseItem ->
                    val dateInstant = try {
                        Instant.parse(caseItem.updatedAt)
                    } catch (_: Exception) {
                        Instant.now()
                    }
                    ResidentInboxItem(
                        sourceType = ResidentInboxSourceType.SUPPORT_MESSAGE,
                        sourceId = caseItem.id,
                        category = caseItem.category.replace('_', ' '),
                        title = "Support Ticket: ${caseItem.title}",
                        body = "Stage: ${caseItem.stage.label}",
                        occurredAt = dateInstant,
                        route = RtcRoute.HELP,
                        isRead = caseItem.stage == CaseStage.RESOLVED,
                    )
                }.sortedByDescending { it.occurredAt }
            }
        }
    }

    override suspend fun markRead(item: ResidentInboxItem): Result<Unit> = runCatching {
        val rpcResult = runCatching {
            supabase.postgrest.rpc("mark_resident_inbox_item_read", buildJsonObject {
                put("p_source_type", item.sourceType.name)
                put("p_source_id", item.sourceId)
            })
        }
        if (rpcResult.isSuccess) return@runCatching

        if (item.sourceType == ResidentInboxSourceType.NOTIFICATION) {
            productionUxRepository.markCommunityAlertRead(item.sourceId).getOrThrow()
        }
    }

    private fun JsonObject.toResidentInboxItem(): ResidentInboxItem = ResidentInboxItem(
        sourceType = requiredString("source_type").let(ResidentInboxSourceType::valueOf),
        sourceId = requiredString("source_id"),
        category = requiredString("category"),
        title = requiredString("title"),
        body = requiredString("body"),
        occurredAt = try { Instant.parse(requiredString("occurred_at")) } catch (_: Exception) { Instant.now() },
        route = requiredString("route"),
        isRead = get("is_read")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
    )

    private fun JsonObject.requiredString(key: String): String =
        get(key)?.jsonPrimitive?.contentOrNull ?: error("Missing Inbox response field: $key")
}
