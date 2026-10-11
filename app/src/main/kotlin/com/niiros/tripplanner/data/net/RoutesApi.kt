package com.niiros.tripplanner.data.net

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.repo.RouteRepository
import com.niiros.tripplanner.core.repo.RouteResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** Routes API, `directions/v2:computeRoutes`. */
interface RoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Body body: ComputeRoutesRequest,
        @Header("X-Goog-FieldMask") fieldMask: String = FIELD_MASK,
    ): ComputeRoutesResponse

    companion object {
        /** Only the three fields the schedule needs, to stay in the cheapest response tier. */
        const val FIELD_MASK = "routes.duration,routes.staticDuration,routes.distanceMeters"
    }
}

@Serializable
data class LatLngDto(val latitude: Double, val longitude: Double)

@Serializable
data class LocationDto(val latLng: LatLngDto)

@Serializable
data class WaypointDto(val location: LocationDto)

@Serializable
data class ComputeRoutesRequest(
    val origin: WaypointDto,
    val destination: WaypointDto,
    val travelMode: String = "DRIVE",
    val routingPreference: String,
    /** RFC 3339 UTC timestamp. Omitted for non-traffic requests. */
    val departureTime: String? = null,
    val languageCode: String = "he",
    val units: String = "METRIC",
)

@Serializable
data class RouteDto(
    val duration: String? = null,
    val staticDuration: String? = null,
    val distanceMeters: Int? = null,
)

@Serializable
data class ComputeRoutesResponse(val routes: List<RouteDto> = emptyList())

/**
 * Adds the API key and, for an Android-restricted key, the package name and the SHA-1 of the
 * signing certificate. Google Maps Platform checks these headers when a web service is called
 * directly from an Android app; without them an Android-restricted key is rejected.
 */
class GoogleApiKeyInterceptor(
    private val apiKey: String,
    private val packageName: String,
    private val certSha1: String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Android-Package", packageName)
        if (certSha1 != null) builder.header("X-Android-Cert", certSha1)
        return chain.proceed(builder.build())
    }
}

class RoutesRouteRepository(private val api: RoutesApi, private val json: Json) : RouteRepository {

    override suspend fun route(origin: GeoPoint, destination: GeoPoint, departure: Instant?, trafficAware: Boolean): RouteResult {
        val request = ComputeRoutesRequest(
            origin = origin.waypoint(),
            destination = destination.waypoint(),
            routingPreference = if (trafficAware) "TRAFFIC_AWARE" else "TRAFFIC_UNAWARE",
            departureTime = if (trafficAware) departure?.toString() else null,
        )
        return try {
            val route = api.computeRoutes(request).routes.firstOrNull() ?: return RouteResult.NotFound
            val duration = route.duration.toDurationOrNull() ?: return RouteResult.NotFound
            val static = route.staticDuration.toDurationOrNull() ?: duration
            RouteResult.Found(duration, static, route.distanceMeters ?: 0)
        } catch (e: HttpException) {
            RouteResult.Failed(classify(e))
        } catch (e: IOException) {
            RouteResult.Failed(ServiceError.OFFLINE)
        } catch (e: kotlinx.serialization.SerializationException) {
            RouteResult.Failed(ServiceError.SERVER)
        }
    }

    private fun classify(e: HttpException): ServiceError {
        val status = runCatching {
            val body = e.response()?.errorBody()?.string().orEmpty()
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("status")?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            status == "RESOURCE_EXHAUSTED" || e.code() == 429 -> ServiceError.QUOTA
            status == "PERMISSION_DENIED" || status == "UNAUTHENTICATED" || e.code() == 403 || e.code() == 401 -> ServiceError.KEY_INVALID
            e.code() >= 500 -> ServiceError.SERVER
            else -> ServiceError.UNKNOWN
        }
    }

    private fun GeoPoint.waypoint() = WaypointDto(LocationDto(LatLngDto(lat, lng)))

    /** Durations come as protobuf JSON strings such as "3600s" or "1.5s". */
    private fun String?.toDurationOrNull(): Duration? {
        val s = this?.removeSuffix("s") ?: return null
        val seconds = s.toDoubleOrNull() ?: return null
        return Duration.ofMillis((seconds * 1000).toLong())
    }
}
