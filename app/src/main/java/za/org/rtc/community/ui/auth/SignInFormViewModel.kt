package za.org.rtc.community.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.org.rtc.community.data.local.UserPreferencesStore
import javax.inject.Inject

/**
 * State holding real-time validation results for email and password inputs.
 */
data class SignInFormState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val emailTouched: Boolean = false,
    val passwordTouched: Boolean = false,
) {
    val emailError: String?
        get() = when {
            !emailTouched -> null
            email.isBlank() -> "Email address is required"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Please enter a valid email address (e.g. name@example.com)"
            else -> null
        }

    val isEmailValid: Boolean
        get() = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    val passwordError: String?
        get() = when {
            !passwordTouched -> null
            password.isBlank() -> "Password is required"
            password.length < 8 -> "Password must be at least 8 characters"
            !password.any(Char::isLowerCase) -> "Include at least one lowercase letter"
            !password.any(Char::isUpperCase) -> "Include at least one uppercase letter"
            !password.any(Char::isDigit) -> "Include at least one number"
            !password.any { !it.isLetterOrDigit() } -> "Include at least one symbol"
            else -> null
        }

    val isPasswordValid: Boolean
        get() = password.length >= 8 &&
                password.any(Char::isLowerCase) &&
                password.any(Char::isUpperCase) &&
                password.any(Char::isDigit) &&
                password.any { !it.isLetterOrDigit() }

    val isFormValid: Boolean
        get() = isEmailValid && isPasswordValid
}

/**
 * ViewModel managing SignInScreen form validation and input state via StateFlow.
 */
@HiltViewModel
class SignInFormViewModel @Inject constructor(
    private val userPreferencesStore: UserPreferencesStore,
) : ViewModel() {

    private val _email = MutableStateFlow("")
    private val _password = MutableStateFlow("")
    private val _rememberMe = MutableStateFlow(false)
    private val _emailTouched = MutableStateFlow(false)
    private val _passwordTouched = MutableStateFlow(false)

    val formState: StateFlow<SignInFormState> = combine(
        _email,
        _password,
        _rememberMe,
        _emailTouched,
        _passwordTouched,
    ) { email, password, rememberMe, emailTouched, passwordTouched ->
        SignInFormState(
            email = email,
            password = password,
            rememberMe = rememberMe,
            emailTouched = emailTouched,
            passwordTouched = passwordTouched,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SignInFormState()
    )

    init {
        // Pre-fill remembered email from DataStore if present
        viewModelScope.launch {
            userPreferencesStore.rememberedEmail.collect { savedEmail ->
                if (savedEmail.isNotBlank()) {
                    _email.value = savedEmail
                    _rememberMe.value = true
                }
            }
        }
    }

    fun onEmailChanged(value: String) {
        _email.value = value
        _emailTouched.value = true
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
        _passwordTouched.value = true
    }

    fun onRememberMeChanged(checked: Boolean) {
        _rememberMe.value = checked
    }

    fun markAllTouched() {
        _emailTouched.value = true
        _passwordTouched.value = true
    }
}
