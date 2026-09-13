package za.org.rtc.remediation.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridge only validated sessions emitted by the host's identity SDK. The server
 * independently verifies every JWT. Passwords and refresh tokens stay in that SDK. */
class SdkSessionProvider : SessionProvider {
    private val current=MutableStateFlow<Session?>(null)
    override val session=current.asStateFlow()
    fun updateFromSdk(userId: String?, accessToken: String?) {
        require((userId==null)==(accessToken==null))
        require(userId==null || userId.isNotBlank())
        require(accessToken==null || accessToken.isNotBlank())
        current.value=if(userId!=null && accessToken!=null) Session(userId,accessToken) else null
    }
    fun clear() { current.value=null }
}
