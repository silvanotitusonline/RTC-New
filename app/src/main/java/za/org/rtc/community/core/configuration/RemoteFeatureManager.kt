package za.org.rtc.community.core.configuration

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private val Context.remoteFeatureConfigStore by preferencesDataStore(name = "remote_feature_configuration")

/**
 * RemoteFeatureManager fetches remote JSON feature flag configuration from Supabase,
 * caches it locally in DataStore, and provides reactive Flows for dynamically toggling
 * application features and modules.
 */
@Singleton
class RemoteFeatureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    companion object {
        private const val TAG = "RemoteFeatureManager"
        private const val TABLE_NAME = "app_configuration"
        private const val NAMESPACE_FEATURES = "features"

        private val KEY_CONFIG_JSON = stringPreferencesKey("remote_feature_config_json")
        private val KEY_LAST_FETCH_TIME = longPreferencesKey("remote_feature_config_timestamp")

        // Default baseline fallback features
        private val DEFAULT_FEATURE_FLAGS = mapOf(
            "marketplace" to true,
            "public_reports" to true,
            "service_centre" to true,
            "community_events" to true,
            "admin_control_plane" to true,
            "ai_assistant" to true,
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Flow of the raw JSON configuration stored in DataStore.
     */
    val cachedConfigFlow: Flow<JsonObject> = context.remoteFeatureConfigStore.data.map { preferences ->
        val jsonString = preferences[KEY_CONFIG_JSON]
        if (!jsonString.isNullOrBlank()) {
            runCatching {
                jsonParser.parseToJsonElement(jsonString).jsonObject
            }.getOrDefault(defaultConfigJsonObject())
        } else {
            defaultConfigJsonObject()
        }
    }.distinctUntilChanged()

    init {
        // Automatically initiate startup fetch in background
        scope.launch {
            fetchConfiguration()
        }
    }

    private fun defaultConfigJsonObject(): JsonObject = buildJsonObject {
        DEFAULT_FEATURE_FLAGS.forEach { (key, isEnabled) ->
            put(key, isEnabled)
        }
    }

    /**
     * Fetches the latest JSON feature configuration from Supabase and caches it in DataStore.
     * Can be invoked on app startup or refreshed on-demand.
     */
    suspend fun fetchConfiguration(): Result<JsonObject> = runCatching {
        Log.d(TAG, "Fetching remote configuration from Supabase table '$TABLE_NAME'...")

        val rows = runCatching {
            supabase.from(TABLE_NAME)
                .select {
                    filter {
                        eq("namespace", NAMESPACE_FEATURES)
                    }
                }
                .decodeList<JsonObject>()
        }.getOrElse { error ->
            Log.w(TAG, "Could not fetch features from '$TABLE_NAME' (${error.message}). Checking general app_configuration...")
            emptyList()
        }

        val configMap = mutableMapOf<String, Boolean>()
        // Initialize with compiled defaults
        DEFAULT_FEATURE_FLAGS.forEach { (k, v) -> configMap[k] = v }

        for (row in rows) {
            val key = row["key"]?.jsonPrimitive?.contentOrNull ?: continue
            val isRowEnabled = row["is_enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val valueObj = row["value"]

            var enabled = isRowEnabled
            if (valueObj != null) {
                // If value is a boolean primitive: {"value": true}
                val directBool = valueObj.jsonPrimitive.booleanOrNull
                if (directBool != null) {
                    enabled = enabled && directBool
                } else {
                    // If value is an object: {"enabled": true, ...}
                    val nestedObj = runCatching { valueObj.jsonObject }.getOrNull()
                    val nestedEnabled = nestedObj?.get("enabled")?.jsonPrimitive?.booleanOrNull
                    if (nestedEnabled != null) {
                        enabled = enabled && nestedEnabled
                    }
                }
            }
            configMap[key] = enabled
        }

        val compiledJson = buildJsonObject {
            configMap.forEach { (k, v) -> put(k, v) }
        }

        val compiledJsonString = jsonParser.encodeToString(compiledJson)

        // Cache in DataStore
        context.remoteFeatureConfigStore.edit { preferences ->
            preferences[KEY_CONFIG_JSON] = compiledJsonString
            preferences[KEY_LAST_FETCH_TIME] = System.currentTimeMillis()
        }

        Log.i(TAG, "Remote configuration cached in DataStore successfully with ${configMap.size} feature flags")
        compiledJson
    }.onFailure { error ->
        Log.e(TAG, "Failed to fetch remote feature configuration from Supabase. Retaining cached values.", error)
    }

    /**
     * Provides a reactive Flow indicating whether a specific feature is enabled.
     * Toggles application modules dynamically whenever the cached configuration changes.
     */
    fun isFeatureEnabled(featureKey: String): Flow<Boolean> {
        return cachedConfigFlow.map { jsonConfig ->
            resolveFeatureFlag(jsonConfig, featureKey)
        }.distinctUntilChanged()
    }

    /**
     * Synchronous / suspend query for feature flag status.
     */
    suspend fun isFeatureEnabledSync(featureKey: String): Boolean {
        val currentConfig = cachedConfigFlow.first()
        return resolveFeatureFlag(currentConfig, featureKey)
    }

    /**
     * Resolves the boolean flag from the JSON object supporting nested and root keys.
     */
    private fun resolveFeatureFlag(json: JsonObject, key: String): Boolean {
        // Direct root key check
        json[key]?.let { element ->
            element.jsonPrimitive.booleanOrNull?.let { return it }
            runCatching {
                element.jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull
            }.getOrNull()?.let { return it }
        }

        // Check inside "features" or "modules" container
        listOf("features", "modules").forEach { container ->
            val nested = runCatching { json[container]?.jsonObject }.getOrNull()
            nested?.get(key)?.let { element ->
                element.jsonPrimitive.booleanOrNull?.let { return it }
                runCatching {
                    element.jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull
                }.getOrNull()?.let { return it }
            }
        }

        // Fallback to default
        return DEFAULT_FEATURE_FLAGS[key] ?: true
    }
}
