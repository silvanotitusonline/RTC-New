package za.org.rtc.community.feature.administration

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AdminOperationsSummary(
    @SerialName("assigned_to_me") val assignedToMe: Long = 0,
    @SerialName("high_priority") val highPriority: Long = 0,
    val unassigned: Long = 0,
    @SerialName("ready_for_review") val readyForReview: Long = 0,
    val overdue: Long = 0,
    @SerialName("total_visible") val totalVisible: Long = 0,
)

@Serializable
data class AdminEligibleAssignee(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
)

@Singleton
class AdminOperationsSummaryRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun load(): Result<AdminOperationsSummary> = runCatching {
        supabase.postgrest
            .rpc("ops_workspace_summary_v1")
            .decodeSingle<AdminOperationsSummary>()
    }

    suspend fun loadEligibleAssignees(workItemId: String): Result<List<AdminEligibleAssignee>> = runCatching {
        supabase.postgrest
            .rpc("ops_list_eligible_assignees_v1", buildJsonObject {
                put("p_work_item_id", workItemId)
            })
            .decodeList<AdminEligibleAssignee>()
    }
}
