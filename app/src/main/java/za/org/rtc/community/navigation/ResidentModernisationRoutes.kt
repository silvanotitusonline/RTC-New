package za.org.rtc.community.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class CommunitySection(val wireValue: String) {
    DISCUSSIONS("discussions"),
    REPORTS("reports"),
}

enum class ReportStatusBucket(val wireValue: String) {
    ALL("all"),
    ACTIVE("active"),
    COMPLETED("completed"),
    INACTIVE("inactive"),
}

enum class ReportScope(val wireValue: String) {
    VERIFIED("verified"),
    ACTIVE("active"),
    RESOLVED("resolved"),
    UNRESOLVED("unresolved"),
}

enum class ReportUrgency(val wireValue: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    CRITICAL("critical"),
}

enum class PublicReportSort(val wireValue: String) {
    LATEST("latest"),
    OLDEST("oldest"),
    HOT("hot"),
    HIGHEST_URGENCY("highest_urgency"),
    MOST_DISCUSSED("most_discussed"),
    MOST_SUPPORTED("most_supported"),
}

enum class InboxTab(val wireValue: String) {
    UPDATES("updates"),
    MESSAGES("messages"),
}

data class CommunityRouteOptions(
    val section: CommunitySection = CommunitySection.DISCUSSIONS,
    val focusPostId: UUID? = null,
    val scope: ReportScope = ReportScope.VERIFIED,
    @Deprecated("Use verified-only ReportScope")
    val bucket: ReportStatusBucket = ReportStatusBucket.ALL,
    val urgency: ReportUrgency? = null,
    @Deprecated("Resident Public Reports are always verified")
    val verified: Boolean? = null,
    val categorySlug: String? = null,
    val sort: PublicReportSort = PublicReportSort.LATEST,
)

sealed interface ResidentModernisationDestination {
    data class Community(val options: CommunityRouteOptions) : ResidentModernisationDestination
    data object PublicReportComposer : ResidentModernisationDestination
    data class PublicReportDetail(val reportId: UUID) : ResidentModernisationDestination
    data object Events : ResidentModernisationDestination
    data class Inbox(val tab: InboxTab) : ResidentModernisationDestination
    data object Services : ResidentModernisationDestination
    data object Account : ResidentModernisationDestination
    data object AccountSettings : ResidentModernisationDestination
    data object AccountSupport : ResidentModernisationDestination
    data object AccountProvider : ResidentModernisationDestination
}

/**
 * Stable route contract for the resident-modernisation rollout.
 *
 * This object intentionally has no Compose or feature-module dependency. Screens consume
 * typed destinations; central navigation remains the sole place that turns a destination
 * into a NavController operation.
 */
object ResidentModernisationRoutes {
    @Suppress("DEPRECATION")
    fun community(options: CommunityRouteOptions = CommunityRouteOptions()): String = buildString {
        append("community?section=").append(options.section.wireValue)
        when (options.section) {
            CommunitySection.DISCUSSIONS -> {
                options.focusPostId?.let { append("&focusPostId=").append(it) }
            }
            CommunitySection.REPORTS -> {
                append("&scope=").append(options.scope.wireValue)
                append("&bucket=").append(options.bucket.wireValue)
                options.urgency?.let { append("&urgency=").append(it.wireValue) }
                options.verified?.let { append("&verified=").append(it) }
                options.categorySlug?.let { append("&category=").append(encode(it)) }
                append("&sort=").append(options.sort.wireValue)
            }
        }
    }

    fun publicReportComposer(): String = "public-report/new"

    fun publicReportDetail(reportId: UUID): String = "public-report/$reportId"

    fun events(): String = "events"

    fun inbox(tab: InboxTab = InboxTab.UPDATES): String = "inbox?tab=${tab.wireValue}"

    fun services(): String = "services"

    fun account(): String = "account"

    fun accountSettings(): String = "account/settings"

    fun accountSupport(): String = "account/support"

    fun accountProvider(): String = "account/provider"

