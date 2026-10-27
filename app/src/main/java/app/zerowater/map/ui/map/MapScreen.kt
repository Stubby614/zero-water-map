package app.zerowater.map.ui.map

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerowater.map.data.location.DistanceFormatter
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.model.QUICK_REVIEW_TAGS
import app.zerowater.map.data.model.StationRatingSummary
import app.zerowater.map.data.model.StationReview
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import app.zerowater.map.data.route.RouteInfo
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.refreshUserLocation(moveCamera = true)
        }
    }
    val stationGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onDraftStationPhotoPicked(uri)
        }
    }

    val stationCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.onDraftStationCameraBitmap(bitmap)
        }
    }

    val reviewGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onDraftReviewPhotoPicked(uri)
        }
    }

    val reviewCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.onDraftReviewCameraBitmap(bitmap)
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreView(
            stations = uiState.stations,
            selectedStationId = uiState.selectedStation?.id,
            userLocation = uiState.userLocation,
            routePoints = uiState.activeRoute?.points.orEmpty(),
            cameraTarget = uiState.cameraTarget,
            onStationClick = { viewModel.selectStation(it.id, moveCamera = false) },
            onCameraCenterChanged = viewModel::onMapCameraIdle,
            markerColorConfig = uiState.markerColorConfig,
            modifier = Modifier.fillMaxSize()
        )

        // Top Filter Bar & Nearest Station Banner
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "拎水地圖 (${uiState.stations.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = uiState.filter.favoritesOnly,
                        onClick = viewModel::toggleFavoritesFilter,
                        label = { Text("★ 已收藏") }
                    )
                    FilterChip(
                        selected = uiState.filter.noCardOnly,
                        onClick = viewModel::toggleNoCardFilter,
                        label = { Text("免門禁/免證件") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                    FilterChip(
                        selected = uiState.filter.requireIced,
                        onClick = viewModel::toggleIcedFilter,
                        label = { Text("有冰水") }
                    )
                    FilterChip(
                        selected = uiState.filter.requireHot,
                        onClick = viewModel::toggleHotFilter,
                        label = { Text("有熱水 (泡麵)") }
                    )
                    FilterChip(
                        selected = uiState.filter.open24HoursOnly,
                        onClick = viewModel::toggle24HoursFilter,
                        label = { Text("24H") }
                    )
                }
            }
            // Temperature Color Legend Capsule (clickable -> opens MarkerColorSettingsSheet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.clickable { viewModel.openColorSettingsSheet() },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 5.dp,
                    shadowElevation = 5.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(uiState.markerColorConfig.icedColorHex)))
                                    .border(1.dp, Color.White, CircleShape)
                            )
                            Text("冰溫熱", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(uiState.markerColorConfig.warmHotColorHex)))
                                    .border(1.dp, Color.White, CircleShape)
                            )
                            Text("溫熱水", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(uiState.markerColorConfig.hotOnlyColorHex)))
                                    .border(1.dp, Color.White, CircleShape)
                            )
                            Text("熱水/單溫", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "自訂圖釘顏色",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }


            // Explore nearby real OSM water stations pill & status banner
            if (!uiState.isPickingLocation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.clickable(enabled = !uiState.isDiscoveringOsm) {
                            viewModel.discoverCurrentAreaOsmStations()
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isDiscoveringOsm) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = if (uiState.isDiscoveringOsm) {
                                    "正在探索周邊飲水機..."
                                } else {
                                    "探索此區域真實飲水機 (OSM)"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            uiState.discoveryStatusMessage?.let { statusMsg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearDiscoveryStatusMessage() },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉提示",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            // Nearest station quick-jump banner when user location is available
            val nearest = uiState.nearestStation
            val nearestDist = uiState.nearestDistanceMeters
            if (!uiState.isPickingLocation && nearest != null && nearestDist != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectStation(nearest.id, moveCamera = true) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column {
                                Text(
                                    text = "離你最近：${nearest.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${DistanceFormatter.formatSummary(nearestDist)}（點擊查看路線）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Center Crosshair & Confirmation Bar during Location Picking Mode
        if (uiState.isPickingLocation) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "選點準星",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-18).dp)
                    .size(44.dp)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "拖曳地圖將紅針準星對準飲水機，或直接用目前定位",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!uiState.pickedAddress.isNullOrBlank()) {
                        Text(
                            text = "地址：${uiState.pickedAddress}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "準星座標：%.5f, %.5f",
                            uiState.pickedLocation.latitude,
                            uiState.pickedLocation.longitude
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            viewModel.useCurrentLocationForNewStation()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(" 將準星移到我的目前定位")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::cancelPickingNewStationLocation,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = viewModel::confirmPickedLocation,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("確認此位置")
                        }
                    }
                }
            }
        } else {
            // Floating Action Buttons (My Location + Add New Water Station)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    if (uiState.isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "定位目前位置"
                        )
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = viewModel::openStationListSheet,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null
                        )
                    },
                    text = { Text("附近清單 (${uiState.stations.size})") }
                )

                ExtendedFloatingActionButton(
                    onClick = viewModel::openAddStationSheet,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AddLocationAlt,
                            contentDescription = null
                        )
                    },
                    text = { Text("通報取水點") }
                )
            }
        }

        // Station Details BottomSheet
        uiState.selectedStation?.let { station ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectStation(null) }
            ) {
                StationDetailSheet(
                    station = station,
                    userLocation = uiState.userLocation,
                    routeInfo = uiState.activeRoute,
                    isLoadingRoute = uiState.isLoadingRoute,
                    ratingSummary = uiState.ratingSummaries[station.id],
                    reviews = uiState.selectedStationReviews,
                    photos = uiState.selectedStationPhotos,
                    draftReviewPhotoUri = uiState.draftReviewPhotoUri,
                    onPickReviewPhoto = {
                        reviewGalleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onTakeReviewPhoto = {
                        reviewCameraLauncher.launch(null)
                    },
                    onClearReviewPhoto = viewModel::clearDraftReviewPhoto,
                    onSubmitReview = { authorName, rating, comment, tags ->
                        viewModel.addStationReview(
                            stationId = station.id,
                            authorName = authorName,
                            rating = rating,
                            comment = comment,
                            tags = tags
                        )
                    },
                    onRequestLocation = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onNavigateClick = {
                        val uri = Uri.parse(
                            "google.navigation:q=${station.latitude},${station.longitude}&mode=w"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=walking")
                                )
                            )
                        }
                    },
                    onToggleFavorite = {
                        viewModel.toggleStationFavorite(station)
                    },
                    onReportStatus = { status ->
                        viewModel.reportStatus(station.id, status)
                    }
                )
            }
        }

        // Nearby & Favorites List BottomSheet
        if (uiState.showStationListSheet) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissStationListSheet
            ) {
                StationListSheet(
                    stations = uiState.stations,
                    userLocation = uiState.userLocation,
                    filter = uiState.filter,
                    ratingSummaries = uiState.ratingSummaries,
                    isDiscoveringOsm = uiState.isDiscoveringOsm,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onToggleFavoritesFilter = viewModel::toggleFavoritesFilter,
                    onToggleNoCardFilter = viewModel::toggleNoCardFilter,
                    onToggleIcedFilter = viewModel::toggleIcedFilter,
                    onToggleHotFilter = viewModel::toggleHotFilter,
                    onDiscoverOsmClick = viewModel::discoverCurrentAreaOsmStations,
                    onToggleStationFavorite = viewModel::toggleStationFavorite,
                    onSelectStation = { clicked ->
                        viewModel.dismissStationListSheet()
                        viewModel.selectStation(clicked.id, moveCamera = true)
                    }
                )
            }
        }
        // Add New Station Form BottomSheet
        if (uiState.showAddStationSheet) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissAddStationSheet
            ) {
                AddStationSheet(
                    pickedLocation = uiState.pickedLocation,
                    pickedAddress = uiState.pickedAddress,
                    isLocating = uiState.isLocating,
                    isSearchingAddress = uiState.isSearchingAddress,
                    addressSearchResults = uiState.addressSearchResults,
                    draftPhotoUri = uiState.draftStationPhotoUri,
                    onPickPhotoFromGallery = {
                        stationGalleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onTakePhotoWithCamera = {
                        stationCameraLauncher.launch(null)
                    },
                    onRemoveDraftPhoto = viewModel::clearDraftStationPhoto,
                    onUseCurrentLocation = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        viewModel.useCurrentLocationForNewStation()
                    },
                    onSearchAddress = viewModel::searchAddressForNewStation,
                    onSelectAddressCandidate = viewModel::selectAddressCandidate,
                    onPickOnMap = viewModel::startPickingNewStationLocation,
                    onDismiss = viewModel::dismissAddStationSheet,
                    onSubmit = viewModel::submitNewStation
                )
            }
        }
        // Marker Color Settings BottomSheet
        if (uiState.showColorSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissColorSettingsSheet
            ) {
                MarkerColorSettingsSheet(
                    config = uiState.markerColorConfig,
                    onSelectColor = viewModel::updateMarkerColor,
                    onResetDefaults = viewModel::resetMarkerColors,
                    onDismiss = viewModel::dismissColorSettingsSheet
                )
            }
        }
    }
}

