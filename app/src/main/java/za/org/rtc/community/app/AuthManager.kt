
package za.org.rtc.community.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import za.org.rtc.community.supabase.SupabaseClient

class AuthManager(private val supabase: SupabaseClient, private val context: Context) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState = _authState.asStateFlow()

    suspend fun signInWithGoogle() {
        try {
            supabase.auth.signInWithOAuth(Provider.GOOGLE)
            _authState.value = AuthState.Authenticated
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun signInWithPassword(email: String, pass: String) {
        try {
            supabase.auth.signInWithPassword(email, pass)
            _authState.value = AuthState.Authenticated
        } catch (e: Exception) {
            // Handle error
        }
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    object Authenticated : AuthState()
}
