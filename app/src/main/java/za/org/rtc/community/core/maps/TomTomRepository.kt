package za.org.rtc.community.core.maps

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Authenticated Edge Function access. The service key never enters the Android application. */
@Singleton
class TomTomRepository @Inject constructor(private val supabase: SupabaseClient) {
    val accountIds = supabase.auth.sessionStatus.map { status ->
        if (status is SessionStatus.Authenticated) supabase.auth.currentUserOrNull()?.id else null
    }.distinctUntilChanged()

    suspend fun configuration(): TomTomMapConfig = request(buildJsonObject { put("action", "config") })

    suspend fun search(query: String, origin: GeoPoint?): TomTomSearchResult = request(buildJsonObject {
        put("action", "search")
        put("query", query.trim())
        origin?.takeIf { it.isValid }?.let { put("origin", it.json()) }
    })

    suspend fun reverse(position: GeoPoint): TomTomSearchResult = request(buildJsonObject {
        require(position.isValid)
        put("action", "reverse")
        put("position", position.json())
    })

    suspend fun route(origin: GeoPoint, destination: GeoPoint, travelMode: String): TomTomRoute = request(buildJsonObject {
        require(origin.isValid && destination.isValid)
        require(travelMode in setOf("car", "pedestrian", "bicycle"))
        put("action", "route")
        put("origin", origin.json())
        put("destination", destination.json())
        put("travelMode", travelMode)
    })

    private suspend inline fun <reified T> request(payload: JsonObject): T {
        currentCoroutineContext().ensureActive()
        val session = supabase.auth.currentSessionOrNull() ?: throw MapServiceException("Sign in to use live maps.")
        val owner = session.user?.id ?: throw MapServiceException("Sign in to use live maps.")
        try {
            // The SDK resolves its current session before invoking this builder. Replace that header
            // with the session captured for these coordinates, and abort if the owner changed.
            val response = supabase.functions.invoke("tomtom-location") {
                if (supabase.auth.currentUserOrNull()?.id != owner) throw CancellationException("Map account changed")
                headers[HttpHeaders.Authorization] = "Bearer ${session.accessToken}"
                headers[HttpHeaders.ContentType] = "application/json"
                setBody(payload.toString())
            }
            if (supabase.auth.currentUserOrNull()?.id != owner) throw CancellationException("Map account changed")
            currentCoroutineContext().ensureActive()
            if (response.status.value !in 200..299) throw MapServiceException(mapServiceErrorMessage(response.status.value))
            return response.body<T>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: RestException) {
            // Functions 3.7 throws for non-success status before returning HttpResponse.
            throw MapServiceException(mapServiceErrorMessage(error.statusCode))
        } catch (known: MapServiceException) {
            throw known
        } catch (_: Exception) {
            // Provider/client exceptions may contain credential-bearing URLs. Never display or log them.
            throw MapServiceException("Cannot reach live maps. Check your connection and retry.")
        }
    }
}

class MapServiceException(message: String) : Exception(message)

internal fun mapServiceErrorMessage(status: Int): String = when (status) {
    401, 403 -> "Your map session needs a fresh sign-in."
    404 -> "No route is available for these locations and travel mode."
    429 -> "Map requests are temporarily limited. Please retry shortly."
    503 -> "The live map service is temporarily unavailable. Please retry."
    else -> "The map request could not be completed. Please retry."
}

private fun GeoPoint.json() = buildJsonObject {
    put("latitude", latitude)
    put("longitude", longitude)
}
