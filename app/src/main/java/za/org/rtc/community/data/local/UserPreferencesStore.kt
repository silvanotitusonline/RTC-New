package za.org.rtc.community.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.core.UserRole

private val Context.rtcPreferences by preferencesDataStore(name = "rtc_user_preferences")

data class LocalUserPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val simplifiedReading: Boolean = false,
)

/**
 * Device cache only: the server remains the authority for published UI configuration.
 * Invalid payloads are never persisted and reads always provide the compiled default.
 */
data class CachedGlobalUiConfiguration(
    val versionId: String? = null,
    val configuration: GlobalUiConfiguration = GlobalUiConfiguration.default(),
)

@Singleton
class UserPreferencesStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val reading = booleanPreferencesKey("simplified_reading")
        val globalUiConfiguration = stringPreferencesKey("global_ui_configuration")
        val globalUiConfigurationVersion = stringPreferencesKey("global_ui_configuration_version")
        val rememberedEmail = stringPreferencesKey("remembered_email")
        val rememberMe = booleanPreferencesKey("remember_me")
        val cachedUserId = stringPreferencesKey("cached_user_id")
        val cachedUserRole = stringPreferencesKey("cached_user_role")
        val cachedIsAdmin = booleanPreferencesKey("cached_is_admin")
    }

    val cachedUserId: Flow<String> = context.rtcPreferences.data.map { values ->
        values[Keys.cachedUserId] ?: ""
    }

    val cachedUserRole: Flow<UserRole> = context.rtcPreferences.data.map { values ->
        val name = values[Keys.cachedUserRole] ?: UserRole.ANONYMOUS_PUBLIC.name
        runCatching { UserRole.valueOf(name) }.getOrDefault(UserRole.ANONYMOUS_PUBLIC)
    }

    val cachedIsAdmin: Flow<Boolean> = context.rtcPreferences.data.map { values ->
        values[Keys.cachedIsAdmin] ?: false
    }

    suspend fun cacheUserRoles(userId: String, role: UserRole, isAdmin: Boolean) {
        context.rtcPreferences.edit { values ->
            values[Keys.cachedUserId] = userId
            values[Keys.cachedUserRole] = role.name
            values[Keys.cachedIsAdmin] = isAdmin || role == UserRole.SYSTEM_ADMIN
        }
    }

    suspend fun clearUserRoles() {
        context.rtcPreferences.edit { values ->
            values.remove(Keys.cachedUserId)
            values.remove(Keys.cachedUserRole)
            values.remove(Keys.cachedIsAdmin)
        }
    }

    val rememberedEmail: Flow<String> = context.rtcPreferences.data.map { values ->
        values[Keys.rememberedEmail] ?: ""
    }

    val isRememberMeEnabled: Flow<Boolean> = context.rtcPreferences.data.map { values ->
        values[Keys.rememberMe] ?: false
    }

    val preferences: Flow<LocalUserPreferences> = context.rtcPreferences.data.map { values ->
        LocalUserPreferences(
            theme = runCatching {
                ThemePreference.valueOf(values[Keys.theme] ?: ThemePreference.SYSTEM.name)
            }.getOrDefault(ThemePreference.SYSTEM),
            dynamicColor = values[Keys.dynamicColor] ?: true,
            simplifiedReading = values[Keys.reading] ?: false,
        )
    }

    val cachedGlobalUiConfiguration: Flow<CachedGlobalUiConfiguration> = context.rtcPreferences.data.map { values ->
        CachedGlobalUiConfiguration(
            versionId = values[Keys.globalUiConfigurationVersion],
            configuration = GlobalUiConfiguration.decodeOrDefault(values[Keys.globalUiConfiguration]),
        )
    }

    suspend fun setTheme(value: ThemePreference) {
        context.rtcPreferences.edit { it[Keys.theme] = value.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.rtcPreferences.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setReadingMode(enabled: Boolean) {
        context.rtcPreferences.edit { it[Keys.reading] = enabled }
    }

    suspend fun setCommunityGuidelinesAccepted(userId: String, accepted: Boolean) {
        val key = booleanPreferencesKey("guidelines_accepted_${userId.ifBlank { "default" }}")
        context.rtcPreferences.edit { it[key] = accepted }
    }

    suspend fun isCommunityGuidelinesAccepted(userId: String): Boolean {
        val key = booleanPreferencesKey("guidelines_accepted_${userId.ifBlank { "default" }}")
        return context.rtcPreferences.data.map { it[key] ?: false }.first()
    }

    suspend fun setInteractiveTutorialCompleted(userId: String, completed: Boolean) {
        val key = booleanPreferencesKey("tutorial_completed_${userId.ifBlank { "default" }}")
        context.rtcPreferences.edit { it[key] = completed }
    }

    suspend fun isInteractiveTutorialCompleted(userId: String): Boolean {
        val key = booleanPreferencesKey("tutorial_completed_${userId.ifBlank { "default" }}")
        return context.rtcPreferences.data.map { it[key] ?: false }.first()
    }

    suspend fun saveLastKnownGoodGlobalUiConfiguration(versionId: String, rawConfiguration: String): Boolean {
        val configuration = GlobalUiConfiguration.decodeOrNull(rawConfiguration) ?: return false
        val safeVersionId = versionId.trim().takeIf { value ->
            value.length in 1..128 && value.all { it.isLetterOrDigit() || it == '-' }
        } ?: return false
        context.rtcPreferences.edit { values ->
            values[Keys.globalUiConfigurationVersion] = safeVersionId
            values[Keys.globalUiConfiguration] = GlobalUiConfiguration.encode(configuration)
        }
        return true
    }

    suspend fun setRememberedEmail(email: String, remember: Boolean) {
        context.rtcPreferences.edit { values ->
            values[Keys.rememberMe] = remember
            if (remember) {
                values[Keys.rememberedEmail] = email.trim()
            } else {
                values.remove(Keys.rememberedEmail)
            }
        }
    }

    suspend fun clearRememberedEmail() {
        context.rtcPreferences.edit { values ->
            values[Keys.rememberMe] = false
            values.remove(Keys.rememberedEmail)
        }
    }
}
