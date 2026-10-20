package app.zerowater.map.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.location.LocationTracker
import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import app.zerowater.map.data.repository.WaterStationRepository
import app.zerowater.map.data.route.RouteInfo
import app.zerowater.map.data.route.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StationFilter(
    val requireHot: Boolean = false,
    val requireIced: Boolean = false,
    val noCardOnly: Boolean = false,
    val open24HoursOnly: Boolean = false
)

data class CameraTargetEvent(
    val point: GeoPoint,
    val zoom: Double = 15.5,
    val seq: Int = 0
)

private data class InteractionState(
    val userLocation: GeoPoint? = null,
    val isLocating: Boolean = false,
    val activeRoute: RouteInfo? = null,
    val isLoadingRoute: Boolean = false,
    val isPickingLocation: Boolean = false,
    val pickedLocation: GeoPoint = GeoPoint(25.0375, 121.5320),
    val showAddStationSheet: Boolean = false,
    val cameraTarget: CameraTargetEvent? = null
)

data class MapUiState(
    val stations: List<WaterStation> = emptyList(),
    val filter: StationFilter = StationFilter(),
    val selectedStation: WaterStation? = null,
    val userLocation: GeoPoint? = null,
    val isLocating: Boolean = false,
    val activeRoute: RouteInfo? = null,
    val isLoadingRoute: Boolean = false,
    val nearestStation: WaterStation? = null,
    val nearestDistanceMeters: Float? = null,
    val isPickingLocation: Boolean = false,
    val pickedLocation: GeoPoint = GeoPoint(25.0375, 121.5320),
    val showAddStationSheet: Boolean = false,
    val cameraTarget: CameraTargetEvent? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: WaterStationRepository,
    private val locationTracker: LocationTracker,
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(StationFilter())
    private val selectedStationIdFlow = MutableStateFlow<String?>(null)
    private val interactionFlow = MutableStateFlow(InteractionState())
    private var routeJob: Job? = null
    private var cameraSeq = 0

    val uiState: StateFlow<MapUiState> = combine(
        repository.observeStations(),
        filterFlow,
        selectedStationIdFlow,
        interactionFlow
    ) { allStations, filter, selectedId, interaction ->
        val userLoc = interaction.userLocation
        val filtered = allStations.filter { station ->
            (!filter.requireHot || station.hasHot) &&
                (!filter.requireIced || station.hasIced) &&
                (!filter.noCardOnly || station.accessType != AccessType.RESTRICTED) &&
                (!filter.open24HoursOnly || station.is24Hours)
        }.let { list ->
            if (userLoc != null) {
                list.sortedBy { userLoc.distanceMetersTo(it.latitude, it.longitude) }
            } else {
                list
            }
        }

        val nearest = if (userLoc != null) filtered.firstOrNull() else null
        val nearestDist = if (userLoc != null && nearest != null) {
            userLoc.distanceMetersTo(nearest.latitude, nearest.longitude)
        } else {
            null
        }

        MapUiState(
            stations = filtered,
            filter = filter,
            selectedStation = allStations.find { it.id == selectedId },
            userLocation = userLoc,
            isLocating = interaction.isLocating,
            activeRoute = interaction.activeRoute,
            isLoadingRoute = interaction.isLoadingRoute,
            nearestStation = nearest,
            nearestDistanceMeters = nearestDist,
            isPickingLocation = interaction.isPickingLocation,
            pickedLocation = interaction.pickedLocation,
            showAddStationSheet = interaction.showAddStationSheet,
            cameraTarget = interaction.cameraTarget
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedDataLoaded()
            if (locationTracker.hasLocationPermission()) {
                refreshUserLocation(moveCamera = false)
            }
        }
    }

    fun toggleHotFilter() = filterFlow.update { it.copy(requireHot = !it.requireHot) }

    fun toggleIcedFilter() = filterFlow.update { it.copy(requireIced = !it.requireIced) }

    fun toggleNoCardFilter() = filterFlow.update { it.copy(noCardOnly = !it.noCardOnly) }

    fun toggle24HoursFilter() = filterFlow.update { it.copy(open24HoursOnly = !it.open24HoursOnly) }

    fun refreshUserLocation(moveCamera: Boolean = true) {
        viewModelScope.launch {
            interactionFlow.update { it.copy(isLocating = true) }
            val location = locationTracker.getCurrentLocation()
            interactionFlow.update { current ->
                val targetEvent = if (moveCamera && location != null) {
                    CameraTargetEvent(point = location, zoom = 15.5, seq = ++cameraSeq)
                } else {
                    current.cameraTarget
                }
                current.copy(
                    userLocation = location ?: current.userLocation,
                    isLocating = false,
                    pickedLocation = if (!current.isPickingLocation && location != null) {
                        location
                    } else {
                        current.pickedLocation
                    },
                    cameraTarget = targetEvent
                )
            }
            updateRouteForSelection()
        }
    }

    fun selectStation(stationId: String?, moveCamera: Boolean = false) {
        selectedStationIdFlow.value = stationId
        if (stationId == null) {
            routeJob?.cancel()
            interactionFlow.update { it.copy(activeRoute = null, isLoadingRoute = false) }
            return
        }
        if (moveCamera) {
            val station = uiState.value.stations.find { it.id == stationId }
            if (station != null) {
                interactionFlow.update {
                    it.copy(
                        cameraTarget = CameraTargetEvent(
                            point = GeoPoint(station.latitude, station.longitude),
                            zoom = 16.0,
                            seq = ++cameraSeq
                        )
                    )
                }
            }
        }
        updateRouteForSelection()
    }

    private fun updateRouteForSelection() {
        routeJob?.cancel()
        val stationId = selectedStationIdFlow.value ?: return
        val userLoc = interactionFlow.value.userLocation ?: return
        routeJob = viewModelScope.launch {
            val station = uiState.value.stations.find { it.id == stationId }
                ?: uiState.value.selectedStation
                ?: return@launch
            interactionFlow.update { it.copy(isLoadingRoute = true) }
            val route = routeRepository.calculateWalkingRoute(
                start = userLoc,
                end = GeoPoint(station.latitude, station.longitude)
            )
            interactionFlow.update { it.copy(activeRoute = route, isLoadingRoute = false) }
        }
    }

    fun startPickingNewStationLocation() {
        selectedStationIdFlow.value = null
        interactionFlow.update {
            it.copy(
                isPickingLocation = true,
                showAddStationSheet = false,
                activeRoute = null
            )
        }
    }

    fun cancelPickingNewStationLocation() {
        interactionFlow.update {
            it.copy(
                isPickingLocation = false,
                showAddStationSheet = false
            )
        }
    }

    fun onMapCameraIdle(center: GeoPoint) {
        interactionFlow.update { it.copy(pickedLocation = center) }
    }

    fun confirmPickedLocation() {
        interactionFlow.update {
            it.copy(
                isPickingLocation = false,
                showAddStationSheet = true
            )
        }
    }

    fun dismissAddStationSheet() {
        interactionFlow.update { it.copy(showAddStationSheet = false) }
    }

    fun submitNewStation(
        name: String,
        floorHint: String,
        accessType: AccessType,
        hasHot: Boolean,
        hasWarm: Boolean,
        hasCold: Boolean,
        hasIced: Boolean,
        is24Hours: Boolean,
        openHours: String
    ) {
        val location = interactionFlow.value.pickedLocation
        val newStation = WaterStation(
            id = "user-${UUID.randomUUID()}",
            name = name.trim(),
            latitude = location.latitude,
            longitude = location.longitude,
            floorHint = floorHint.trim().ifEmpty { "1F 飲水機" },
            accessType = accessType,
            hasHot = hasHot,
            hasWarm = hasWarm,
            hasCold = hasCold,
            hasIced = hasIced,
            is24Hours = is24Hours,
            openHours = if (is24Hours) "24 小時開放" else openHours.trim().ifEmpty { "依場館開放時間" },
            status = StationStatus.WORKING
        )
        viewModelScope.launch {
            repository.addStation(newStation)
            interactionFlow.update {
                it.copy(
                    showAddStationSheet = false,
                    isPickingLocation = false,
                    cameraTarget = CameraTargetEvent(
                        point = location,
                        zoom = 16.5,
                        seq = ++cameraSeq
                    )
                )
            }
            selectStation(newStation.id)
        }
    }

    fun reportStatus(stationId: String, status: StationStatus) {
        viewModelScope.launch {
            repository.reportStationStatus(stationId, status)
        }
    }
}
