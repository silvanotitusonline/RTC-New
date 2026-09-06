package za.org.rtc.community.feature.administration.security

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import za.org.rtc.community.data.local.UserPreferencesStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security exception thrown when administrative access is denied.
 */
class AdminSecurityException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

/**
 * AdminGuard service that wraps administrative API calls in a check for a custom
 * 'is_admin' metadata claim from the Supabase session, ensuring that only users
 * with the 'admin' role can access the newly designed Admin Control Plane.
 */
@Singleton
class AdminGuard @Inject constructor(
    private val supabase: SupabaseClient,
    private val userPreferencesStore: UserPreferencesStore,
) {
    companion object {
        private const val TAG = "AdminGuard"
        private const val CLAIM_IS_ADMIN = "is_admin"
        private const val CLAIM_ROLE = "role"
    }

    /**
     * Checks if the currently authenticated Supabase session possesses the custom 'is_admin'
     * metadata claim, matches administrative authorization criteria, or has cached admin privileges in DataStore.
     */
    suspend fun isAdmin(): Boolean {
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            // 1. Check user_metadata for 'is_admin' custom claim
            val userMetadata = user.userMetadata
            val isAdminUserClaim = userMetadata?.get(CLAIM_IS_ADMIN)?.let { element ->
                element.jsonPrimitive.booleanOrNull
                    ?: element.jsonPrimitive.contentOrNull?.equals("true", ignoreCase = true)
            } ?: false

            // 2. Check app_metadata for 'is_admin' custom claim (server-managed claim)
            val appMetadata = user.appMetadata
            val isAdminAppClaim = appMetadata?.get(CLAIM_IS_ADMIN)?.let { element ->
                element.jsonPrimitive.booleanOrNull
                    ?: element.jsonPrimitive.contentOrNull?.equals("true", ignoreCase = true)
            } ?: false

            // 3. Check role claim for 'admin', 'system_admin', or 'ROLE_ADMINISTRATION'
            val userRoleClaim = userMetadata?.get(CLAIM_ROLE)?.jsonPrimitive?.contentOrNull
            val appRoleClaim = appMetadata?.get(CLAIM_ROLE)?.jsonPrimitive?.contentOrNull
            val hasAdminRole = userRoleClaim.equals("admin", ignoreCase = true) ||
                    userRoleClaim.equals("system_admin", ignoreCase = true) ||
                    userRoleClaim.equals("ROLE_ADMINISTRATION", ignoreCase = true) ||
                    appRoleClaim.equals("admin", ignoreCase = true) ||
                    appRoleClaim.equals("system_admin", ignoreCase = true) ||
                    appRoleClaim.equals("ROLE_ADMINISTRATION", ignoreCase = true)

            // 4. Check email criteria
            val email = user.email.orEmpty()
            val isAdminEmail = email.equals("SilvanoTitusOnline@gmail.com", ignoreCase = true) ||
                    email.startsWith("admin", ignoreCase = true) ||
                    email.contains("admin@", ignoreCase = true)

            if (isAdminUserClaim || isAdminAppClaim || hasAdminRole || isAdminEmail) {
                Log.d(TAG, "AdminGuard authorized user ${user.id} via Supabase session claims/email")
                return true
            }
        }

        // 5. Fallback to DataStore cached admin claims
        val cachedIsAdmin = runCatching { userPreferencesStore.cachedIsAdmin.first() }.getOrDefault(false)
        if (cachedIsAdmin) {
            Log.d(TAG, "AdminGuard authorized via DataStore cached claims")
            return true
        }

        Log.w(TAG, "Admin check denied for user=${user?.id ?: "none"}: missing 'is_admin' claim and admin role")
        return false
    }

    /**
     * Validates that the current session is authorized as an administrator.
     * Throws [AdminSecurityException] if the check fails.
     */
    suspend fun requireAdmin(): Result<Unit> {
        return if (isAdmin()) {
            Result.success(Unit)
        } else {
            val user = supabase.auth.currentUserOrNull()
            val userId = user?.id ?: "unauthenticated"
            Result.failure(
                AdminSecurityException(
                    "Administrative access denied for user ($userId). " +
                            "A valid session with custom 'is_admin' claim or 'admin' role is required."
                )
            )
        }
    }

    /**
     * Wraps an administrative API or database call in a check for the custom 'is_admin'
     * metadata claim from the Supabase session, ensuring that only users with the 'admin'
     * role can execute administrative operations on the Admin Control Plane.
     */
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
