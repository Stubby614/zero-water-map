package app.zerowater.map.ui.map

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zerowater.map.data.local.PhotoStorageHelper
import app.zerowater.map.data.location.AddressCandidate
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.location.GeocodingService
import app.zerowater.map.data.location.LocationTracker
import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationRatingSummary
import app.zerowater.map.data.model.StationReview
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
    val open24HoursOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val searchQuery: String = ""
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
    val pickedAddress: String? = null,
    val addressSearchResults: List<AddressCandidate> = emptyList(),
    val isSearchingAddress: Boolean = false,
    val showAddStationSheet: Boolean = false,
    val showStationListSheet: Boolean = false,
    val isDiscoveringOsm: Boolean = false,
    val discoveryStatusMessage: String? = null,
    val cameraTarget: CameraTargetEvent? = null,
    val draftStationPhotoUri: String? = null,
    val draftReviewPhotoUri: String? = null
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
    val pickedAddress: String? = null,
    val addressSearchResults: List<AddressCandidate> = emptyList(),
    val isSearchingAddress: Boolean = false,
    val showAddStationSheet: Boolean = false,
    val showStationListSheet: Boolean = false,
    val isDiscoveringOsm: Boolean = false,
    val discoveryStatusMessage: String? = null,
    val cameraTarget: CameraTargetEvent? = null,
    val selectedStationReviews: List<StationReview> = emptyList(),
    val ratingSummaries: Map<String, StationRatingSummary> = emptyMap(),
    val selectedStationPhotos: List<String> = emptyList(),
    val draftStationPhotoUri: String? = null,
    val draftReviewPhotoUri: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: WaterStationRepository,
    private val locationTracker: LocationTracker,
    private val geocodingService: GeocodingService,
    private val routeRepository: RouteRepository,
    private val photoStorageHelper: PhotoStorageHelper
) : ViewModel() {

    private val filterFlow = MutableStateFlow(StationFilter())
    private val selectedStationIdFlow = MutableStateFlow<String?>(null)
    private val interactionFlow = MutableStateFlow(InteractionState())
    private var routeJob: Job? = null
    private var reverseGeocodeJob: Job? = null
    private var searchAddressJob: Job? = null
    private var cameraSeq = 0
    val uiState: StateFlow<MapUiState> = combine(
        repository.observeStations(),
        repository.observeAllReviews(),
        filterFlow,
        selectedStationIdFlow,
        interactionFlow
    ) { allStations, allReviews, filter, selectedId, interaction ->
        val userLoc = interaction.userLocation
        val query = filter.searchQuery.trim()
        val filtered = allStations.filter { station ->
            (!filter.requireHot || station.hasHot) &&
                (!filter.requireIced || station.hasIced) &&
                (!filter.noCardOnly || station.accessType != AccessType.RESTRICTED) &&
                (!filter.open24HoursOnly || station.is24Hours) &&
                (!filter.favoritesOnly || station.isFavorite) &&
                (query.isEmpty() ||
                    station.name.contains(query, ignoreCase = true) ||
                    station.floorHint.contains(query, ignoreCase = true))
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
        val summaries = allReviews.groupBy { it.stationId }.mapValues { (_, reviews) ->
            val avg = if (reviews.isEmpty()) 0f else reviews.map { it.rating }.average().toFloat()
            StationRatingSummary(
                averageRating = avg,
                reviewCount = reviews.size
            )
        }

        val selectedStation = allStations.find { it.id == selectedId }
        val selectedReviews = if (selectedId != null) {
            allReviews.filter { it.stationId == selectedId }
        } else {
            emptyList()
        }
        val selectedPhotos = buildList {
            selectedStation?.photoUri?.takeIf { it.isNotBlank() }?.let { add(it) }
            selectedReviews.forEach { review ->
                review.photoUri?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.distinct()

        MapUiState(
            stations = filtered,
            filter = filter,
            selectedStation = selectedStation,
            userLocation = userLoc,
            isLocating = interaction.isLocating,
            activeRoute = interaction.activeRoute,
            isLoadingRoute = interaction.isLoadingRoute,
            nearestStation = nearest,
            nearestDistanceMeters = nearestDist,
            isPickingLocation = interaction.isPickingLocation,
            pickedLocation = interaction.pickedLocation,
            pickedAddress = interaction.pickedAddress,
            addressSearchResults = interaction.addressSearchResults,
            isSearchingAddress = interaction.isSearchingAddress,
            showAddStationSheet = interaction.showAddStationSheet,
            showStationListSheet = interaction.showStationListSheet,
            isDiscoveringOsm = interaction.isDiscoveringOsm,
            discoveryStatusMessage = interaction.discoveryStatusMessage,
            cameraTarget = interaction.cameraTarget,
            selectedStationReviews = selectedReviews,
            ratingSummaries = summaries,
            selectedStationPhotos = selectedPhotos,
            draftStationPhotoUri = interaction.draftStationPhotoUri,
            draftReviewPhotoUri = interaction.draftReviewPhotoUri
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

    fun toggleFavoritesFilter() = filterFlow.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun updateSearchQuery(query: String) = filterFlow.update { it.copy(searchQuery = query) }

    fun openStationListSheet() {
        interactionFlow.update { it.copy(showStationListSheet = true) }
    }

    fun dismissStationListSheet() {
        interactionFlow.update { it.copy(showStationListSheet = false) }
    }

    fun clearDiscoveryStatusMessage() {
        interactionFlow.update { it.copy(discoveryStatusMessage = null) }
    }

    fun discoverCurrentAreaOsmStations() {
        if (interactionFlow.value.isDiscoveringOsm) return
        val center = interactionFlow.value.pickedLocation
        viewModelScope.launch {
            interactionFlow.update {
                it.copy(
                    isDiscoveringOsm = true,
                    discoveryStatusMessage = "正在搜尋方圓 3 公里內 OpenStreetMap 飲水機..."
                )
            }
            val result = repository.discoverNearbyFromOsm(
                latitude = center.latitude,
                longitude = center.longitude,
                radiusMeters = 3000
            )
            val msg = result.fold(
                onSuccess = { count ->
                    if (count > 0) {
                        "成功從 OpenStreetMap 找到 $count 個周邊飲水機點位！"
                    } else {
                        "此區域 3 公里內暫無 OpenStreetMap 標記點位，歡迎成為第一位通報者！"
                    }
                },
                onFailure = {
                    "暫時無法連線至開放圖資伺服器，請稍後再試"
                }
            )
            interactionFlow.update {
                it.copy(
                    isDiscoveringOsm = false,
                    discoveryStatusMessage = msg
                )
            }
        }
    }

    fun toggleStationFavorite(station: WaterStation) {
        viewModelScope.launch {
            repository.toggleFavorite(station.id, !station.isFavorite)
        }
    }

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
        interactionFlow.update { it.copy(draftReviewPhotoUri = null) }
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

    fun openAddStationSheet() {
        selectedStationIdFlow.value = null
        val startPoint = interactionFlow.value.userLocation ?: interactionFlow.value.pickedLocation
        interactionFlow.update {
            it.copy(
                isPickingLocation = false,
                showAddStationSheet = true,
                pickedLocation = startPoint,
                addressSearchResults = emptyList(),
                activeRoute = null
            )
        }
        refreshPickedAddress(startPoint)
    }

    fun startPickingNewStationLocation() {
        selectedStationIdFlow.value = null
        val startPoint = interactionFlow.value.userLocation ?: interactionFlow.value.pickedLocation
        interactionFlow.update {
            it.copy(
                isPickingLocation = true,
                showAddStationSheet = false,
                pickedLocation = startPoint,
                addressSearchResults = emptyList(),
                activeRoute = null
            )
        }
        refreshPickedAddress(startPoint)
    }

    fun cancelPickingNewStationLocation() {
        interactionFlow.update {
            it.copy(
                isPickingLocation = false,
                showAddStationSheet = false,
                addressSearchResults = emptyList(),
                draftStationPhotoUri = null
            )
        }
    }

    fun onMapCameraIdle(center: GeoPoint) {
        interactionFlow.update { it.copy(pickedLocation = center) }
        if (interactionFlow.value.isPickingLocation) {
            refreshPickedAddress(center)
        }
    }

    private fun refreshPickedAddress(point: GeoPoint) {
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = viewModelScope.launch {
            val address = geocodingService.reverseGeocode(point)
            interactionFlow.update { it.copy(pickedAddress = address) }
        }
    }

    fun useCurrentLocationForNewStation() {
        viewModelScope.launch {
            interactionFlow.update { it.copy(isLocating = true) }
            val freshLocation = locationTracker.getCurrentLocation()
                ?: interactionFlow.value.userLocation
            if (freshLocation != null) {
                interactionFlow.update {
                    it.copy(
                        userLocation = freshLocation,
                        isLocating = false,
                        pickedLocation = freshLocation,
                        addressSearchResults = emptyList(),
                        cameraTarget = CameraTargetEvent(
                            point = freshLocation,
                            zoom = 17.0,
                            seq = ++cameraSeq
                        )
                    )
                }
                refreshPickedAddress(freshLocation)
            } else {
                interactionFlow.update { it.copy(isLocating = false) }
            }
        }
    }

    fun searchAddressForNewStation(query: String) {
        searchAddressJob?.cancel()
        if (query.isBlank()) {
            interactionFlow.update {
                it.copy(addressSearchResults = emptyList(), isSearchingAddress = false)
            }
            return
        }
        searchAddressJob = viewModelScope.launch {
            interactionFlow.update { it.copy(isSearchingAddress = true) }
            val results = geocodingService.searchAddress(query)
            interactionFlow.update {
                it.copy(
                    addressSearchResults = results,
                    isSearchingAddress = false
                )
            }
        }
    }

    fun selectAddressCandidate(candidate: AddressCandidate) {
        interactionFlow.update {
            it.copy(
                pickedLocation = candidate.point,
                pickedAddress = candidate.fullAddress,
                addressSearchResults = emptyList(),
                cameraTarget = CameraTargetEvent(
                    point = candidate.point,
                    zoom = 17.0,
                    seq = ++cameraSeq
                )
            )
        }
    }

    fun confirmPickedLocation() {
        interactionFlow.update {
            it.copy(
                isPickingLocation = false,
                showAddStationSheet = true,
                addressSearchResults = emptyList()
            )
        }
    }

    fun dismissAddStationSheet() {
        interactionFlow.update {
            it.copy(
                showAddStationSheet = false,
                addressSearchResults = emptyList(),
                draftStationPhotoUri = null
            )
        }
    }

    fun onDraftStationPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val savedUri = photoStorageHelper.savePhotoFromUri(uri) ?: return@launch
            interactionFlow.update { it.copy(draftStationPhotoUri = savedUri) }
        }
    }

    fun onDraftStationCameraBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val savedUri = photoStorageHelper.savePhotoFromBitmap(bitmap) ?: return@launch
            interactionFlow.update { it.copy(draftStationPhotoUri = savedUri) }
        }
    }

    fun clearDraftStationPhoto() {
        interactionFlow.update { it.copy(draftStationPhotoUri = null) }
    }

    fun onDraftReviewPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val savedUri = photoStorageHelper.savePhotoFromUri(uri) ?: return@launch
            interactionFlow.update { it.copy(draftReviewPhotoUri = savedUri) }
        }
    }

    fun onDraftReviewCameraBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val savedUri = photoStorageHelper.savePhotoFromBitmap(bitmap) ?: return@launch
            interactionFlow.update { it.copy(draftReviewPhotoUri = savedUri) }
        }
    }

    fun clearDraftReviewPhoto() {
        interactionFlow.update { it.copy(draftReviewPhotoUri = null) }
    }

    fun addStationReview(
        stationId: String,
        authorName: String,
        rating: Int,
        comment: String,
        tags: List<String>
    ) {
        val photoUri = interactionFlow.value.draftReviewPhotoUri
        val review = StationReview(
            id = "review-${UUID.randomUUID()}",
            stationId = stationId,
            authorName = authorName.trim().ifEmpty { "租屋拎水客" },
            rating = rating.coerceIn(1, 5),
            comment = comment.trim(),
            tags = tags,
            photoUri = photoUri
        )
        viewModelScope.launch {
            repository.addReview(review)
            interactionFlow.update { it.copy(draftReviewPhotoUri = null) }
        }
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
        val stationPhotoUri = interactionFlow.value.draftStationPhotoUri
        val trimmedHint = floorHint.trim()
        val newStation = WaterStation(
            id = "user-${UUID.randomUUID()}",
            name = name.trim(),
            latitude = location.latitude,
            longitude = location.longitude,
            floorHint = trimmedHint.ifEmpty { "1F 飲水機" },
            accessType = accessType,
            hasHot = hasHot,
            hasWarm = hasWarm,
            hasCold = hasCold,
            hasIced = hasIced,
            is24Hours = is24Hours,
            openHours = if (is24Hours) "24 小時開放" else openHours.trim().ifEmpty { "依場館開放時間" },
            status = StationStatus.WORKING,
            photoUri = stationPhotoUri
        )
        viewModelScope.launch {
            repository.addStation(newStation)
            if (stationPhotoUri != null || trimmedHint.isNotEmpty()) {
                val initialReview = StationReview(
                    id = "review-${UUID.randomUUID()}",
                    stationId = newStation.id,
                    authorName = "首發通報者",
                    rating = 5,
                    comment = trimmedHint.ifEmpty { "新增取水點並附上現場照片" },
                    tags = listOf("位置好找"),
                    photoUri = stationPhotoUri
                )
                repository.addReview(initialReview)
            }
            interactionFlow.update {
                it.copy(
                    showAddStationSheet = false,
                    isPickingLocation = false,
                    draftStationPhotoUri = null,
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
