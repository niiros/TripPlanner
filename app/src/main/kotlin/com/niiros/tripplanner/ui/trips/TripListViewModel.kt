package com.niiros.tripplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niiros.tripplanner.AppContainer
import com.niiros.tripplanner.core.demo.DemoData
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.util.TimeMath
import com.niiros.tripplanner.data.db.TripRepository
import com.niiros.tripplanner.data.db.TripSummary
import com.niiros.tripplanner.data.json.TripJson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripListViewModel(private val container: AppContainer) : ViewModel() {

    val trips: StateFlow<List<TripSummary>?> = container.trips.observeTrips()
        .map<List<TripSummary>, List<TripSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val demoMode: Boolean get() = container.demoMode

    init {
        // In demo mode, the Morocco sample day is added once so the app is useful immediately.
        if (container.demoMode) {
            viewModelScope.launch {
                val existing = container.trips.observeTrips().first()
                if (!container.settings.markSampleSeeded() && existing.isEmpty()) insertSample()
            }
        }
    }

    /** Creates an empty trip for today, departing now (rounded up to 5 minutes). */
    fun createTrip(defaultName: (String) -> String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val now = container.clock().atZone(container.deviceZone())
            val trip = Trip(
                id = TripRepository.newId(),
                name = defaultName("%d.%d".format(now.dayOfMonth, now.monthValue)),
                date = now.toLocalDate(),
                departureTime = TimeMath.ceilLocalTime(now.toLocalTime(), 5),
                start = null,
                stops = emptyList(),
            )
            container.trips.save(trip)
            onCreated(trip.id)
        }
    }

    private suspend fun insertSample(): String {
        val tomorrow = container.clock().atZone(DemoData.MOROCCO).toLocalDate().plusDays(1)
        val sample = DemoData.moroccoTrip(TripRepository.newId(), tomorrow)
        // Ids must be unique per trip; elevations are re-read from the provider in real mode.
        val trip = sample.copy(
            stops = sample.stops.map { it.copy(id = TripRepository.newId(), elevationM = if (container.demoMode) it.elevationM else null) },
            startElevationM = if (container.demoMode) sample.startElevationM else null,
        )
        container.trips.save(trip)
        return trip.id
    }

    fun loadSample(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(insertSample()) }
    }

    fun duplicate(id: String, suffix: String) {
        viewModelScope.launch { container.trips.duplicate(id, suffix) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.trips.delete(id) }
    }

    fun import(text: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val trip = TripJson.decode(text)
            if (trip != null) container.trips.save(trip)
            onResult(trip?.id)
        }
    }
}
