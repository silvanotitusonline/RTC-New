package za.org.rtc.community.feature.community

import kotlin.math.max

data class CommunityComposerAttachment(
    val id: String,
    val uri: String,
)

enum class CommunityUploadPhase {
    IDLE,
    PREPARING,
    UPLOADING,
    FAILED,
    COMPLETE,
}

data class CommunityUploadState(
    val phase: CommunityUploadPhase = CommunityUploadPhase.IDLE,
    val uploadedItems: Int = 0,
    val totalItems: Int = 0,
    val message: String? = null,
) {
    val fraction: Float
        get() = if (totalItems <= 0) 0f else uploadedItems.coerceIn(0, totalItems).toFloat() / totalItems.toFloat()

    val canRetry: Boolean
        get() = phase == CommunityUploadPhase.FAILED
}

data class CommunityComposerState(
    val body: String = "",
    val attachments: List<CommunityComposerAttachment> = emptyList(),
    val upload: CommunityUploadState = CommunityUploadState(),
) {
    val isDirty: Boolean
        get() = body.isNotBlank() || attachments.isNotEmpty()

    fun withBody(value: String): CommunityComposerState = copy(body = value)

    fun addAttachments(items: List<CommunityComposerAttachment>): CommunityComposerState {
        if (items.isEmpty()) return this
        val merged = LinkedHashMap<String, CommunityComposerAttachment>(attachments.size + items.size)
        attachments.forEach { merged[it.id] = it }
        items.forEach { merged[it.id] = it }
        return copy(attachments = merged.values.toList())
    }

    fun removeAttachment(id: String): CommunityComposerState = copy(
        attachments = attachments.filterNot { it.id == id },
    )

    fun beginUpload(totalItems: Int): CommunityComposerState = copy(
        upload = CommunityUploadState(
            phase = CommunityUploadPhase.PREPARING,
            uploadedItems = 0,
            totalItems = max(0, totalItems),
        ),
    )

    fun withUploadedItems(uploadedItems: Int): CommunityComposerState {
        val bounded = uploadedItems.coerceIn(0, upload.totalItems)
        return copy(
            upload = upload.copy(
                phase = if (upload.totalItems > 0 && bounded >= upload.totalItems) CommunityUploadPhase.COMPLETE else CommunityUploadPhase.UPLOADING,
                uploadedItems = bounded,
                message = null,
            ),
        )
    }

    fun withUploadFailure(message: String): CommunityComposerState = copy(
        upload = upload.copy(
            phase = CommunityUploadPhase.FAILED,
            message = message.ifBlank { "Upload interrupted" },
        ),
    )

    fun retryUpload(): CommunityComposerState = copy(
        upload = upload.copy(
            phase = CommunityUploadPhase.PREPARING,
            message = null,
        ),
    )

    fun resetUpload(): CommunityComposerState = copy(upload = CommunityUploadState())
}

data class CommunityUploadRecovery(
    val ownerId: String,
    val draftId: String,
    val uploadedItems: Int,
    val totalItems: Int,
) {
    val fraction: Float
        get() = if (totalItems <= 0) 0f else uploadedItems.coerceIn(0, totalItems).toFloat() / totalItems.toFloat()

    fun belongsTo(authenticatedOwnerId: String, candidateDraftId: String): Boolean =
        ownerId == authenticatedOwnerId && draftId == candidateDraftId
}
