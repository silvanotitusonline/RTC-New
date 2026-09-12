
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
) : BaseViewModel() {

    private val _accountState = MutableStateFlow(AccountState())
    val accountState = _accountState.asStateFlow()

    fun updateProfileVisibility(visibility: String) {
        launchSafe {
            // REAL REPOSITORY CALL
            repository.updateAccountSetting("profile_visibility", visibility)
                .onSuccess {
                    _accountState.update { it.copy(profileVisibility = visibility) }
                }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        launchSafe {
            repository.updateAccountSetting("notifications_enabled", enabled.toString())
                .onSuccess {
                    _accountState.update { it.copy(notificationsEnabled = enabled) }
                }
        }
    }

    fun requestAccountDeletion() {
        launchSafe {
            repository.initiateDeletionRequest()
        }
    }

    fun updateMfaStatus(enabled: Boolean) {
        launchSafe {
            repository.updateMfaPreference(enabled)
            _accountState.update { it.copy(mfaEnabled = enabled) }
        }
    }
}

data class AccountState(
    val profileVisibility: String = "PUBLIC",
    val notificationsEnabled: Boolean = true,
    val mfaEnabled: Boolean = false,
    val accountStatus: String = "ACTIVE"
)
