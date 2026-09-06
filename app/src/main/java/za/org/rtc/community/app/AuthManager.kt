package za.org.rtc.community.app

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import za.org.rtc.community.data.RtcRepository
import za.org.rtc.community.ui.auth.GoogleAuthProviderConfig

/**
 * Manages authentication state and integrates Google ID token sign-in flows
 * utilizing GoogleAuthProviderConfig.
 */
@Singleton
class AuthManager @Inject constructor(
    private val repository: RtcRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val session = repository.session

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): Result<Unit> {
        return repository.signInWithGoogleIdToken(idToken, nonce)
    }

    fun signOut() {
        scope.launch {
            repository.signOutToPublicWelcome()
        }
    }

    val googleProvider = GoogleAuthProviderConfig.configureProvider()
    val webClientId = GoogleAuthProviderConfig.DEFAULT_WEB_CLIENT_ID
}
