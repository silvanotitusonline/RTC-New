package za.org.rtc.community.feature.publicreports.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.publicreports.domain.PublicReportDraft

/** PostgreSQL's nullable parameters are required arguments; omission selects no RPC overload. */
internal fun publicReportCreateParameters(draft: PublicReportDraft) = buildJsonObject {
    put("p_client_request_id", draft.clientRequestId)
    put("p_title", draft.title.trim())
    put("p_description", draft.description.trim())
    put("p_started_at", draft.startedAt?.toString())
    put("p_category_id", draft.categoryId)
    put("p_urgency", draft.urgency.name)
    put("p_identity_mode", draft.identityMode.name)
    put("p_location_mode", draft.locationMode.name)
    put("p_public_location_label", draft.publicLocationLabel.trim())
    put("p_latitude", draft.latitude)
    put("p_longitude", draft.longitude)
    put("p_exact_address", draft.exactAddress?.trim()?.takeIf { it.isNotEmpty() })
    put("p_no_evidence_reason", draft.noEvidenceReason?.trim()?.takeIf { it.isNotEmpty() })
    put("p_contact_permission", draft.contactPermission)
    put("p_guidelines_version", draft.guidelinesVersion)
}
