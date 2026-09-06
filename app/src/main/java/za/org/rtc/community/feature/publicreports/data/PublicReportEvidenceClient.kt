package za.org.rtc.community.feature.publicreports.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.publicreports.domain.PublicReportEvidenceItem
import za.org.rtc.community.feature.publicreports.domain.PublicReportMediaKind

@Singleton
class PublicReportEvidenceClient @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(reportId: String): Result<List<PublicReportEvidenceItem>> = runCatching {
        supabase.postgrest.rpc(
            PublicReportRpcContract.EVIDENCE_PUBLIC,
            buildJsonObject { put("p_report_id", reportId) },
        ).decodeList<JsonObject>().map(::toItem)
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    suspend fun signedUrl(evidenceId: String): Result<String> = runCatching {
        val response = supabase.functions.invoke(
            PublicReportRpcContract.EVIDENCE_MEDIA_URL,
            buildJsonObject { put("evidenceId", evidenceId) },
        )
        check(response.status.value in 200..299) { "Evidence could not be opened." }
        json.decodeFromString<SignedUrlResponse>(response.bodyAsText()).url
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    private fun toItem(row: JsonObject): PublicReportEvidenceItem {
        require(row["storage_path"] == null || row["storage_path"]?.toString() == "null") {
            "Public evidence must not include a storage path."
        }
        val kind = row["media_kind"]?.jsonPrimitive?.contentOrNull?.uppercase()
        return PublicReportEvidenceItem(
            id = row["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            reportId = row["report_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mediaKind = if (kind == "VIDEO") PublicReportMediaKind.VIDEO else PublicReportMediaKind.IMAGE,
            mimeType = row["mime_type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            byteSize = row["byte_size"]?.jsonPrimitive?.longOrNull ?: 0L,
            width = row["width"]?.jsonPrimitive?.intOrNull,
            height = row["height"]?.jsonPrimitive?.intOrNull,
            durationSeconds = row["duration_seconds"]?.jsonPrimitive?.intOrNull,
            position = row["sort_position"]?.jsonPrimitive?.intOrNull
                ?: row["position"]?.jsonPrimitive?.intOrNull
                ?: 0,
        )
    }
}

@Serializable
private data class SignedUrlResponse(
    val url: String,
    @SerialName("expiresAt") val expiresAt: String? = null,
)
