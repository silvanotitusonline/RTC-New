package za.org.rtc.community.feature.publicreports.domain

object PublicReportValidation {
    const val TITLE_MIN = 5
    const val TITLE_MAX = 80
    const val DESCRIPTION_MIN = 20
    const val DESCRIPTION_MAX = 2_000
    const val LOCATION_MIN = 3
    const val LOCATION_MAX = 180
    const val ADDRESS_MIN = 3
    const val ADDRESS_MAX = 500
    const val EXCEPTION_MIN = 20
    const val EXCEPTION_MAX = 300
    const val COMMENT_MIN = 1
    const val COMMENT_MAX = 1_000
    const val EVIDENCE_MAX = 6
    const val IMAGE_MAX_BYTES = 5L * 1024 * 1024
    const val VIDEO_MAX_BYTES = 20L * 1024 * 1024
    const val VIDEO_MAX_SECONDS = 180
    const val PAGE_MAX = 50
    const val CRITICAL_NOTICE =
        "Public Reports is not an emergency service. If anyone is in immediate danger, contact the appropriate emergency service first."
    const val ANONYMOUS_NOTICE =
        "Anonymous reports are hidden from public viewers. RTC can still associate the report with your account."

    val allowedImageMimes = setOf("image/jpeg", "image/png", "image/webp")
    val allowedVideoMimes = setOf("video/mp4", "video/webm")

    fun title(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.length < TITLE_MIN -> "Enter a short title of at least $TITLE_MIN characters."
            trimmed.length > TITLE_MAX -> "Keep the title to $TITLE_MAX characters or fewer."
            else -> null
        }
    }

    fun description(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.length < DESCRIPTION_MIN -> "Describe what is happening in at least $DESCRIPTION_MIN characters."
            trimmed.length > DESCRIPTION_MAX -> "Keep the description to $DESCRIPTION_MAX characters or fewer."
            else -> null
        }
    }

    fun urgency(value: PublicReportUrgency?): String? =
        if (value == null || value == PublicReportUrgency.UNKNOWN) "Choose an urgency." else null

    fun category(categoryId: String?): String? =
        if (categoryId.isNullOrBlank()) "Choose a category." else null

    fun identity(value: PublicReportIdentityMode?): String? =
        if (value == null || value == PublicReportIdentityMode.UNKNOWN) "Choose how your name should appear." else null

    fun publicLocation(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.length < LOCATION_MIN -> "Enter a public landmark or area of at least $LOCATION_MIN characters."
            trimmed.length > LOCATION_MAX -> "Keep the public location to $LOCATION_MAX characters or fewer."
            else -> null
        }
    }

    fun manualAddress(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return when {
            trimmed.length < ADDRESS_MIN -> "Enter a manual address or landmark of at least $ADDRESS_MIN characters."
            trimmed.length > ADDRESS_MAX -> "Keep the address to $ADDRESS_MAX characters or fewer."
            else -> null
        }
    }

    fun evidenceOrException(evidenceCount: Int, cannotProvideEvidence: Boolean, reason: String?): String? {
        if (evidenceCount > EVIDENCE_MAX) return "Attach at most $EVIDENCE_MAX files."
        if (evidenceCount > 0) return null
        if (!cannotProvideEvidence) return "Add evidence or confirm that you cannot safely provide evidence."
        return exceptionReason(reason)
    }

    fun exceptionReason(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return when {
            trimmed.length < EXCEPTION_MIN -> "Explain why evidence cannot be provided in at least $EXCEPTION_MIN characters."
            trimmed.length > EXCEPTION_MAX -> "Keep the explanation to $EXCEPTION_MAX characters or fewer."
            else -> null
        }
    }

    fun guidelinesAccepted(accepted: Boolean, version: String?): String? =
        if (!accepted || version.isNullOrBlank()) "Accept the current guidelines to submit a Public Report." else null

    fun comment(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.length < COMMENT_MIN -> "Enter a comment."
            trimmed.length > COMMENT_MAX -> "Keep comments to $COMMENT_MAX characters or fewer."
            else -> null
        }
    }

    fun evidenceMime(mime: String, kind: PublicReportMediaKind): String? {
        val allowed = if (kind == PublicReportMediaKind.IMAGE) allowedImageMimes else allowedVideoMimes
        return if (mime.lowercase() in allowed) null else "Choose a JPEG, PNG, WebP, MP4, or WebM file."
    }

    fun evidenceSize(kind: PublicReportMediaKind, bytes: Long, durationSeconds: Int?): String? = when (kind) {
        PublicReportMediaKind.IMAGE ->
            if (bytes in 1..IMAGE_MAX_BYTES && durationSeconds == null) null else "Images must be 5 MB or smaller."
        PublicReportMediaKind.VIDEO ->
            if (bytes in 1..VIDEO_MAX_BYTES && durationSeconds != null && durationSeconds in 1..VIDEO_MAX_SECONDS) {
                null
            } else {
                "Videos must be 20 MB or smaller and no longer than 3 minutes."
            }
    }

    fun draft(draft: PublicReportDraft, evidenceCount: Int, cannotProvideEvidence: Boolean, guidelinesAccepted: Boolean): String? =
        title(draft.title)
            ?: description(draft.description)
            ?: urgency(draft.urgency)
            ?: category(draft.categoryId)
            ?: identity(draft.identityMode)
            ?: publicLocation(draft.publicLocationLabel)
            ?: (if (draft.locationMode == PublicReportLocationMode.MANUAL) manualAddress(draft.exactAddress) else null)
            ?: evidenceOrException(evidenceCount, cannotProvideEvidence, draft.noEvidenceReason)
            ?: guidelinesAccepted(guidelinesAccepted, draft.guidelinesVersion)
}
