import re

with open("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt", "r") as f:
    content = f.read()

import_statement = "import kotlinx.coroutines.async\nimport kotlinx.coroutines.coroutineScope\n"
import_idx = content.find("import kotlinx.coroutines.flow.MutableStateFlow")
content = content[:import_idx] + import_statement + content[import_idx:]

original_refresh = """        val metrics = productionUxRepository.getDashboardMetrics()
        val projects = productionUxRepository.listProjects(offset = 0)
        val centres = productionUxRepository.listCentres(offset = 0)
        val opportunities = productionUxRepository.listOpportunities(offset = 0)
        val notices = productionUxRepository.publishedOfficialNotices()
        val helpArticles = productionUxRepository.publishedHelpArticles()

        // Community views deliberately grant SELECT only to authenticated users. Public startup
        // must not convert that policy boundary into a misleading empty/error state.
        val communityPosts = runCatching {
            productionUxRepository.publishedCommunityPosts().getOrThrow()
        }.getOrElse { emptyList() }

        val alerts = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.communityAlertInbox()
        } else {
            Result.success(emptyList())
        }

        val supportCases = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.mySupportCases()
        } else Result.success(emptyList())

        val alertDashboard = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
            && _session.value.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)
        ) productionUxRepository.communityAlertDashboard() else Result.success(emptyList())

        val assignedSupportCases = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
            && _session.value.role == UserRole.CASE_STAFF
        ) productionUxRepository.assignedSupportCases() else Result.success(emptyList())"""


new_refresh = """        val (metrics, projects, centres, opportunities, notices, helpArticles, communityPosts, alerts, supportCases, alertDashboard, assignedSupportCases) = coroutineScope {
            val m = async { productionUxRepository.getDashboardMetrics() }
            val p = async { productionUxRepository.listProjects(offset = 0) }
            val c = async { productionUxRepository.listCentres(offset = 0) }
            val o = async { productionUxRepository.listOpportunities(offset = 0) }
            val n = async { productionUxRepository.publishedOfficialNotices() }
            val ha = async { productionUxRepository.publishedHelpArticles() }

            val cp = async {
                runCatching {
                    productionUxRepository.publishedCommunityPosts().getOrThrow()
                }.getOrElse { emptyList() }
            }

            val al = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                    productionUxRepository.communityAlertInbox()
                } else {
                    Result.success(emptyList())
                }
            }

            val sc = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                    productionUxRepository.mySupportCases()
                } else Result.success(emptyList())
            }

            val ad = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
                    && _session.value.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)
                ) productionUxRepository.communityAlertDashboard() else Result.success(emptyList())
            }
            
            val asc = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
                    && _session.value.role == UserRole.CASE_STAFF
                ) productionUxRepository.assignedSupportCases() else Result.success(emptyList())
            }

            listOf(m.await(), p.await(), c.await(), o.await(), n.await(), ha.await(), cp.await(), al.await(), sc.await(), ad.await(), asc.await())
        }"""

# Actually, Kotlin listOf returns List<Any?> so destructuring doesn't work out of the box for more than 5 elements typically or it loses types. Let's do it safely without listOf.

new_refresh_safe = """        val metricsJob = coroutineScope { async { productionUxRepository.getDashboardMetrics() } }
        val projectsJob = coroutineScope { async { productionUxRepository.listProjects(offset = 0) } }
        val centresJob = coroutineScope { async { productionUxRepository.listCentres(offset = 0) } }
        val opportunitiesJob = coroutineScope { async { productionUxRepository.listOpportunities(offset = 0) } }
        val noticesJob = coroutineScope { async { productionUxRepository.publishedOfficialNotices() } }
        val helpArticlesJob = coroutineScope { async { productionUxRepository.publishedHelpArticles() } }

        val communityPostsJob = coroutineScope { async {
            runCatching {
                productionUxRepository.publishedCommunityPosts().getOrThrow()
            }.getOrElse { emptyList() }
        } }

        val alertsJob = coroutineScope { async {
            if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                productionUxRepository.communityAlertInbox()
            } else {
                Result.success(emptyList())
            }
        } }

        val supportCasesJob = coroutineScope { async {
            if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                productionUxRepository.mySupportCases()
            } else Result.success(emptyList())
        } }

        val alertDashboardJob = coroutineScope { async {
            if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
                && _session.value.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)
            ) productionUxRepository.communityAlertDashboard() else Result.success(emptyList())
        } }
        
        val assignedSupportCasesJob = coroutineScope { async {
            if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
                && _session.value.role == UserRole.CASE_STAFF
            ) productionUxRepository.assignedSupportCases() else Result.success(emptyList())
        } }

        val metrics = metricsJob.await()
        val projects = projectsJob.await()
        val centres = centresJob.await()
        val opportunities = opportunitiesJob.await()
        val notices = noticesJob.await()
        val helpArticles = helpArticlesJob.await()
        val communityPosts = communityPostsJob.await()
        val alerts = alertsJob.await()
        val supportCases = supportCasesJob.await()
        val alertDashboard = alertDashboardJob.await()
        val assignedSupportCases = assignedSupportCasesJob.await()"""

