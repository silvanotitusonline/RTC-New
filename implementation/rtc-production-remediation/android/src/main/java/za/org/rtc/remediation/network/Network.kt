package za.org.rtc.remediation.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Supplied by the host identity SDK. Never store a password or service-role key here. */
data class Session(val userId: String, val accessToken: String)
interface SessionProvider { val session: StateFlow<Session?> }
class OfflineException : IOException("Network offline. Your saved request will retry when connected.")
class AuthenticationRequiredException : IOException("Sign in again to send your saved requests.")

class Connectivity(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    fun isOnline(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } == true
}

/** A hint only: connectivity can disappear after this check, so Room owns recovery. */
class ConnectivityInterceptor(private val connectivity: Connectivity) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        if (!connectivity.isOnline()) throw OfflineException()
        return chain.proceed(chain.request())
    }
}
private data class AccountBinding(val userId: String)
class BearerInterceptor(private val origin: HttpUrl, private val sessions: SessionProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val url = request.url
        // Coil can load provider images from another host: never forward credentials there.
        if (url.scheme != origin.scheme || url.host != origin.host || url.port != origin.port) {
            return chain.proceed(request.newBuilder().removeHeader("Authorization").removeHeader("X-RTC-Expected-Account").build())
        }
        val current = sessions.session.value ?: throw AuthenticationRequiredException()
        val expected = request.tag(AccountBinding::class.java)?.userId
        if (expected != null && expected != current.userId) throw AuthenticationRequiredException()
        return chain.proceed(request.newBuilder().removeHeader("X-RTC-Expected-Account")
            .header("Authorization", "Bearer ${current.accessToken}").build())
    }
}

internal val rtcJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
class RtcNetwork(baseUrl: String, sessions: SessionProvider, connectivity: Connectivity) {
    val base: HttpUrl = baseUrl.toHttpUrl().also {
        require(it.isHttps && it.encodedPath.endsWith('/')) { "Supply an HTTPS API base URL ending in /." }
    }
    val imageClient = OkHttpClient.Builder()
        .addInterceptor(ConnectivityInterceptor(connectivity))
        .addNetworkInterceptor(BearerInterceptor(base, sessions))
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).build()

    /** Each call is bound to its initiating account even if a user switches during dispatch. */
    fun apiFor(userId: String): RtcApi {
        val client = imageClient.newBuilder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().tag(AccountBinding::class.java,AccountBinding(userId)).build())
        }.build()
        return Retrofit.Builder().baseUrl(base).client(client)
            .addConverterFactory(rtcJson.asConverterFactory("application/json".toMediaType()))
            .build().create(RtcApi::class.java)
    }
}
