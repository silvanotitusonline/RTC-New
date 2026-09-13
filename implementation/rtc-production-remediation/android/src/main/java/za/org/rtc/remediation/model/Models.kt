package za.org.rtc.remediation.model

import kotlinx.serialization.Serializable

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T) : LoadState<T>
    data class Failure(val message: String) : LoadState<Nothing>
}
@Serializable data class FeedPost(
    val id: String, val authorId: String, val body: String, val createdAt: String,
    val imageUrl: String? = null, val imageWidth: Int? = null, val imageHeight: Int? = null,
    val voteCount: Int = 0, val votedByMe: Boolean = false,
)
@Serializable data class VoteInput(val voted: Boolean)
@Serializable data class VoteResult(val postId: String, val voteCount: Int, val votedByMe: Boolean)
@Serializable data class ReportInput(
    val body: String, val category: String, val priority: String,
    val latitude: Double? = null, val longitude: Double? = null,
)
@Serializable data class Report(
    val id: String, val body: String, val category: String, val priority: String,
    val createdAt: String, val status: String = "PENDING",
    val latitude: Double? = null, val longitude: Double? = null,
)
@Serializable data class ReportSummary(val pending: Int, val working: Int, val resolved: Int, val total: Int)
@Serializable data class Provider(
    val id: String, val name: String, val rateCents: Long, val imageUrl: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
)
@Serializable data class BookingInput(val providerId: String, val startsAt: String, val notes: String)
@Serializable data class Booking(
    val id: String, val providerId: String, val startsAt: String, val notes: String,
    val status: String = "PENDING", val createdAt: String,
)
@Serializable data class RouteDistance(val distanceMeters: Long, val travelTimeSeconds: Long, val calculatedAt: String)
@Serializable data class Place(val id: String, val name: String, val latitude: Double, val longitude: Double)
@Serializable data class PreparedImage(val path: String, val mimeType: String)
data class OutboxItem(val id: String, val kind: String, val state: String, val lastError: String?, val preview: String = "", val imagePath: String? = null, val createdAt: String = "")
@Serializable internal data class PostInput(val body: String)
@Serializable data class ApiError(val code: String, val message: String)
@Serializable data class ErrorEnvelope(val error: ApiError)
