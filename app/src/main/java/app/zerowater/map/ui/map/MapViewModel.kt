package app.zerowater.map.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import app.zerowater.map.data.repository.WaterStationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationFilter(
    val requireHot: Boolean = false,
    val requireIced: Boolean = false,
    val noCardOnly: Boolean = false,
    val open24HoursOnly: Boolean = false
)

data class MapUiState(
    val stations: List<WaterStation> = emptyList(),
    val filter: StationFilter = StationFilter(),
    val selectedStation: WaterStation? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: WaterStationRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(StationFilter())
    private val selectedStationIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MapUiState> = combine(
        repository.observeStations(),
        filterFlow,
        selectedStationIdFlow
    ) { allStations, filter, selectedId ->
        val filtered = allStations.filter { station ->
            (!filter.requireHot || station.hasHot) &&
                (!filter.requireIced || station.hasIced) &&
                (!filter.noCardOnly || station.accessType != AccessType.RESTRICTED) &&
                (!filter.open24HoursOnly || station.is24Hours)
        }
        MapUiState(
            stations = filtered,
            filter = filter,
            selectedStation = allStations.find { it.id == selectedId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedDataLoaded()
        }
    }

    fun toggleHotFilter() = filterFlow.update { it.copy(requireHot = !it.requireHot) }

    fun toggleIcedFilter() = filterFlow.update { it.copy(requireIced = !it.requireIced) }

    fun toggleNoCardFilter() = filterFlow.update { it.copy(noCardOnly = !it.noCardOnly) }

    fun selectStation(stationId: String?) {
        selectedStationIdFlow.value = stationId
    }

    fun reportStatus(stationId: String, status: StationStatus) {
        viewModelScope.launch {
            repository.reportStationStatus(stationId, status)
        }
    }
}
