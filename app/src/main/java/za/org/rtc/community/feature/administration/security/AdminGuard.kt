package za.org.rtc.community.feature.administration.security

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

/** Security exception thrown when administrative access is denied. */
class AdminSecurityException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

/**
 * Client-side administrative gate backed by the server-owned authorization function.
 *
 * No email naming convention, user-editable metadata, cached DataStore value, or local role is
 * accepted as administrative proof. `public.admin_access_guard()` resolves the authenticated
 * actor from auth.uid(), checks `public.user_roles`, and requires the server MFA/AAL2 policy.
 * Database RLS/RPC authorization remains the final enforcement boundary; this guard exists to
 * keep the Android control plane consistent with that same authoritative decision.
 */
@Singleton
class AdminGuard @Inject constructor(
    private val supabase: SupabaseClient,
) {
    companion object {
        private const val TAG = "AdminGuard"
    }

    suspend fun isAdmin(): Boolean {
        if (supabase.auth.currentUserOrNull() == null) {
            Log.w(TAG, "Admin check denied: no authenticated Supabase user")
            return false
        }

        return runCatching {
            // A successful invocation is the authorization proof. The returned UUID is not used
            // by the client because identity continues to come from the authenticated session.
            supabase.postgrest.rpc("admin_access_guard")
            true
        }.onFailure { error ->
            Log.w(TAG, "Server-authoritative admin check denied", error)
        }.getOrDefault(false)
    }

    suspend fun requireAdmin(): Result<Unit> =
        if (isAdmin()) {
            Result.success(Unit)
        } else {
            Result.failure(
                AdminSecurityException(
                    "Administrative access denied. A current privileged session with verified MFA is required.",
                ),
            )
        }

    suspend fun <T> runAdminGuarded(
        actionName: String = "admin_action",
        block: suspend () -> T,
    ): Result<T> {
        val authResult = requireAdmin()
        if (authResult.isFailure) {
            val exception = authResult.exceptionOrNull() as? AdminSecurityException
                ?: AdminSecurityException("Unauthorized administrative operation: $actionName")
            Log.e(TAG, "Access denied for operation: $actionName")
            return Result.failure(exception)
        }

        return runCatching { block() }
            .onFailure { error -> Log.e(TAG, "Administrative action failed: $actionName", error) }
    }
}