@Composable
private fun StationDetailSheet(
    station: WaterStation,
    userLocation: GeoPoint?,
    routeInfo: RouteInfo?,
    isLoadingRoute: Boolean,
    ratingSummary: StationRatingSummary?,
    reviews: List<StationReview>,
    photos: List<String>,
    draftReviewPhotoUri: String?,
    onPickReviewPhoto: () -> Unit,
    onTakeReviewPhoto: () -> Unit,
    onClearReviewPhoto: () -> Unit,
    onSubmitReview: (authorName: String, rating: Int, comment: String, tags: List<String>) -> Unit,
    onRequestLocation: () -> Unit,
    onNavigateClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReportStatus: (StationStatus) -> Unit
) {
    var showReviewForm by remember(station.id) { mutableStateOf(false) }
    var authorName by remember(station.id) { mutableStateOf("") }
    var rating by remember(station.id) { mutableIntStateOf(5) }
    var selectedTags by remember(station.id) { mutableStateOf(setOf<String>()) }
    var comment by remember(station.id) { mutableStateOf("") }
    var enlargedPhotoUri by remember { mutableStateOf<String?>(null) }

    if (enlargedPhotoUri != null) {
        Dialog(
            onDismissRequest = { enlargedPhotoUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { enlargedPhotoUri = null }
            ) {
                AsyncImage(
                    model = enlargedPhotoUri,
                    contentDescription = "放大檢視現場照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                IconButton(
                    onClick = { enlargedPhotoUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "關閉照片預覽"
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (station.isFavorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = "收藏此取水點",
                    tint = if (station.isFavorite) {
                        Color(0xFFE53935)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (ratingSummary != null && ratingSummary.reviewCount > 0) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = String.format(
                        Locale.TAIWAN,
                        "%.1f (%d 則評價)",
                        ratingSummary.averageRating,
                        ratingSummary.reviewCount
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "尚無評價 · 成為第一個回報的人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Distance & Walking Route Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    userLocation == null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "尚未取得你的位置，無法計算距離",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(onClick = onRequestLocation) {
                                Text("開啟定位")
                            }
                        }
                    }
                    isLoadingRoute -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            val directDist = userLocation.distanceMetersTo(station.latitude, station.longitude)
                            Text(
                                text = "直線 ${DistanceFormatter.formatDistance(directDist)} · 正在規劃步行路線...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    routeInfo != null -> {
                        Text(
                            text = DistanceFormatter.formatSummary(
                                routeInfo.distanceMeters,
                                routeInfo.durationSeconds
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (routeInfo.isActualRoadRoute) {
                                "已在地圖上繪製最佳步行路線（藍線）"
                            } else {
                                "已在地圖上繪製直線方向（離線估算）"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    else -> {
                        val directDist = userLocation.distanceMetersTo(station.latitude, station.longitude)
                        Text(
                            text = DistanceFormatter.formatSummary(directDist),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(station.accessType.label) })
            AssistChip(onClick = {}, label = { Text(station.status.label) })
        }

        Text(
            text = "位置提示：${station.floorHint}",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "開放時間：${station.openHours}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val temps = buildList {
            if (station.hasHot) add("熱水")
            if (station.hasWarm) add("溫水")
            if (station.hasCold) add("冷水")
            if (station.hasIced) add("冰水")
        }.joinToString(" / ")

        Text(
            text = "供應水溫：${temps.ifEmpty { "未標註" }}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onNavigateClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("開啟 Google Maps 步行導航")
        }

        Text(
            text = "回報目前飲水機狀態：",
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onReportStatus(StationStatus.WORKING) },
                modifier = Modifier.weight(1f)
            ) {
                Text("正常出水")
            }
            OutlinedButton(
                onClick = { onReportStatus(StationStatus.FILTER_EXPIRED) },
                modifier = Modifier.weight(1f)
            ) {
                Text("濾心紅燈")
            }
            OutlinedButton(
                onClick = { onReportStatus(StationStatus.OUT_OF_ORDER) },
                modifier = Modifier.weight(1f)
            ) {
                Text("故障")
            }
        }

        if (photos.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "📷 現場位置與飲水機照片 (${photos.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(photos) { photoUri ->
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "取水點現場照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(140.dp)
                            .height(105.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { enlargedPhotoUri = photoUri }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💬 拎水客評價與位置回報 (${reviews.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { showReviewForm = !showReviewForm }) {
                Icon(
                    imageVector = Icons.Default.RateReview,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (showReviewForm) " 收起表單" else " ✍️ 寫評論／補拍位置圖",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (showReviewForm) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = authorName,
                        onValueChange = { authorName = it },
                        label = { Text("你的暱稱（選填）") },
                        placeholder = { Text("租屋拎水客") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "評分：",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = { rating = star },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$star 星",
                                    tint = if (star <= rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "快速標籤（可複選）：",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_REVIEW_TAGS.forEach { tag ->
                            val isSelected = tag in selectedTags
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("位置指引與水質心得") },
                        placeholder = { Text("例：走進大門左轉到底茶水間，出水很快、熱水夠燙！") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTakeReviewPhoto,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(" 📷 拍照標示位置", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onPickReviewPhoto,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(" 🖼️ 相簿選圖", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (draftReviewPhotoUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = draftReviewPhotoUri,
                                contentDescription = "評論附圖預覽",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = onClearReviewPhoto,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除照片",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val finalComment = comment.trim().ifEmpty {
                                if (selectedTags.isNotEmpty()) {
                                    selectedTags.joinToString("、")
                                } else {
                                    "已完成現場取水回報"
                                }
                            }
                            onSubmitReview(
                                authorName,
                                rating,
                                finalComment,
                                selectedTags.toList()
                            )
                            comment = ""
                            selectedTags = emptySet()
                            rating = 5
                            showReviewForm = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("送出評價")
                    }
                }
            }
        }

        if (reviews.isEmpty()) {
            Text(
                text = "目前還沒有人留下詳細評價，歡迎分享飲水機確切位置與水質心得！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val dateFormatter = remember {
                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN)
            }
            reviews.forEach { review ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = review.authorName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormatter.format(Date(review.createdAtEpochMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "★".repeat(review.rating.coerceIn(1, 5)) +
                                "☆".repeat((5 - review.rating).coerceIn(0, 4)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB300)
                        )

                        if (review.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                review.tags.forEach { tag ->
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (review.comment.isNotBlank()) {
                            Text(
                                text = review.comment,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (!review.photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = review.photoUri,
                                contentDescription = "評論附圖",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .width(160.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { enlargedPhotoUri = review.photoUri }
                            )
                        }
                    }
                }
            }
        }
    }
}
