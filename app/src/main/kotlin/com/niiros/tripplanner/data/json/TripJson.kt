package com.niiros.tripplanner.data.json

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.Stop
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.data.db.TripRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** File format for export/import. Contains places and times only: never the API key. */
@Serializable
data class TripFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val name: String,
    val date: String,
    val departureTime: String,
    val start: PlaceJson? = null,
    val stops: List<StopJson> = emptyList(),
) {
    companion object {
        const val FORMAT = "tripplanner-day"
    }
}

@Serializable
data class PlaceJson(val name: String, val address: String? = null, val lat: Double, val lng: Double, val placeId: String? = null)

@Serializable
data class StopJson(
    val place: PlaceJson,
    val dwellMinutes: Long,
    val needsDaylight: Boolean = false,
    val minchaHere: Boolean = false,
    val arvitHere: Boolean = false,
)

object TripJson {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(trip: Trip): String = json.encodeToString(
        TripFile.serializer(),
        TripFile(
            name = trip.name,
            date = trip.date.toString(),
            departureTime = trip.departureTime.toString(),
            start = trip.start?.toJson(),
            stops = trip.stops.map { StopJson(it.place.toJson(), it.dwell.toMinutes(), it.needsDaylight, it.minchaHere, it.arvitHere) },
        ),
    )

    /** Returns null if the text is not a trip file. Imported trips get fresh ids. */
    fun decode(text: String): Trip? = try {
        val f = json.decodeFromString(TripFile.serializer(), text)
        if (f.format != TripFile.FORMAT) {
            null
        } else {
            Trip(
                id = TripRepository.newId(),
                name = f.name,
                date = LocalDate.parse(f.date),
                departureTime = LocalTime.parse(f.departureTime),
                start = f.start?.toPlace(),
                stops = f.stops.map {
                    Stop(TripRepository.newId(), it.place.toPlace(), Duration.ofMinutes(it.dwellMinutes.coerceIn(0, 24 * 60)), it.needsDaylight, it.minchaHere, it.arvitHere)
                },
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun PlaceRef.toJson() = PlaceJson(name, address, point.lat, point.lng, placeId)
    private fun PlaceJson.toPlace() = PlaceRef(name, address, GeoPoint(lat, lng), placeId)
}
