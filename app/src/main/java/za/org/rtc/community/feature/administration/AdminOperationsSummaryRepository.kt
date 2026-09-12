package za.org.rtc.community.feature.administration

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Singleton
class AdminOperationsSummaryRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun load(): Result<AdminOperationsSummary> = runCatching {
        supabase.postgrest
            .rpc("ops_workspace_summary_v1")
            .decodeSingle<AdminOperationsSummary>()
    }
}
