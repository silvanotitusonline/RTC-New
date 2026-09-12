
package za.org.rtc.community.feature.administration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdministrationDashboardViewModel @Inject constructor() : ViewModel() {
    
    // Navigation state for the Admin Hub
    private val _currentSection = MutableStateFlow(AdminSection.OVERVIEW)
    val currentSection = _currentSection.asStateFlow()

    fun setSection(section: AdminSection) {
        _currentSection.value = section
    }
}

enum class AdminSection {
    OVERVIEW,
    MODERATION,
    CIVIC_ANALYTICS,
    USER_MANAGEMENT,
    SYSTEM_HEALTH
}
