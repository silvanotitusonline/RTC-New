package za.org.rtc.community.feature.marketplace.domain

import kotlinx.serialization.json.JsonObject

enum class MarketplaceReviewReportReason(val code: String, val label: String) {
    SPAM("SPAM", "Spam or promotional content"),
    HARASSMENT("HARASSMENT", "Harassment or abuse"),
    HATE("HATE", "Hate or discriminatory content"),
    PRIVACY("PRIVACY", "Privacy or personal information"),
    MISINFORMATION("MISINFORMATION", "Misleading or false information"),
    OTHER("OTHER", "Other"),
}

enum class MarketplaceBusinessLifecycleAction {
    SUSPEND,
    REINSTATE;

    companion object {
        fun forState(state: String): Set<MarketplaceBusinessLifecycleAction> = when (state.uppercase()) {
            "PUBLISHED" -> setOf(SUSPEND)
            "SUSPENDED" -> setOf(REINSTATE)
            else -> emptySet()
        }
    }
}

data class MarketplaceAdminSubmissionDetail(
    val submissionId: String,
    val businessId: String,
    val revisionId: String,
    val displayName: String,
    val submissionState: String,
    val revisionState: String,
    val lifecycleState: String,
    val submittedRevision: JsonObject,
    val publishedRevision: JsonObject?,
    val locations: List<JsonObject>,
    val offerings: List<JsonObject>,
    val media: List<JsonObject>,
    val verification: List<JsonObject>,
    val history: List<JsonObject>,
) {
    val lifecycleActions: Set<MarketplaceBusinessLifecycleAction>
        get() = MarketplaceBusinessLifecycleAction.forState(lifecycleState)
}
