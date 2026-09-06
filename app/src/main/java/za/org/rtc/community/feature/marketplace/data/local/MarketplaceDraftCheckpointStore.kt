package za.org.rtc.community.feature.marketplace.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.marketplaceDraftPreferences by preferencesDataStore(name = "rtc_marketplace_drafts")

data class MarketplaceDraftCheckpoint(val businessId: String, val step: Int)

data class BusinessCreationDraft(
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val category: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val lastSavedTimestamp: Long = 0L,
) {
    val isNotEmpty: Boolean
        get() = name.isNotBlank() || tagline.isNotBlank() || description.isNotBlank() ||
                category.isNotBlank() || phone.isNotBlank() || email.isNotBlank() || website.isNotBlank()
}

/**
 * Holds local resume pointers and auto-saved business profile creation form state in DataStore.
 * The Supabase draft remains authoritative for submitted drafts, while uncommitted drafts
 * are continuously preserved across backgrounding, orientation changes, and interruptions.
 */
@Singleton
class MarketplaceDraftCheckpointStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private object Keys {
        val businessId = stringPreferencesKey("business_id")
        val step = intPreferencesKey("step")

        val createDraftName = stringPreferencesKey("create_draft_name")
        val createDraftTagline = stringPreferencesKey("create_draft_tagline")
        val createDraftDescription = stringPreferencesKey("create_draft_description")
        val createDraftCategory = stringPreferencesKey("create_draft_category")
        val createDraftPhone = stringPreferencesKey("create_draft_phone")
        val createDraftEmail = stringPreferencesKey("create_draft_email")
        val createDraftWebsite = stringPreferencesKey("create_draft_website")
        val createDraftTimestamp = longPreferencesKey("create_draft_timestamp")
    }

    val checkpoint: Flow<MarketplaceDraftCheckpoint?> = context.marketplaceDraftPreferences.data.map { values ->
        values[Keys.businessId]?.takeIf { it.isNotBlank() }?.let { id ->
            MarketplaceDraftCheckpoint(id, values[Keys.step]?.coerceIn(1, 9) ?: 1)
        }
    }

    val creationDraft: Flow<BusinessCreationDraft> = context.marketplaceDraftPreferences.data.map { values ->
        BusinessCreationDraft(
            name = values[Keys.createDraftName].orEmpty(),
            tagline = values[Keys.createDraftTagline].orEmpty(),
            description = values[Keys.createDraftDescription].orEmpty(),
            category = values[Keys.createDraftCategory].orEmpty(),
            phone = values[Keys.createDraftPhone].orEmpty(),
            email = values[Keys.createDraftEmail].orEmpty(),
            website = values[Keys.createDraftWebsite].orEmpty(),
            lastSavedTimestamp = values[Keys.createDraftTimestamp] ?: 0L,
        )
    }

    suspend fun read(): MarketplaceDraftCheckpoint? = checkpoint.first()

    suspend fun readCreationDraft(): BusinessCreationDraft = creationDraft.first()

    suspend fun save(businessId: String, step: Int) {
        context.marketplaceDraftPreferences.edit { values ->
            values[Keys.businessId] = businessId
            values[Keys.step] = step.coerceIn(1, 9)
        }
    }

    suspend fun saveCreationDraft(draft: BusinessCreationDraft) {
        context.marketplaceDraftPreferences.edit { values ->
            values[Keys.createDraftName] = draft.name
            values[Keys.createDraftTagline] = draft.tagline
            values[Keys.createDraftDescription] = draft.description
            values[Keys.createDraftCategory] = draft.category
            values[Keys.createDraftPhone] = draft.phone
            values[Keys.createDraftEmail] = draft.email
            values[Keys.createDraftWebsite] = draft.website
            values[Keys.createDraftTimestamp] = System.currentTimeMillis()
        }
    }

    suspend fun clear(businessId: String) {
        context.marketplaceDraftPreferences.edit { values ->
            if (values[Keys.businessId] == businessId) {
                values.remove(Keys.businessId)
                values.remove(Keys.step)
            }
        }
    }

    suspend fun clearCreationDraft() {
        context.marketplaceDraftPreferences.edit { values ->
            values.remove(Keys.createDraftName)
            values.remove(Keys.createDraftTagline)
            values.remove(Keys.createDraftDescription)
            values.remove(Keys.createDraftCategory)
            values.remove(Keys.createDraftPhone)
            values.remove(Keys.createDraftEmail)
            values.remove(Keys.createDraftWebsite)
            values.remove(Keys.createDraftTimestamp)
        }
    }
}
