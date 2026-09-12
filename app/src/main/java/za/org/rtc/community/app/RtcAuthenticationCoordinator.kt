package za.org.rtc.community.app

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.data.RtcRepository

internal class RtcAuthenticationCoordinator(
    private val repository: RtcRepository,
    private val supabase: SupabaseClient,
    private val applicationContext: Context,
    private val scope: CoroutineScope,
    private val onStaffAuthenticated: () -> Unit,
) {
    private val _isSessionRestoring = MutableStateFlow(true)
    val isSessionRestoring = _isSessionRestoring.asStateFlow()
    private val _authenticationUi = MutableStateFlow(AuthenticationUiState())
    val authenticationUi = _authenticationUi.asStateFlow()
    private val _administratorMfaUi = MutableStateFlow(AdministratorMfaUiState())
    val administratorMfaUi = _administratorMfaUi.asStateFlow()
    private val _passwordUi = MutableStateFlow(PasswordUiState())
    val passwordUi = _passwordUi.asStateFlow()
    private val _passwordRecoveryActive = MutableStateFlow(false)
    val passwordRecoveryActive = _passwordRecoveryActive.asStateFlow()
    private val _notificationPermissionPrompt = MutableStateFlow(false)
    val notificationPermissionPrompt = _notificationPermissionPrompt.asStateFlow()
    private val _declaredLocalityUi = MutableStateFlow(DeclaredLocalityUiState())
    val declaredLocalityUi = _declaredLocalityUi.asStateFlow()
    private val _feedbackUi = MutableStateFlow(WorkflowSubmissionUiState())
    val feedbackUi = _feedbackUi.asStateFlow()

    /**
     * A locally cached identity may help continuity, but it is never accepted as authenticated
     * authority. Restoration succeeds only when the shared Supabase client exposes a current user.
     */
    suspend fun restoreSession(): Result<Boolean> {
        _isSessionRestoring.value = true
        return try {
            val result = withTimeoutOrNull(2500L) {
                repository.restoreSupabaseSession()
            } ?: Result.success(false)

            if (result.isFailure) {
                _authenticationUi.value = AuthenticationUiState(
                    message = "Your saved session could not be restored. Please sign in again.",
                )
                return result
            }

            if (result.getOrDefault(false) && supabase.auth.currentUserOrNull() == null) {
                // The repository may still contain a legacy cached-session fallback. Clear it rather
                // than allowing that local identity to cross the authentication trust boundary.
                repository.signOutToPublicWelcome()
                _authenticationUi.value = AuthenticationUiState(
                    message = "Your previous sign-in could not be verified. Please sign in again.",
                )
                Result.success(false)
            } else {
                result
            }
        } finally {
            _isSessionRestoring.value = false
        }
    }

    fun registerCurrentFcmToken() {
        if (supabase.auth.currentUserOrNull() == null) return
        runCatching { FirebaseApp.initializeApp(applicationContext) }.getOrNull() ?: return
        runCatching { FirebaseMessaging.getInstance().token }
            .onSuccess { task ->
                task.addOnSuccessListener { token ->
                    scope.launch { repository.registerFcmDevice(token, BuildConfig.VERSION_NAME) }
                }
            }
    }

    fun consumeNotificationPermissionPrompt() {
        _notificationPermissionPrompt.value = false
    }

    fun signInWithEmail(email: String, password: String) {
        scope.launch {
            _authenticationUi.value = AuthenticationUiState(isWorking = true)
            repository.signInWithEmail(email, password)
                .mapCatching {
                    requireVerifiedSession()
                }
                .onSuccess { completeAuthentication() }
                .onFailure { error ->
                    clearUnverifiedLocalSession()
                    _authenticationUi.value = AuthenticationUiState(
                        message = SafeUiError.generic(error, "Sign-in could not be completed."),
                    )
                }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, nonce: String) {
        scope.launch {
            _authenticationUi.value = AuthenticationUiState(isWorking = true)
            repository.signInWithGoogleIdToken(idToken, nonce)
                .mapCatching {
                    requireVerifiedSession()
                }
                .onSuccess { completeAuthentication() }
                .onFailure { error ->
                    clearUnverifiedLocalSession()
                    _authenticationUi.value = AuthenticationUiState(
                        message = SafeUiError.generic(error, "Google sign-in could not be completed."),
                    )
                }
        }
    }

    private fun completeAuthentication() {
        if (supabase.auth.currentUserOrNull() == null) {
            _authenticationUi.value = AuthenticationUiState(
                message = "The account session could not be verified. Please sign in again.",
            )
            return
        }
        _authenticationUi.value = AuthenticationUiState()
        _notificationPermissionPrompt.value = true
        if (repository.session.value.role.isStaff) onStaffAuthenticated()
        registerCurrentFcmToken()
    }

    fun reportGoogleSignInError(message: String) {
        _authenticationUi.value = AuthenticationUiState(message = message.take(240))
    }

    /**
     * Registration goes directly to Supabase. No local account, role, or authenticated session is
     * manufactured if the server rejects the request or email confirmation is still pending.
     */
    fun signUpWithEmail(email: String, password: String, displayName: String) {
        scope.launch {
            _authenticationUi.value = AuthenticationUiState(isWorking = true)
            runCatching {
                val cleanEmail = email.trim()
                val cleanDisplayName = displayName.trim()
                require(cleanEmail.contains('@')) { "Enter a valid email address." }
                require(cleanDisplayName.isNotEmpty()) { "Enter your name to create an account." }
                require(password.isNotBlank()) { "Enter a password." }

                supabase.auth.signUpWith(Email, "rtc://community") {
                    this.email = cleanEmail
                    this.password = password
                    data = buildJsonObject { put("full_name", cleanDisplayName) }
                }
            }.onSuccess {
                if (supabase.auth.currentUserOrNull() != null) {
                    repository.restoreSupabaseSession()
                        .onSuccess { completeAuthentication() }
                        .onFailure { error ->
                            _authenticationUi.value = AuthenticationUiState(
                                message = SafeUiError.generic(error, "Account created, but the session could not be started."),
                            )
                        }
                } else {
                    _authenticationUi.value = AuthenticationUiState(
                        message = "Check your email to confirm your account, then return here to sign in.",
                        confirmationRequired = true,
                    )
                }
            }.onFailure { error ->
                _authenticationUi.value = AuthenticationUiState(
                    message = SafeUiError.generic(error, "Account creation could not be completed."),
                )
            }
        }
    }

    fun dismissAuthenticationMessage() {
        _authenticationUi.value = AuthenticationUiState()
    }

    fun showGlobalMessage(message: String, isSuccess: Boolean = false, isWorking: Boolean = false) {
        _authenticationUi.value = AuthenticationUiState(
            message = message.take(320),
            isSuccess = isSuccess,
            isWorking = isWorking,
        )
    }

    fun requestPasswordRecovery(email: String) {
        scope.launch {
            _passwordUi.value = PasswordUiState(isWorking = true)
            repository.requestPasswordRecovery(email)
                .onSuccess {
                    _passwordUi.value = PasswordUiState(
                        message = "If this email is registered, a password-reset link has been sent.",
                    )
                }
                .onFailure {
                    _passwordUi.value = PasswordUiState(
                        message = "Password recovery could not be started. Check the email address and try again.",
                    )
                }
        }
    }

    fun beginPasswordRecovery() {
        _passwordRecoveryActive.value = true
    }

    fun updatePassword(newPassword: String, currentPassword: String? = null) {
        scope.launch {
            _passwordUi.value = PasswordUiState(isWorking = true)
            repository.updatePassword(newPassword, currentPassword)
                .onSuccess {
                    _passwordUi.value = PasswordUiState(
                        message = "Your password has been updated.",
                        isSuccess = true,
                    )
                }
                .onFailure {
                    _passwordUi.value = PasswordUiState(
                        message = "Password update could not be completed. Check the password requirements and try again.",
                    )
                }
        }
    }

    fun dismissPasswordUi() {
        _passwordUi.value = PasswordUiState()
    }

    fun finishPasswordRecovery() {
        _passwordRecoveryActive.value = false
    }

    fun signOutToPublicWelcome() {
        scope.launch { repository.signOutToPublicWelcome() }
    }

    fun enrollSystemAdministratorTotp() {
        scope.launch {
            _administratorMfaUi.value = AdministratorMfaUiState(isWorking = true)
            repository.enrollSystemAdministratorTotp()
                .onSuccess { enrollment ->
                    _administratorMfaUi.value = AdministratorMfaUiState(enrollment = enrollment)
                }
                .onFailure {
                    _administratorMfaUi.value = AdministratorMfaUiState(
                        message = "MFA enrollment could not be started. Please sign in again and retry.",
                    )
                }
        }
    }

    fun verifySystemAdministratorTotp(factorId: String?, code: String) {
        scope.launch {
            val enrollment = _administratorMfaUi.value.enrollment
            _administratorMfaUi.value = AdministratorMfaUiState(
                isWorking = true,
                enrollment = enrollment,
            )
            repository.verifySystemAdministratorTotp(factorId, code)
                .onSuccess {
                    _administratorMfaUi.value = AdministratorMfaUiState(
                        message = "Authenticator verification complete.",
                    )
                }
                .onFailure {
                    _administratorMfaUi.value = AdministratorMfaUiState(
                        enrollment = enrollment,
                        message = "The verification code was not accepted. Check the time on your authenticator app and try again.",
                    )
                }
        }
    }

    fun dismissAdministratorMfaUi() {
        _administratorMfaUi.value = AdministratorMfaUiState()
    }

    fun uploadProfilePhoto(uri: Uri) {
        scope.launch {
            _authenticationUi.value = AuthenticationUiState(isWorking = true)
            runCatching {
                withTimeout(PROFILE_PHOTO_OPERATION_TIMEOUT_MS) {
                    repository.uploadProfilePhoto(uri).getOrThrow()
                }
            }.onSuccess {
                _authenticationUi.value = AuthenticationUiState(
                    message = "Profile photo saved and refreshed across Community.",
                    isSuccess = true,
                )
            }.onFailure { error ->
                _authenticationUi.value = AuthenticationUiState(
                    message = SafeUiError.profilePhoto(error),
                )
            }
        }
    }

    fun deleteProfilePhoto() {
        scope.launch {
            _authenticationUi.value = AuthenticationUiState(isWorking = true)
            repository.deleteProfilePhoto()
                .onSuccess {
                    _authenticationUi.value = AuthenticationUiState(message = "Profile photo removed.")
                }
                .onFailure {
                    _authenticationUi.value = AuthenticationUiState(
                        message = SafeUiError.generic(it, "Profile photo could not be removed."),
                    )
                }
        }
    }

    fun switchRole(role: UserRole) {
        if (BuildConfig.DEBUG) repository.setRole(role)
    }

    fun toggleReadingMode() {
        scope.launch {
            repository.toggleReadingMode()
                .onFailure { showGlobalMessage(SafeUiError.generic(it, "Reading mode could not be saved.")) }
        }
    }

    fun setTheme(preference: ThemePreference) {
        scope.launch {
            repository.setTheme(preference)
                .onFailure { showGlobalMessage(SafeUiError.generic(it, "Theme preference could not be saved.")) }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        scope.launch {
            repository.setDynamicColor(enabled)
                .onFailure { showGlobalMessage(SafeUiError.generic(it, "Dynamic color preference could not be saved.")) }
        }
    }

    fun updateProfile(displayName: String, bio: String, interests: List<String>) {
        scope.launch {
            repository.updateProfile(displayName, bio, interests)
                .onFailure { showGlobalMessage(SafeUiError.generic(it, "Profile could not be updated.")) }
        }
    }

    fun setNotificationPreference(kind: String, enabled: Boolean) {
        scope.launch {
            val result = if (kind == "community") {
                repository.setOrdinaryAlertPreference(enabled)
            } else {
                repository.setNotificationPreference(kind, enabled)
            }
            result.onFailure {
                showGlobalMessage(SafeUiError.generic(it, "Notification preference could not be updated."))
            }
        }
    }

    fun saveDeclaredLocality(locality: String?) {
        scope.launch {
            _declaredLocalityUi.value = DeclaredLocalityUiState(isWorking = true)
            repository.setDeclaredLocality(locality)
                .onSuccess {
                    _declaredLocalityUi.value = DeclaredLocalityUiState(
                        message = if (locality.isNullOrBlank()) "Declared locality cleared." else "Declared locality saved.",
                        isSuccess = true,
                    )
                }
                .onFailure { error ->
                    _declaredLocalityUi.value = DeclaredLocalityUiState(
                        message = SafeUiError.generic(error, "Declared locality could not be saved."),
                    )
                }
        }
    }

    fun dismissDeclaredLocalityMessage() {
        _declaredLocalityUi.value = DeclaredLocalityUiState()
    }

    fun submitFeedback(message: String, screenshotUri: Uri? = null) {
        scope.launch {
            _feedbackUi.value = WorkflowSubmissionUiState(isWorking = true)
            val deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
            repository.submitFeedback(message, BuildConfig.VERSION_NAME, deviceSummary, screenshotUri)
                .onSuccess {
                    _feedbackUi.value = WorkflowSubmissionUiState(
                        message = "Feedback submitted. Thank you.",
                        isSuccess = true,
                    )
                }
                .onFailure { error ->
                    _feedbackUi.value = WorkflowSubmissionUiState(
                        message = SafeUiError.generic(error, "Feedback could not be submitted."),
                    )
                }
        }
    }

    fun dismissFeedbackMessage() {
        _feedbackUi.value = WorkflowSubmissionUiState()
    }

    private fun requireVerifiedSession() {
        check(supabase.auth.currentUserOrNull() != null) {
            "The authentication server did not establish a verified session."
        }
    }

    private suspend fun clearUnverifiedLocalSession() {
        if (supabase.auth.currentUserOrNull() == null) {
            repository.signOutToPublicWelcome()
        }
    }

    private companion object {
        const val PROFILE_PHOTO_OPERATION_TIMEOUT_MS = 45_000L
    }
}
