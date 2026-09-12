package za.org.rtc.community.feature.publicreports.data

object PublicReportRpcContract {
    const val CREATE = "civic_report_create_v1"
    const val PAGE = "civic_report_page_v1"
    const val VERIFIED_PAGE = "civic_report_page_v2"
    const val GET = "civic_report_get_v1"
    const val SET_VOTE = "civic_report_set_vote_v1"
    const val COMMENT_PAGE = "civic_report_comment_page_v1"
    const val ADD_COMMENT = "civic_report_add_comment_v1"
    const val FINALIZE_EVIDENCE = "civic_report_finalize_evidence_v1"
    const val DASHBOARD = "civic_report_dashboard_v1"
    const val VERIFIED_DASHBOARD = "civic_report_dashboard_v2"
    const val MY_PAGE = "civic_report_my_page_v1"
    const val OWNER_PRIVATE = "civic_report_owner_private_details_v1"
    const val ADMIN_PAGE = "admin_civic_report_page_v1"
    const val ADMIN_VERIFY = "admin_civic_report_set_verification_v1"
    const val ADMIN_TRANSITION = "admin_civic_report_transition_v1"
    const val WITHDRAW = "civic_report_withdraw_v1"
    const val CATEGORIES = "civic_report_categories_v1"
    const val TIMELINE = "civic_report_timeline_v1"
    const val EVIDENCE_PUBLIC = "civic_report_evidence_public_v1"
    const val EVIDENCE_MEDIA_URL = "civic-report-media-url"
    const val EVIDENCE_BUCKET = "civic-report-evidence"
    const val EVIDENCE_PATH_SHAPE = "<owner_id>/<report_client_request_id>/<object>"
    const val PAGE_MAX = 50
    val CREATE_PARAMS = listOf(
        "p_client_request_id", "p_title", "p_description", "p_started_at", "p_category_id",
        "p_urgency", "p_identity_mode", "p_location_mode", "p_public_location_label",
        "p_latitude", "p_longitude", "p_exact_address", "p_no_evidence_reason",
        "p_contact_permission", "p_guidelines_version",
    )
    val PAGE_PARAMS = listOf(
        "p_status", "p_urgency", "p_category_slug", "p_verified", "p_sort",
        "p_cursor_created_at", "p_cursor_id", "p_limit", "p_evaluated_at",
    )
    val VERIFIED_PAGE_PARAMS = listOf(
        "p_scope", "p_urgency", "p_category_slug", "p_sort",
        "p_cursor_created_at", "p_cursor_id", "p_limit", "p_evaluated_at",
    )
    val VOTE_VALUES = listOf(-1, 0, 1)
    val STAFF_ROLES_PAGE = listOf("CASE_STAFF", "EVIDENCE_REVIEWER", "SYSTEM_ADMIN")
    val STAFF_ROLES_VERIFY = listOf("EVIDENCE_REVIEWER", "SYSTEM_ADMIN")
}
