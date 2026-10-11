package com.niiros.tripplanner.ui.addstop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niiros.tripplanner.AppContainer
import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.Stop
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.plan.StopPlan
import com.niiros.tripplanner.core.repo.PlaceSearchResult
import com.niiros.tripplanner.core.repo.PlaceSuggestion
import com.niiros.tripplanner.data.db.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration

sealed interface SheetMode {
    data object Add : SheetMode
    data class Edit(val stopId: String) : SheetMode
    data object Start : SheetMode
}

data class AddStopUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<PlaceSuggestion> = emptyList(),
    val searchError: ServiceError? = null,
    val loadingPlace: Boolean = false,
    val selected: PlaceRef? = null,
    val dwellMinutes: Long = 30,
    val needsDaylight: Boolean = false,
    val minchaHere: Boolean = false,
    val arvitHere: Boolean = false,
    val preview: StopPlan? = null,
    val previewLoading: Boolean = false,
    val demoMode: Boolean = false,
)

@OptIn(FlowPreview::class)
class AddStopViewModel(
    private val tripId: String,
    private val mode: SheetMode,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(AddStopUiState(demoMode = container.demoMode))
    val state: StateFlow<AddStopUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    private var trip: Trip? = null
    private var previewResolvedZone: String? = null
    private var previewResolvedElevation: Double? = null

    init {
        viewModelScope.launch {
            trip = container.trips.observeTrip(tripId).first()
            val editing = (mode as? SheetMode.Edit)?.let { m -> trip?.stops?.firstOrNull { it.id == m.stopId } }
            if (editing != null) {
                _state.update {
                    it.copy(
                        selected = editing.place,
                        dwellMinutes = editing.dwell.toMinutes(),
                        needsDaylight = editing.needsDaylight,
                        minchaHere = editing.minchaHere,
                        arvitHere = editing.arvitHere,
                    )
                }
                computePreview()
            }
        }
        viewModelScope.launch {
            query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) {
                    _state.update { it.copy(results = emptyList(), searching = false, searchError = null) }
                    return@collectLatest
                }
                _state.update { it.copy(searching = true, searchError = null) }
                when (val r = container.placeSearch.autocomplete(q, biasCenter())) {
                    is PlaceSearchResult.Ok -> _state.update { it.copy(results = r.suggestions, searching = false) }
                    is PlaceSearchResult.Failed -> _state.update { it.copy(results = emptyList(), searching = false, searchError = r.error) }
                }
            }
        }
    }

    /** Autocomplete is biased to the previous stop's area. */
    private fun biasCenter(): GeoPoint? {
        val t = trip ?: return null
        return when (mode) {
            SheetMode.Start -> t.start?.point
            SheetMode.Add -> t.stops.lastOrNull()?.place?.point ?: t.start?.point
            is SheetMode.Edit -> {
                val i = t.stops.indexOfFirst { it.id == mode.stopId }
                (if (i > 0) t.stops[i - 1].place.point else null) ?: t.start?.point
            }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        query.value = q
    }

    fun pick(s: PlaceSuggestion) {
        viewModelScope.launch {
            _state.update { it.copy(loadingPlace = true) }
            val place = container.placeSearch.details(s)
            _state.update { it.copy(loadingPlace = false, selected = place, searchError = if (place == null) ServiceError.UNKNOWN else null) }
            if (place != null && mode != SheetMode.Start) computePreview()
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = null, preview = null) }
    fun setDwell(minutes: Long) = _state.update { it.copy(dwellMinutes = minutes.coerceIn(0, 24 * 60)) }
    fun setNeedsDaylight(v: Boolean) = _state.update { it.copy(needsDaylight = v) }
    fun setMincha(v: Boolean) = _state.update { it.copy(minchaHere = v) }
    fun setArvit(v: Boolean) = _state.update { it.copy(arvitHere = v) }

    private fun candidate(id: String): Stop? {
        val s = _state.value
        val place = s.selected ?: return null
        return Stop(
            id = id,
            place = place,
            dwell = Duration.ofMinutes(s.dwellMinutes),
            needsDaylight = s.needsDaylight,
            minchaHere = s.minchaHere,
            arvitHere = s.arvitHere,
            zoneId = previewResolvedZone,
            elevationM = previewResolvedElevation,
        )
    }

    /** Runs the full planner on the trip with this stop in place and extracts the stop's result. */
    private suspend fun computePreview() {
        val t = trip ?: return
        val id = (mode as? SheetMode.Edit)?.stopId ?: PREVIEW_ID
        val stop = candidate(id) ?: return
        val stops = when (mode) {
            is SheetMode.Edit -> t.stops.map { if (it.id == id) stop.copy(actualDeparture = it.actualDeparture) else it }
            else -> t.stops + stop
        }
        _state.update { it.copy(previewLoading = true) }
        try {
            val settings = container.settings.settings.first()
            val plan = container.dayPlanner.plan(t.copy(stops = stops), settings.planner, container.clock(), container.deviceZone())
            plan.resolvedPointInfo[id]?.let { info ->
                previewResolvedZone = info.zone?.id
                previewResolvedElevation = info.elevationM
            }
            _state.update { it.copy(preview = plan.stops.firstOrNull { sp -> sp.scheduled.stop.id == id }, previewLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(previewLoading = false) }
        }
    }

    /** Saves the stop. Returns the chosen place for start mode (the caller sets it). */
    fun confirm(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = container.trips.getTrip(tripId) ?: return@launch
            when (mode) {
                SheetMode.Add -> candidate(TripRepository.newId())?.let { container.trips.save(current.copy(stops = current.stops + it)) }
                is SheetMode.Edit -> candidate(mode.stopId)?.let { edited ->
                    container.trips.save(
                        current.copy(
                            stops = current.stops.map { s ->
                                if (s.id == mode.stopId) {
                                    val samePlace = s.place.point == edited.place.point
                                    edited.copy(
                                        zoneId = if (samePlace) s.zoneId else edited.zoneId,
                                        elevationM = if (samePlace) s.elevationM else edited.elevationM,
                                        actualDeparture = s.actualDeparture,
                                    )
                                } else {
                                    s
                                }
                            },
                        ),
                    )
                }
                SheetMode.Start -> Unit
            }
            onDone()
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        private const val PREVIEW_ID = "__preview__"
        val DWELL_CHIPS = listOf(15L, 30L, 60L, 120L)
    }
}
