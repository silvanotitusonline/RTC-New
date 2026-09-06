@file:Suppress("DEPRECATION")

package za.org.rtc.community.navigation

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ResidentModernisationRoutesTest {
    @Test
    fun `community report route round trips typed filters`() {
        val route = ResidentModernisationRoutes.community(
            CommunityRouteOptions(
                section = CommunitySection.REPORTS,
                bucket = ReportStatusBucket.ACTIVE,
                urgency = ReportUrgency.CRITICAL,
                verified = true,
                categorySlug = "water-sanitation",
                sort = PublicReportSort.HOT,
            ),
        )

        assertEquals(
            CommunityRouteOptions(
                section = CommunitySection.REPORTS,
                bucket = ReportStatusBucket.ACTIVE,
                urgency = ReportUrgency.CRITICAL,
                verified = true,
                categorySlug = "water-sanitation",
                sort = PublicReportSort.HOT,
            ),
            (ResidentModernisationRoutes.parse(route) as ResidentModernisationDestination.Community).options,
        )
    }

    @Test
    fun `unknown community enum values fall back to safe defaults`() {
        val destination = ResidentModernisationRoutes.parse(
            "community?section=unknown&bucket=unknown&urgency=unknown&verified=not-a-boolean&sort=unknown",
        ) as ResidentModernisationDestination.Community

        assertEquals(CommunitySection.DISCUSSIONS, destination.options.section)
        assertEquals(ReportStatusBucket.ALL, destination.options.bucket)
        assertEquals(PublicReportSort.LATEST, destination.options.sort)
        assertNull(destination.options.urgency)
        assertNull(destination.options.verified)
    }

    @Test
    fun `malformed public report identifiers are rejected`() {
        assertNull(ResidentModernisationRoutes.parse("public-report/not-a-uuid"))
    }

    @Test
    fun `public report detail route preserves a valid identifier`() {
        val reportId = UUID.fromString("22bb9e2e-0d5f-4c96-a5ad-bd20f0b5e4e1")

        assertEquals(
            ResidentModernisationDestination.PublicReportDetail(reportId),
            ResidentModernisationRoutes.parse(ResidentModernisationRoutes.publicReportDetail(reportId)),
        )
    }

    @Test
    fun `feature routes are centrally built and parsed`() {
        assertEquals(
            ResidentModernisationDestination.Inbox(InboxTab.MESSAGES),
            ResidentModernisationRoutes.parse(ResidentModernisationRoutes.inbox(InboxTab.MESSAGES)),
        )
        assertEquals(ResidentModernisationDestination.Events, ResidentModernisationRoutes.parse("events"))
        assertEquals(ResidentModernisationDestination.Services, ResidentModernisationRoutes.parse("services"))
        assertEquals(ResidentModernisationDestination.AccountSettings, ResidentModernisationRoutes.parse("account/settings"))
    }

    @Test
    fun `malformed encoded community query value is ignored without throwing`() {
        val destination = ResidentModernisationRoutes.parse(
            "community?section=reports&category=%ZZ&bucket=active",
        ) as ResidentModernisationDestination.Community

        assertEquals(CommunitySection.REPORTS, destination.options.section)
        assertEquals(ReportStatusBucket.ACTIVE, destination.options.bucket)
        assertNull(destination.options.categorySlug)
    }

    @Test
    fun `community route keeps discussion and report parameters isolated`() {
        val focusPostId = UUID.fromString("6d874976-503c-4a80-a254-17781e81e24d")
        val reportRoute = ResidentModernisationRoutes.community(
            CommunityRouteOptions(
                section = CommunitySection.REPORTS,
                focusPostId = focusPostId,
                bucket = ReportStatusBucket.ACTIVE,
            ),
        )
        assertFalse(reportRoute.contains("focusPostId="))

        val discussion = ResidentModernisationRoutes.parse(
            "community?section=discussions&bucket=active&urgency=critical&verified=true&category=water&sort=hot",
        ) as ResidentModernisationDestination.Community
        assertEquals(CommunitySection.DISCUSSIONS, discussion.options.section)
        assertEquals(ReportStatusBucket.ALL, discussion.options.bucket)
        assertNull(discussion.options.urgency)
        assertNull(discussion.options.verified)
        assertNull(discussion.options.categorySlug)
        assertEquals(PublicReportSort.LATEST, discussion.options.sort)
    }
}