# Actually doing `coroutineScope { async { } }` sequentially waits for each coroutine to finish!
# The right way is to wrap all of them in a single `coroutineScope` and just call async.

new_refresh_correct = """        var metrics: Result<za.org.rtc.community.core.DashboardMetrics>? = null
        var projects: Result<List<za.org.rtc.community.core.MunicipalProject>>? = null
        var centres: Result<List<za.org.rtc.community.core.MunicipalServiceCentre>>? = null
        var opportunities: Result<List<za.org.rtc.community.core.CommunityOpportunity>>? = null
        var notices: Result<List<za.org.rtc.community.core.OfficialNotice>>? = null
        var helpArticles: Result<List<za.org.rtc.community.core.HelpArticle>>? = null
        var communityPosts: List<za.org.rtc.community.core.CommunityPost>? = null
        var alerts: Result<List<za.org.rtc.community.core.CommunityAlert>>? = null
        var supportCases: Result<List<za.org.rtc.community.core.SupportCase>>? = null
        var alertDashboard: Result<List<za.org.rtc.community.core.CommunityAlert>>? = null
        var assignedSupportCases: Result<List<za.org.rtc.community.core.SupportCase>>? = null

        coroutineScope {
            val m = async { productionUxRepository.getDashboardMetrics() }
            val p = async { productionUxRepository.listProjects(offset = 0) }
            val c = async { productionUxRepository.listCentres(offset = 0) }
            val o = async { productionUxRepository.listOpportunities(offset = 0) }
            val n = async { productionUxRepository.publishedOfficialNotices() }
            val ha = async { productionUxRepository.publishedHelpArticles() }

            val cp = async {
                runCatching {
                    productionUxRepository.publishedCommunityPosts().getOrThrow()
                }.getOrElse { emptyList() }
            }

            val al = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                    productionUxRepository.communityAlertInbox()
                } else {
                    Result.success(emptyList())
                }
            }

            val sc = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                    productionUxRepository.mySupportCases()
                } else Result.success(emptyList())
            }

            val ad = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
                    && _session.value.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)
                ) productionUxRepository.communityAlertDashboard() else Result.success(emptyList())
            }
            
            val asc = async {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
                    && _session.value.role == UserRole.CASE_STAFF
                ) productionUxRepository.assignedSupportCases() else Result.success(emptyList())
            }

            metrics = m.await()
            projects = p.await()
            centres = c.await()
            opportunities = o.await()
            notices = n.await()
            helpArticles = ha.await()
            communityPosts = cp.await()
            alerts = al.await()
            supportCases = sc.await()
            alertDashboard = ad.await()
            assignedSupportCases = asc.await()
        }"""

content = content.replace(original_refresh, new_refresh_correct)

content = content.replace("metrics.isFailure ||", "metrics!!.isFailure ||")
content = content.replace("metrics.getOrNull()", "metrics!!.getOrNull()")
content = content.replace("projects.getOrNull()", "projects!!.getOrNull()")
content = content.replace("centres.getOrNull()", "centres!!.getOrNull()")
content = content.replace("opportunities.getOrNull()", "opportunities!!.getOrNull()")
content = content.replace("notices.getOrNull()", "notices!!.getOrNull()")
content = content.replace("helpArticles.getOrNull()", "helpArticles!!.getOrNull()")
content = content.replace("communityPosts,", "communityPosts!!,")
content = content.replace("alerts.getOrNull()", "alerts!!.getOrNull()")
content = content.replace("supportCases.getOrNull()", "supportCases!!.getOrNull()")
content = content.replace("alertDashboard.getOrNull()", "alertDashboard!!.getOrNull()")
content = content.replace("assignedSupportCases.getOrNull()", "assignedSupportCases!!.getOrNull()")

with open("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt", "w") as f:
    f.write(content)
