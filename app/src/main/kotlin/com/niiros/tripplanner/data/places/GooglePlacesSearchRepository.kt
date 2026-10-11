package com.niiros.tripplanner.data.places

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.PlacesStatusCodes
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.api.net.kotlin.awaitFindAutocompletePredictions
import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.repo.PlaceSearchRepository
import com.niiros.tripplanner.core.repo.PlaceSearchResult
import com.niiros.tripplanner.core.repo.PlaceSuggestion
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** Places SDK for Android (Places API (New)), Hebrew results, biased to the previous stop. */
class GooglePlacesSearchRepository(context: Context, apiKey: String) : PlaceSearchRepository {

    private val client: PlacesClient

    init {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey, Locale.forLanguageTag("he"))
        }
        client = Places.createClient(context.applicationContext)
    }

    /** One session per search-and-pick, which is how autocomplete is billed. */
    private var session: AutocompleteSessionToken? = null

    override suspend fun autocomplete(query: String, biasCenter: GeoPoint?): PlaceSearchResult {
        if (query.isBlank()) return PlaceSearchResult.Ok(emptyList())
        val token = session ?: AutocompleteSessionToken.newInstance().also { session = it }
        return try {
            val response = client.awaitFindAutocompletePredictions {
                this.query = query
                this.sessionToken = token
                if (biasCenter != null) {
                    locationBias = CircularBounds.newInstance(LatLng(biasCenter.lat, biasCenter.lng), BIAS_RADIUS_M)
                }
            }
            PlaceSearchResult.Ok(
                response.autocompletePredictions.map {
                    PlaceSuggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString().ifBlank { null })
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PlaceSearchResult.Failed(e.toServiceError())
        }
    }

    override suspend fun details(suggestion: PlaceSuggestion): PlaceRef? {
        val token = session
        return try {
            val place = client.awaitFetchPlace(
                suggestion.id,
                listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION),
            ) {
                sessionToken = token
            }.place
            val location = place.location ?: return null
            PlaceRef(
                name = place.displayName ?: suggestion.primary,
                address = place.formattedAddress,
                point = GeoPoint(location.latitude, location.longitude),
                placeId = place.id,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            session = null
        }
    }

    private fun Exception.toServiceError(): ServiceError {
        val code = (this as? ApiException)?.statusCode ?: return ServiceError.UNKNOWN
        return when (code) {
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> ServiceError.OFFLINE
            PlacesStatusCodes.OVER_QUERY_LIMIT -> ServiceError.QUOTA
            PlacesStatusCodes.REQUEST_DENIED -> ServiceError.KEY_INVALID
            else -> ServiceError.UNKNOWN
        }
    }

    companion object {
        /** Maximum radius the Places API accepts for a circular bias. */
        const val BIAS_RADIUS_M = 50_000.0
    }
}
