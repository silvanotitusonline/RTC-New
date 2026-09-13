package za.org.rtc.community.feature.account

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.org.rtc.community.data.local.UserPreferencesStore

/**
 * Owns device-local resident entry and language preferences only.
 *
 * Granting guest entry does not create an authenticated resident, assign a role, or alter Supabase
 * authority. Server authorization remains the final authority for every protected mutation.
 */
@HiltViewModel
internal class ResidentEntryViewModel @Inject constructor(
    private val preferences: UserPreferencesStore,
) : ViewModel() {
    val residentEntryGranted = preferences.residentEntryGranted.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )

    val applicationLanguage = preferences.onboardingLanguage.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        "en",
    )

    fun continueAsGuest() {
        viewModelScope.launch { preferences.setResidentEntryGranted(true) }
    }

    fun returnToWelcome() {
        viewModelScope.launch { preferences.setResidentEntryGranted(false) }
    }

    fun setApplicationLanguage(language: String) {
        if (language !in UserPreferencesStore.SUPPORTED_ONBOARDING_LANGUAGES) return
        viewModelScope.launch { preferences.setOnboardingLanguage(language) }
    }
}

/** Stable application-level locale state for composables that provide localized copy. */
internal val LocalRtcApplicationLanguage = staticCompositionLocalOf { "en" }
