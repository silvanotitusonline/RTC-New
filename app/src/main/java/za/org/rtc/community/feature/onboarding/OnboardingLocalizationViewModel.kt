package za.org.rtc.community.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.org.rtc.community.data.local.UserPreferencesStore

@HiltViewModel
class OnboardingLocalizationViewModel @Inject constructor(
    private val preferences: UserPreferencesStore,
) : ViewModel() {
    val language = preferences.onboardingLanguage.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "en",
    )

    fun setLanguage(code: String) {
        if (code !in UserPreferencesStore.SUPPORTED_ONBOARDING_LANGUAGES) return
        viewModelScope.launch { preferences.setOnboardingLanguage(code) }
    }
}
