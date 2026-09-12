package za.org.rtc.community.feature.administration.security

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

class AdminSecurityException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

/**
 * Client-side defense in depth for administrator-only surfaces.
 *
 * This guard intentionally trusts only Supabase app_metadata because that namespace is
 * server-managed. Editable user_metadata, email naming conventions, and locally cached flags
 * are never authorization signals. Every protected backend operation must still enforce its
 * own RLS/RPC authorization because a client-side guard is not a security boundary.
 */
internal fun hasServerAdminClaim(appMetadata: JsonObject?): Boolean {
    if (appMetadata == null) return false

    val isAdmin = appMetadata["is_admin"]?.let { element ->
        element.jsonPrimitive.booleanOrNull
            ?: element.jsonPrimitive.contentOrNull?.equals("true", ignoreCase = true)
    } ?: false

    val role = appMetadata["role"]?.jsonPrimitive?.contentOrNull
        ?.trim()
        ?.uppercase()

    return isAdmin || role in setOf("ADMIN", "SYSTEM_ADMIN", "ROLE_ADMINISTRATION")
}

@Singleton
class AdminGuard @Inject constructor(
    private val supabase: SupabaseClient,
) {
    companion object {
        private const val TAG = "AdminGuard"
    }

    suspend fun isAdmin(): Boolean {
        val user = supabase.auth.currentUserOrNull()
        val authorized = user != null && hasServerAdminClaim(user.appMetadata)
        if (authorized) {
            Log.d(TAG, "AdminGuard authorized user ${user?.id} from server-managed app_metadata")
        } else {
            Log.w(TAG, "Admin check denied for user=${user?.id ?: "none"}: missing server-managed admin claim")
        }
        return authorized
    }

    suspend fun requireAdmin(): Result<Unit> {
        return if (isAdmin()) {
            Result.success(Unit)
        } else {
            val userId = supabase.auth.currentUserOrNull()?.id ?: "unauthenticated"
            Result.failure(
                AdminSecurityException(
                    "Administrative access denied for user ($userId). A server-managed administrator role is required.",
                ),
            )
        }
    }

    suspend fun <T> runAdminGuarded(
        actionName: String = "admin_action",
        block: suspend () -> T,
    ): Result<T> {
        val authResult = requireAdmin()
        if (authResult.isFailure) {
            val exception = authResult.exceptionOrNull() as? AdminSecurityException
                ?: AdminSecurityException("Unauthorized access to administrative operation: $actionName")
            Log.e(TAG, "Access denied for operation: $actionName - ${exception.message}")
            return Result.failure(exception)
        }

        return runCatching {
            Log.d(TAG, "Executing guarded administrative action: $actionName")
            block()
        }.onFailure { error ->
            Log.e(TAG, "Administrative action failed: $actionName", error)
        }
    }
}
