package za.org.rtc.community.feature.publicreports.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicReportRpcContractTest {
    @Test
    fun createUsesExactMergedParameterNames() {
        assertEquals("civic_report_create_v1", PublicReportRpcContract.CREATE)
        assertTrue(PublicReportRpcContract.CREATE_PARAMS.contains("p_client_request_id"))
        assertTrue(PublicReportRpcContract.CREATE_PARAMS.contains("p_guidelines_version"))
        assertEquals(15, PublicReportRpcContract.CREATE_PARAMS.size)
    }

    @Test
    fun pageUsesServerOwnedBoundedCursors() {
        assertEquals("civic_report_page_v1", PublicReportRpcContract.PAGE)
        assertTrue(PublicReportRpcContract.PAGE_PARAMS.contains("p_cursor_created_at"))
        assertTrue(PublicReportRpcContract.PAGE_PARAMS.contains("p_cursor_id"))
        assertEquals(50, PublicReportRpcContract.PAGE_MAX)
    }

    @Test
    fun votesAreMutuallyExclusiveIntegers() {
        assertEquals(listOf(-1, 0, 1), PublicReportRpcContract.VOTE_VALUES)
        assertEquals("civic_report_set_vote_v1", PublicReportRpcContract.SET_VOTE)
    }

    @Test
    fun evidenceUsesOwnerScopedBucketAndPath() {
        assertEquals("civic-report-evidence", PublicReportRpcContract.EVIDENCE_BUCKET)
        assertTrue(PublicReportRpcContract.EVIDENCE_PATH_SHAPE.contains("report_client_request_id"))
        assertEquals("civic_report_finalize_evidence_v1", PublicReportRpcContract.FINALIZE_EVIDENCE)
    }

    @Test
    fun administratorRpcsAreRoleGatedByBackend() {
        assertEquals(listOf("CASE_STAFF", "EVIDENCE_REVIEWER", "SYSTEM_ADMIN"), PublicReportRpcContract.STAFF_ROLES_PAGE)
        assertEquals(listOf("EVIDENCE_REVIEWER", "SYSTEM_ADMIN"), PublicReportRpcContract.STAFF_ROLES_VERIFY)
    }
}
