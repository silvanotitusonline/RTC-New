package za.org.rtc.community.core.maps

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
        val owner = supabase.auth.currentUserOrNull()?.id ?: throw MapServiceException("Sign in to use live maps.")
        try {
            val response = supabase.functions.invoke("tomtom-location", payload)
            if (supabase.auth.currentUserOrNull()?.id != owner) throw CancellationException("Map account changed")
            when (response.status.value) {
                in 200..299 -> return response.body<T>()
                401, 403 -> throw MapServiceException("Your map session needs a fresh sign-in.")
                404 -> throw MapServiceException("No route is available for these locations and travel mode.")
                429 -> throw MapServiceException("Map requests are temporarily limited. Please retry shortly.")
                503 -> throw MapServiceException("The live map service is temporarily unavailable. Please retry.")
                else -> throw MapServiceException("The map request could not be completed. Please retry.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: MapServiceException) {
            throw known
        } catch (_: Exception) {
            // Provider/client exceptions may contain credential-bearing URLs. Never display or log them.
            throw MapServiceException("Cannot reach live maps. Check your connection and retry.")
        }
    }
}

class MapServiceException(message: String) : Exception(message)

private fun GeoPoint.json() = buildJsonObject {
    put("latitude", latitude)
    put("longitude", longitude)
}
