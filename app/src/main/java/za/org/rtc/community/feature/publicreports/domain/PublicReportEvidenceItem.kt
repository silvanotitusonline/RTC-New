package za.org.rtc.community.feature.publicreports.domain

data class PublicReportEvidenceItem(
    val id: String,
    val reportId: String,
    val mediaKind: PublicReportMediaKind,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
    val position: Int,
    val signedUrl: String? = null,
)
