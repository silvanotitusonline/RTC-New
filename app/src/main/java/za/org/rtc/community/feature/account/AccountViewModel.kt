
package za.org.rtc.community.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import za.org.rtc.community.data.RtcRepository

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: RtcRepository
) : ViewModel() {

    private val _accountState = MutableStateFlow(AccountState())
    val accountState = _accountState.asStateFlow()

    fun updateProfileVisibility(visibility: String) {
        viewModelScope.launch {
            repository.updateAccountSetting("profile_visibility", visibility)
            _accountState.update { it.copy(profileVisibility = visibility) }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAccountSetting("notifications_enabled", enabled)
            _accountState.update { it.copy(notificationsEnabled = enabled) }
        }
    }

    fun requestAccountDeletion() {
        viewModelScope.launch {
            repository.initiateDeletionRequest()
        }
    }

    fun updateMfaStatus(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateMfaPreference(enabled)
        }
    }
}

data class AccountState(
    val profileVisibility: String = "PUBLIC",
    val notificationsEnabled: Boolean = true,
    val mfaEnabled: Boolean = false,
    val accountStatus: String = "ACTIVE"
)
