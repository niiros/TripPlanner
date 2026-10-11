package com.niiros.tripplanner.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niiros.tripplanner.AppContainer
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.daylight.DaylightFix
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.plan.DayPlan
import com.niiros.tripplanner.core.plan.DayPlanner
import com.niiros.tripplanner.core.util.TimeMath
import com.niiros.tripplanner.data.json.TripJson
import com.niiros.tripplanner.data.settings.AppSettings
import com.niiros.tripplanner.notify.TripReminders
import com.niiros.tripplanner.ui.common.Texts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class TripUiState(
    val trip: Trip? = null,
    val plan: DayPlan? = null,
    val calculating: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val demoMode: Boolean = false,
    val locationProblem: Int? = null,
)

/** Main timeline screen. Every change to the trip or settings recalculates the whole chain. */
@OptIn(FlowPreview::class)
class TripViewModel(
    private val tripId: String,
    private val isNew: Boolean,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState(demoMode = container.demoMode))
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val refresh = MutableStateFlow(0)

    /** For a new trip, "now" is re-expressed in the start point's zone once that zone is known. */
    private var autoDeparture = isNew

    init {
        viewModelScope.launch {
            combine(container.trips.observeTrip(tripId).filterNotNull(), container.settings.settings, refresh) { trip, settings, _ ->
                trip to settings
            }.debounce(RECALC_DEBOUNCE_MS).collectLatest { (trip, settings) ->
                _state.update { it.copy(trip = trip, settings = settings, calculating = true) }
                try {
                    val plan = container.dayPlanner.plan(trip, settings.planner, container.clock(), container.deviceZone())
                    _state.update { it.copy(plan = plan, calculating = false) }
                    if (settings.notificationsEnabled) {
                        TripReminders.schedule(container.appContext, plan, settings.planner.sunsetBuffer, container.clock())
                    }
                    persistResolvedInfo(trip, plan)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(calculating = false) }
                }
            }
        }
    }

    private suspend fun persistResolvedInfo(trip: Trip, plan: DayPlan) {
        val info = plan.resolvedPointInfo
        var updated = trip
        info[DayPlanner.START_KEY]?.let { p ->
            if (trip.startZoneId == null || trip.startElevationM == null) {
                updated = updated.copy(startZoneId = trip.startZoneId ?: p.zone?.id, startElevationM = trip.startElevationM ?: p.elevationM)
            }
        }
        if (autoDeparture) {
            val zone = updated.startZoneId?.let { ZoneId.of(it) }
            if (zone != null) {
                autoDeparture = false
                val now = container.clock().atZone(zone)
                updated = updated.copy(date = now.toLocalDate(), departureTime = TimeMath.ceilLocalTime(now.toLocalTime(), 5))
            }
        }
        updated = updated.copy(
            stops = updated.stops.map { s ->
                val p = info[s.id] ?: return@map s
                s.copy(zoneId = s.zoneId ?: p.zone?.id, elevationM = s.elevationM ?: p.elevationM)
            },
        )
        if (updated != trip) container.trips.save(updated)
    }

    private fun edit(clearOverrides: Boolean = false, freshTraffic: Boolean = false, transform: (Trip) -> Trip) {
        viewModelScope.launch {
            val current = container.trips.getTrip(tripId) ?: return@launch
            var next = transform(current)
            if (clearOverrides) next = next.copy(stops = next.stops.map { it.copy(actualDeparture = null) })
            if (freshTraffic) container.legPlanner.clearCache()
            container.trips.save(next)
        }
    }

    fun setDate(date: LocalDate) {
        autoDeparture = false
        edit(clearOverrides = true) { it.copy(date = date) }
    }

    fun setDepartureTime(time: LocalTime) {
        autoDeparture = false
        edit(clearOverrides = true) { it.copy(departureTime = time) }
    }

    fun rename(name: String) = edit { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun deleteStop(id: String) = edit { t -> t.copy(stops = t.stops.filterNot { it.id == id }) }

    fun reorder(orderedIds: List<String>) = edit { t ->
        val byId = t.stops.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        if (reordered.size == t.stops.size) t.copy(stops = reordered) else t
    }

    fun moveStop(id: String, delta: Int) = edit { t ->
        val i = t.stops.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in t.stops.indices) t else t.copy(stops = t.stops.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun applyFix(fix: DaylightFix) {
        when (fix) {
            is DaylightFix.LeaveEarlier -> {
                autoDeparture = false
                edit(clearOverrides = true, freshTraffic = true) { it.copy(departureTime = fix.newDeparture) }
            }
            is DaylightFix.ShortenDwell -> edit(freshTraffic = true) { t ->
                t.copy(stops = t.stops.map { s -> if (s.id == fix.stopId) s.copy(dwell = s.dwell.minus(fix.by).coerceAtLeast(MIN_DWELL)) else s })
            }
        }
    }

    /** Re-anchors the schedule to the actual current time, from the stop the user is at now. */
    fun leftNow(texts: Texts) {
        val plan = _state.value.plan ?: return
        val now = container.clock().truncatedTo(ChronoUnit.MINUTES)
        val index = DayPlanner.currentStopIndex(plan.schedule, now)
        autoDeparture = false
        if (index < 0) {
            val zone = plan.schedule.startZone
            val local = now.atZone(zone)
            edit(clearOverrides = true, freshTraffic = true) { it.copy(date = local.toLocalDate(), departureTime = local.toLocalTime()) }
            _messages.tryEmit(texts.s(R.string.trip_left_now_done, plan.trip.start?.name ?: ""))
        } else {
            val stopId = plan.stops[index].scheduled.stop.id
            edit(freshTraffic = true) { t ->
                val pos = t.stops.indexOfFirst { it.id == stopId }
                t.copy(
                    stops = t.stops.mapIndexed { i, s ->
                        when {
                            i == pos -> s.copy(actualDeparture = now)
                            i > pos -> s.copy(actualDeparture = null)
                            else -> s
                        }
                    },
                )
            }
            _messages.tryEmit(texts.s(R.string.trip_left_now_done, plan.stops[index].point.place.name))
        }
    }

    fun setStart(place: PlaceRef) = edit(clearOverrides = true) {
        it.copy(start = place, startZoneId = null, startElevationM = null)
    }

    /** Returns false when location permission is missing or no fix could be obtained. */
    fun useCurrentLocation(myLocationName: String) {
        viewModelScope.launch {
            if (!container.location.hasPermission()) {
                _state.update { it.copy(locationProblem = R.string.error_location_denied) }
                return@launch
            }
            val point = container.location.current()
            if (point == null) {
                _state.update { it.copy(locationProblem = R.string.error_location_unavailable) }
                return@launch
            }
            _state.update { it.copy(locationProblem = null) }
            setStart(PlaceRef(myLocationName, null, point))
        }
    }

    fun onLocationDenied() = _state.update { it.copy(locationProblem = R.string.error_location_denied) }

    fun retry() {
        container.legPlanner.clearCache()
        refresh.update { it + 1 }
    }

    suspend fun exportJson(): String? = container.trips.observeTrip(tripId).first()?.let { TripJson.encode(it) }

    fun shareText(texts: Texts): String? {
        val plan = _state.value.plan ?: return null
        return ShareText.build(plan, texts)
    }

    companion object {
        const val RECALC_DEBOUNCE_MS = 350L
        val MIN_DWELL: Duration = Duration.ofMinutes(5)
    }
}
