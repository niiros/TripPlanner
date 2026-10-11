package com.niiros.tripplanner.data.db

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.Stop
import com.niiros.tripplanner.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TripSummary(val id: String, val name: String, val date: LocalDate, val startName: String?)

class TripRepository(private val dao: TripDao, private val clock: () -> Instant) {

    fun observeTrips(): Flow<List<TripSummary>> = dao.observeTrips().map { list ->
        list.map { TripSummary(it.id, it.name, LocalDate.parse(it.date), it.startName) }
    }

    fun observeTrip(id: String): Flow<Trip?> = dao.observeTrip(id).map { it?.toDomain() }

    suspend fun getTrip(id: String): Trip? = dao.getTrip(id)?.toDomain()

    suspend fun save(trip: Trip) {
        dao.saveTrip(trip.toEntity(clock()), trip.stops.mapIndexed { i, s -> s.toEntity(trip.id, i) })
    }

    suspend fun delete(id: String) = dao.deleteTrip(id)

    /** Copies a trip with fresh ids; the copy drops "יצאתי עכשיו" overrides. */
    suspend fun duplicate(id: String, nameSuffix: String): String? {
        val trip = getTrip(id) ?: return null
        val copy = trip.withNewIds().copy(name = trip.name + nameSuffix)
        save(copy)
        return copy.id
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

fun Trip.withNewIds(): Trip {
    val tripId = TripRepository.newId()
    return copy(id = tripId, stops = stops.map { it.copy(id = TripRepository.newId(), actualDeparture = null) })
}

private fun TripWithStops.toDomain(): Trip {
    val t = trip
    val start = if (t.startLat != null && t.startLng != null) {
        PlaceRef(t.startName ?: "", t.startAddress, GeoPoint(t.startLat, t.startLng), t.startPlaceId)
    } else {
        null
    }
    return Trip(
        id = t.id,
        name = t.name,
        date = LocalDate.parse(t.date),
        departureTime = LocalTime.parse(t.departureTime),
        start = start,
        stops = stops.sortedBy { it.position }.map { s ->
            Stop(
                id = s.id,
                place = PlaceRef(s.name, s.address, GeoPoint(s.lat, s.lng), s.placeId),
                dwell = Duration.ofMinutes(s.dwellMinutes),
                needsDaylight = s.needsDaylight,
                minchaHere = s.minchaHere,
                arvitHere = s.arvitHere,
                actualDeparture = s.actualDepartureEpochSec?.let { Instant.ofEpochSecond(it) },
                zoneId = s.zoneId,
                elevationM = s.elevation,
            )
        },
        startZoneId = t.startZoneId,
        startElevationM = t.startElevation,
    )
}

private fun Trip.toEntity(now: Instant) = TripEntity(
    id = id,
    name = name,
    date = date.toString(),
    departureTime = departureTime.toString(),
    startName = start?.name,
    startAddress = start?.address,
    startLat = start?.point?.lat,
    startLng = start?.point?.lng,
    startPlaceId = start?.placeId,
    startZoneId = startZoneId,
    startElevation = startElevationM,
    updatedAtEpochMs = now.toEpochMilli(),
)

private fun Stop.toEntity(tripId: String, position: Int) = StopEntity(
    id = id,
    tripId = tripId,
    position = position,
    name = place.name,
    address = place.address,
    lat = place.point.lat,
    lng = place.point.lng,
    placeId = place.placeId,
    dwellMinutes = dwell.toMinutes(),
    needsDaylight = needsDaylight,
    minchaHere = minchaHere,
    arvitHere = arvitHere,
    actualDepartureEpochSec = actualDeparture?.epochSecond,
    zoneId = zoneId,
    elevation = elevationM,
)
