package za.org.rtc.community.app

/**
 * Maps implementation/backend failures to bounded user-facing copy. Raw SQL, RPC, storage,
 * provider, or transport details stay out of resident UI surfaces.
 */
internal object SafeUiError {
    fun community(error: Throwable, fallback: String): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("ANONYMOUS_READ_ONLY", ignoreCase = true) ->
                "RTC no longer requires resident accounts. This Community action is read-only until its anonymous server safety contract is enabled."
            detail.contains("permission", ignoreCase = true) ||
                detail.contains("not authorized", ignoreCase = true) ||
                detail.contains("forbidden", ignoreCase = true) ->
                "You do not have access to complete this Community action."
            detail.contains("guideline", ignoreCase = true) ->
                "Please accept the current Community Guidelines and try again."
            detail.contains("timeout", ignoreCase = true) || detail.contains("timed out", ignoreCase = true) ->
                "The request took too long. Check your connection and try again."
            else -> fallback
        }
    }

    fun serviceCentre(error: Throwable, fallback: String): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("auth_required", ignoreCase = true) ||
                detail.contains("unauthenticated", ignoreCase = true) ||
                detail.contains("jwt", ignoreCase = true) && detail.contains("expired", ignoreCase = true) ->
                "Please sign in again to continue."
            detail.contains("permission", ignoreCase = true) ||
                detail.contains("not authorized", ignoreCase = true) ||
                detail.contains("forbidden", ignoreCase = true) ->
                "You do not have access to complete this Service Centre action."
            detail.contains("not found", ignoreCase = true) ->
                "This Service Centre item is no longer available. Refresh and try again."
            detail.contains("invalid state", ignoreCase = true) ||
                detail.contains("state transition", ignoreCase = true) ||
                detail.contains("already accepted", ignoreCase = true) ||
                detail.contains("already declined", ignoreCase = true) ||
                detail.contains("already cancelled", ignoreCase = true) ||
                detail.contains("already completed", ignoreCase = true) ->
                "This booking has changed. Refresh to see the latest status."
            detail.contains("timeout", ignoreCase = true) || detail.contains("timed out", ignoreCase = true) ->
                "The request took too long. Check your connection and try again."
            detail.contains("network", ignoreCase = true) ||
                detail.contains("connect", ignoreCase = true) ||
                detail.contains("unreachable", ignoreCase = true) ->
                "Service Centre could not be reached. Check your connection and try again."
            else -> fallback
        }
    }

    fun marketplace(
        error: Throwable,
        fallback: String = "Marketplace action could not be completed. Please try again.",
    ): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("auth_required", ignoreCase = true) ||
                detail.contains("unauthenticated", ignoreCase = true) ||
                detail.contains("jwt", ignoreCase = true) && detail.contains("expired", ignoreCase = true) ->
                "Please sign in again to continue."
            detail.contains("permission", ignoreCase = true) ||
                detail.contains("not authorized", ignoreCase = true) ||
                detail.contains("forbidden", ignoreCase = true) ->
                "You do not have access to complete this Marketplace action."
            detail.contains("not found", ignoreCase = true) ->
                "This Marketplace item is no longer available. Refresh and try again."
            detail.contains("validation", ignoreCase = true) || detail.contains("invalid", ignoreCase = true) ->
                "Check the Marketplace information and try again."
            detail.contains("timeout", ignoreCase = true) || detail.contains("timed out", ignoreCase = true) ->
                "The request took too long. Check your connection and try again."
            detail.contains("network", ignoreCase = true) ||
                detail.contains("connect", ignoreCase = true) ||
                detail.contains("unreachable", ignoreCase = true) ->
                "Marketplace could not be reached. Check your connection and try again."
            else -> fallback
        }
    }

    fun profilePhoto(error: Throwable): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("timed out", ignoreCase = true) || detail.contains("timeout", ignoreCase = true) ->
                "Profile photo upload took too long. Check your connection and try again."
            detail.contains("read", ignoreCase = true) ||
                detail.contains("decode", ignoreCase = true) ||
                detail.contains("prepare", ignoreCase = true) ->
                "This image could not be prepared. Choose a standard JPG or PNG image and try again."
            detail.contains("5 MB", ignoreCase = true) || detail.contains("size", ignoreCase = true) ->
                "Choose an image that can be prepared below 5 MB."
            else -> "Profile photo could not be uploaded. Check your connection and try again."
        }
    }

    fun generic(error: Throwable, fallback: String): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("Invalid login credentials", ignoreCase = true) -> "Invalid login credentials. Please check your email and password."
            detail.contains("Email not confirmed", ignoreCase = true) -> "Please confirm your email address before signing in."
            detail.contains("permission", ignoreCase = true) ||
                detail.contains("not authorized", ignoreCase = true) ||
                detail.contains("forbidden", ignoreCase = true) -> "You do not have access to complete this action."
            detail.contains("timeout", ignoreCase = true) || detail.contains("timed out", ignoreCase = true) ->
                "The request took too long. Check your connection and try again."
            detail.contains("network", ignoreCase = true) || detail.contains("connect", ignoreCase = true) ->
                "The service could not be reached. Check your connection and try again."
            else -> "$fallback (${error.javaClass.simpleName}: $detail)"
        }
    }
}