    fun parse(route: String?): ResidentModernisationDestination? {
        val normalized = route?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val path = normalized.substringBefore('?')
        val parameters = parseQuery(normalized.substringAfter('?', ""))

        return when (path) {
            "community" -> {
                val section = enumByWire(parameters["section"], CommunitySection.entries) { it.wireValue }
                    ?: CommunitySection.DISCUSSIONS
                val options = when (section) {
                    CommunitySection.DISCUSSIONS -> CommunityRouteOptions(
                        section = section,
                        focusPostId = parameters["focusPostId"].toUuidOrNull(),
                    )
                    CommunitySection.REPORTS -> {
                        val legacyBucket = enumByWire(
                            parameters["bucket"],
                            ReportStatusBucket.entries,
                        ) { it.wireValue } ?: ReportStatusBucket.ALL
                        val scope = if (parameters.containsKey("scope")) {
                            enumByWire(parameters["scope"], ReportScope.entries) { it.wireValue }
                                ?: ReportScope.VERIFIED
                        } else {
                            legacyBucket.toVerifiedScope()
                        }
                        CommunityRouteOptions(
                            section = section,
                            scope = scope,
                            bucket = legacyBucket,
                            urgency = enumByWire(parameters["urgency"], ReportUrgency.entries) { it.wireValue },
                            verified = parameters["verified"].toBooleanOrNull(),
                            categorySlug = parameters["category"].toCategorySlugOrNull(),
                            sort = enumByWire(parameters["sort"], PublicReportSort.entries) { it.wireValue }
                                ?: PublicReportSort.LATEST,
                        )
                    }
                }
                ResidentModernisationDestination.Community(options)
            }
            "public-report/new" -> ResidentModernisationDestination.PublicReportComposer
            "events" -> ResidentModernisationDestination.Events
            "inbox" -> ResidentModernisationDestination.Inbox(
                enumByWire(parameters["tab"], InboxTab.entries) { it.wireValue } ?: InboxTab.UPDATES,
            )
            "services" -> ResidentModernisationDestination.Services
            "account" -> ResidentModernisationDestination.Account
            "account/settings" -> ResidentModernisationDestination.AccountSettings
            "account/support" -> ResidentModernisationDestination.AccountSupport
            "account/provider" -> ResidentModernisationDestination.AccountProvider
            else -> path.removePrefix("public-report/")
                .toUuidOrNull()
                ?.takeIf { path.startsWith("public-report/") }
                ?.let(ResidentModernisationDestination::PublicReportDetail)
        }
    }

    private fun parseQuery(rawQuery: String): Map<String, String> =
        rawQuery
            .split('&')
            .asSequence()
            .mapNotNull { part ->
                val delimiter = part.indexOf('=')
                if (delimiter <= 0) {
                    null
                } else {
                    val key = decodeOrNull(part.substring(0, delimiter)) ?: return@mapNotNull null
                    val value = decodeOrNull(part.substring(delimiter + 1)) ?: return@mapNotNull null
                    key to value
                }
            }
            .toMap()

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun decodeOrNull(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
    }.getOrNull()

    private fun <T> enumByWire(
        value: String?,
        candidates: Iterable<T>,
        wireValue: (T) -> String,
    ): T? = candidates.firstOrNull { wireValue(it) == value }
}

private fun ReportStatusBucket.toVerifiedScope(): ReportScope = when (this) {
    ReportStatusBucket.ALL -> ReportScope.VERIFIED
    ReportStatusBucket.ACTIVE -> ReportScope.ACTIVE
    ReportStatusBucket.COMPLETED -> ReportScope.RESOLVED
    ReportStatusBucket.INACTIVE -> ReportScope.UNRESOLVED
}

private fun String?.toUuidOrNull(): UUID? = runCatching {
    this?.let(UUID::fromString)
}.getOrNull()

private fun String?.toCategorySlugOrNull(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) }

private fun String?.toBooleanOrNull(): Boolean? = when (this?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}
