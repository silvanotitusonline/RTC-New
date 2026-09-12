package za.org.rtc.community.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.data.RtcRepository
import za.org.rtc.community.data.local.UserPreferencesStore
import javax.inject.Inject

/**
 * Represents the reactive authentication status of the user.
 */
sealed interface AuthStatus {
    /** The app is determining the session state on launch or re-auth. */
    data object Loading : AuthStatus

    /** The user is unauthenticated (anonymous public guest). */
    data object Unauthenticated : AuthStatus

    /** The user is authenticated with active session details. */
    data class Authenticated(
        val session: RtcSession,
        val userEmail: String? = null,
        val userId: String? = null,
    ) : AuthStatus
}

/**
 * UI State exposed by [AuthStateViewModel] tracking the user's authentication status,
 * error state, and progress.
 */
data class AuthStateUiState(
    val authStatus: AuthStatus = AuthStatus.Loading,
    val isLoading: Boolean = false,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
) {
    val isAuthenticated: Boolean
        get() = authStatus is AuthStatus.Authenticated
}

/**
 * AuthStateViewModel tracks and exposes the user's authentication status as a StateFlow,
 * allowing the UI to reactively transition between the login/sign-in screen and the main
 * app navigation.
 */
@HiltViewModel
class AuthStateViewModel @Inject constructor(
    private val repository: RtcRepository,
    private val supabaseClient: SupabaseClient,
    private val userPreferencesStore: UserPreferencesStore,
) : ViewModel() {

    companion object {
        private const val TAG = "AuthStateViewModel"
    }

    private val _uiState = MutableStateFlow(AuthStateUiState())
    val uiState: StateFlow<AuthStateUiState> = _uiState.asStateFlow()

    /** Exposes remembered email persisted across app restarts via DataStore */
    val rememberedEmail: StateFlow<String> = userPreferencesStore.rememberedEmail
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    /** Exposes remember me state persisted across app restarts via DataStore */
    val isRememberMeEnabled: StateFlow<Boolean> = userPreferencesStore.isRememberMeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * Reactive StateFlow directly representing the current [AuthStatus] for clean UI navigation
     * switching.
     */
    val authStatus: StateFlow<AuthStatus> = repository.session
        .map { rtcSession ->
            if (rtcSession.role == UserRole.ANONYMOUS_PUBLIC) {
                AuthStatus.Unauthenticated
            } else {
                AuthStatus.Authenticated(
                    session = rtcSession,
                    userEmail = rtcSession.authenticatedEmail,
                    userId = rtcSession.id,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthStatus.Loading
        )

    init {
        // Observe session stream from repository to keep uiState in sync
        viewModelScope.launch {
            repository.session.collectLatest { session ->
                val newStatus = if (session.role == UserRole.ANONYMOUS_PUBLIC) {
                    AuthStatus.Unauthenticated
                } else {
                    AuthStatus.Authenticated(
                        session = session,
                        userEmail = session.authenticatedEmail,
                        userId = session.id
                    )
                }
                _uiState.update { current ->
                    current.copy(
                        authStatus = newStatus,
                        isLoading = false,
                    )
                }
            }
        }

        // Listen for Supabase session status events
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collectLatest { sessionStatus ->
                Log.d(TAG, "Supabase session status changed: $sessionStatus")
                when (sessionStatus) {
                    is SessionStatus.NotAuthenticated,
                    is SessionStatus.RefreshFailure -> {
                        _uiState.update { it.copy(authStatus = AuthStatus.Unauthenticated) }
                    }
                    is SessionStatus.Authenticated -> {
                        // Session will be updated reactively via repository.session
                    }
                    is SessionStatus.Initializing -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Signs in using a Google ID token and raw nonce returned by CredentialManager.
     */
    fun signInWithGoogle(idToken: String, nonce: String) {
        viewModelScope.launch {
            AuthenticationLogger.logState(
                AuthenticationLogger.TransitionState.AUTHENTICATING,
                "Starting backend token exchange with Supabase."
            )
            _uiState.update { it.copy(isLoading = true, isAuthenticating = true, errorMessage = null) }
            val result = repository.signInWithGoogleIdToken(idToken, nonce)
            result.fold(
                onSuccess = {
                    Log.i(TAG, "Successfully authenticated with Google ID token")
                    AuthenticationLogger.logState(
                        AuthenticationLogger.TransitionState.SUCCESS,
                        "Successfully authenticated with Google account."
                    )
                    _uiState.update { it.copy(isLoading = false, isAuthenticating = false, errorMessage = null) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed Google authentication: ${error.message}", error)
                    AuthenticationLogger.logState(
                        AuthenticationLogger.TransitionState.FAILURE,
                        "Backend token exchange failed: ${error.message}"
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticating = false,
                            errorMessage = error.localizedMessage ?: "Google sign-in failed. Please try again."
                        )
                    }
                }
            )
        }
    }

    /**
     * Signs in using email and password, handling Remember Me persistence and loading states.
     */
    fun signInWithEmail(email: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Persist or clear remembered email in DataStore
            userPreferencesStore.setRememberedEmail(email, rememberMe)

            val result = repository.signInWithEmail(email.trim(), password)
            result.fold(
                onSuccess = {
                    Log.i(TAG, "Successfully authenticated with email")
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Email authentication failed: ${error.message}", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Invalid email or password. Please try again."
                        )
                    }
                }
            )
        }
    }

    /**
     * Updates Remember Me preference in DataStore.
     */
    fun updateRememberMe(email: String, enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesStore.setRememberedEmail(email, enabled)
        }
    }

    /**
     * Requests password reset using Supabase Auth's resetPasswordForEmail function.
     */
    fun requestPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.requestPasswordRecovery(email.trim())
            _uiState.update { it.copy(isLoading = false) }
            onResult(result)
        }
    }

    /**
     * Signs the user out to the unauthenticated public login screen.
     */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.signOutToPublicWelcome()
            _uiState.update {
                it.copy(
                    authStatus = AuthStatus.Unauthenticated,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Clears any active error messages.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
