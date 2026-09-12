package za.org.rtc.community.ui.navigation

/**
 * Guards against malformed deep links reaching a screen that expects a real id.
 *
 * A link such as rtc://community_post/null or one missing its argument entirely
 * would otherwise be handed straight to a repository call and surface as an
 * empty or crashing detail screen.
 */
object RouteParamValidation {

    private val RESERVED_PLACEHOLDERS = setOf("null", "undefined", "none", "0")

    /** True when [value] is present, non-blank and not a stringified null. */
    fun isPresent(value: String?): Boolean {
        val trimmed = value?.trim() ?: return false
        if (trimmed.isEmpty()) return false
        return !RESERVED_PLACEHOLDERS.contains(trimmed.lowercase())
    }

    /**
     * True only when every named parameter carries a usable value.
     * Returns false on the first missing entry.
     */
    fun validateRouteParams(params: Map<String, String?>, required: List<String>): Boolean {
        for (key in required) {
            if (!isPresent(params[key])) return false
        }
        return true
    }

    /** Extracts a validated id, or null when the argument cannot be trusted. */
    fun validatedId(value: String?): String? = if (isPresent(value)) value!!.trim() else null
}
