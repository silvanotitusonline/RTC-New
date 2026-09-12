package za.org.rtc.community.data.ui_config

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.UploadData
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.UiConfigurationValidator
import za.org.rtc.community.data.local.UserPreferencesStore

@Serializable
data class UiConfigurationVersionSummary(
    @SerialName("version_id") val versionId: String,
    val state: String,
    @SerialName("created_reason") val createdReason: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val configuration: JsonObject,
)

data class UiConfigurationCommit(
    val versionId: String,
    val effectiveConfigurationRefreshed: Boolean,
)

@Serializable
private data class EffectiveUiConfigurationRow(
    @SerialName("version_id") val versionId: String,
    val configuration: JsonObject,
)

@Serializable
private data class UiAssetTicket(
    @SerialName("asset_id") val assetId: String,
    @SerialName("object_path") val objectPath: String,
)

@Serializable
private data class UiAssetPathRow(
    @SerialName("object_path") val objectPath: String,
)

@Singleton
class UiConfigurationRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val preferencesStore: UserPreferencesStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _effectiveConfiguration = MutableStateFlow(GlobalUiConfiguration.default())
    val effectiveConfiguration: StateFlow<GlobalUiConfiguration> = _effectiveConfiguration.asStateFlow()

    private val _effectiveVersionId = MutableStateFlow<String?>(null)
    val effectiveVersionId: StateFlow<String?> = _effectiveVersionId.asStateFlow()

    init {
        scope.launch {
            preferencesStore.cachedGlobalUiConfiguration.collectLatest { cached ->
                _effectiveConfiguration.value = cached.configuration
                _effectiveVersionId.value = cached.versionId
            }
        }
    }

    suspend fun refreshEffectiveConfiguration(): Result<GlobalUiConfiguration> = runCatching {
        val row = supabase.postgrest.rpc("ui_configuration_effective_global")
            .decodeList<EffectiveUiConfigurationRow>()
            .firstOrNull()
            ?: return@runCatching _effectiveConfiguration.value
        val raw = row.configuration.toString()
        val decoded = GlobalUiConfiguration.decodeOrNull(raw)
            ?: error("Published UI configuration was rejected by the client validator.")
        if (preferencesStore.saveLastKnownGoodGlobalUiConfiguration(row.versionId, raw)) {
            _effectiveConfiguration.value = decoded
            _effectiveVersionId.value = row.versionId
        }
        decoded
    }

    suspend fun createDraft(configuration: GlobalUiConfiguration, reason: String): Result<String> = runCatching {
        require(UiConfigurationValidator.isValid(configuration)) { "Correct blocking validation errors before saving." }
        require(reason.trim().length in 3..500) { "Provide a draft reason between 3 and 500 characters." }
        val basedOnVersionId = _effectiveVersionId.value
        supabase.postgrest.rpc(
            "ui_configuration_create_draft",
            buildJsonObject {
                put("p_configuration", Json.parseToJsonElement(GlobalUiConfiguration.encode(configuration)))
                put("p_reason", reason.trim())
                basedOnVersionId?.let { put("p_based_on_version_id", it) }
            },
        ).decodeSingle<String>()
    }

    suspend fun publishDraft(
        draftId: String,
        reason: String,
        confirmation: String,
    ): Result<UiConfigurationCommit> = runCatching {
        require(confirmation.trim() == PUBLISH_CONFIRMATION) { "Type $PUBLISH_CONFIRMATION to publish." }
        require(reason.trim().length in 3..500) { "Provide a publication reason between 3 and 500 characters." }
        val versionId = supabase.postgrest.rpc(
            "ui_configuration_publish_draft",
            buildJsonObject {
                put("p_draft_id", draftId)
                put("p_reason", reason.trim())
                put("p_confirmation", confirmation.trim())
            },
        ).decodeSingle<String>()
        UiConfigurationCommit(
            versionId = versionId,
            effectiveConfigurationRefreshed = refreshEffectiveConfiguration().isSuccess,
        )
    }

    suspend fun history(limit: Int = 40): Result<List<UiConfigurationVersionSummary>> = runCatching {
        supabase.postgrest.rpc(
            "ui_configuration_admin_history",
            buildJsonObject { put("p_limit", limit.coerceIn(1, 100)) },
        ).decodeList<UiConfigurationVersionSummary>()
    }

    suspend fun revert(
        versionId: String,
        reason: String,
        confirmation: String,
    ): Result<UiConfigurationCommit> = runCatching {
        require(confirmation.trim() == PUBLISH_CONFIRMATION) { "Type $PUBLISH_CONFIRMATION to restore this version." }
        require(reason.trim().length in 3..500) { "Provide a restore reason between 3 and 500 characters." }
        val restored = supabase.postgrest.rpc(
            "ui_configuration_revert",
            buildJsonObject {
                put("p_version_id", versionId)
                put("p_reason", reason.trim())
                put("p_confirmation", confirmation.trim())
            },
        ).decodeSingle<String>()
        UiConfigurationCommit(
            versionId = restored,
            effectiveConfigurationRefreshed = refreshEffectiveConfiguration().isSuccess,
        )
    }

    suspend fun uploadUiImage(
        bytes: ByteArray,
        mimeType: String,
        width: Int,
        height: Int,
    ): Result<String> = runCatching {
        require(bytes.size in 1..MAX_UI_IMAGE_BYTES) { "UI image must be 8 MiB or smaller." }
        require(mimeType in ALLOWED_UI_IMAGE_TYPES) { "Use JPEG, PNG or WebP." }
        require(width in 1..MAX_UI_IMAGE_DIMENSION && height in 1..MAX_UI_IMAGE_DIMENSION) {
            "UI image dimensions are not supported."
        }
        val ticket = supabase.postgrest.rpc(
            "ui_configuration_asset_begin",
            buildJsonObject {
                put("p_mime_type", mimeType)
                put("p_byte_size", bytes.size)
                put("p_width", width)
                put("p_height", height)
            },
        ).decodeSingle<UiAssetTicket>()
        val bucket = supabase.storage.from(UI_ASSET_BUCKET)
        bucket.upload(
            ticket.objectPath,
            UploadData(ByteArrayInputStream(bytes).toByteReadChannel(), bytes.size.toLong()),
        ) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
        supabase.postgrest.rpc(
            "ui_configuration_asset_finalize",
            buildJsonObject { put("p_asset_id", ticket.assetId) },
        )
        ticket.assetId
    }

    suspend fun signedUiImageUrl(assetId: String): Result<String> = runCatching {
        val path = uiAssetPath(assetId)
        supabase.storage.from(UI_ASSET_BUCKET).createSignedUrl(path, 10.minutes)
    }

    suspend fun deleteDraftAsset(assetId: String): Result<Unit> = runCatching {
        val path = uiAssetPath(assetId)
        // Storage RLS and the RPC both fail closed when the asset is published or not owned by
        // the current verified System Administrator. Client checks are not the security boundary.
        supabase.storage.from(UI_ASSET_BUCKET).delete(path)
        supabase.postgrest.rpc(
            "ui_configuration_asset_delete",
            buildJsonObject { put("p_asset_id", assetId) },
        )
        Unit
    }

    private suspend fun uiAssetPath(assetId: String): String =
        supabase.postgrest.rpc(
            "ui_configuration_asset_path",
            buildJsonObject { put("p_asset_id", assetId) },
        ).decodeSingle<UiAssetPathRow>().objectPath

    companion object {
        const val UI_ASSET_BUCKET = "rtc-ui-assets"
        const val MAX_UI_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_UI_IMAGE_DIMENSION = 8192
        const val PUBLISH_CONFIRMATION = "PUBLISH UI CONFIGURATION"
        val ALLOWED_UI_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
