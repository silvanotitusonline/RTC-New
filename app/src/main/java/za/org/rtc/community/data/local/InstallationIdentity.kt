package za.org.rtc.community.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable, installation-scoped key used only for local continuity and idempotency.
 *
 * This value is deliberately not an account identifier, authorization credential, role, or proof
 * of server-side ownership. Protected operations must continue to obtain authority from Supabase
 * and server-side policy checks.
 */
@Singleton
class InstallationIdentity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val id: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        preferences.getString(KEY_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: "installation_${UUID.randomUUID()}".also { generated ->
                preferences.edit().putString(KEY_ID, generated).apply()
            }
    }

    val localOwnerKey: String
        get() = "installation:$id"

    private companion object {
        const val PREFERENCES_NAME = "rtc_installation_identity"
        const val KEY_ID = "installation_id"
    }
}
