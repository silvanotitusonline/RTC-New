package za.org.rtc.community.ui.auth

import io.github.jan.supabase.auth.providers.Google

/**
 * Centralizes Supabase Google auth initialization, ensuring the correct client ID
 * and OAuth scopes are utilized during authentication flow.
 */
object GoogleAuthProviderConfig {
    const val DEFAULT_WEB_CLIENT_ID = RTC_GOOGLE_WEB_CLIENT_ID
    val REQUIRED_SCOPES = listOf("email", "profile")

    fun configureProvider(): Google {
        return Google
    }

    fun getAuthConfigSummary(): Map<String, String> {
        return mapOf(
            "client_id" to DEFAULT_WEB_CLIENT_ID,
            "scopes" to REQUIRED_SCOPES.joinToString(", ")
        )
    }
}
