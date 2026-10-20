package app.zerowater.map.ui.map

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerowater.map.data.location.DistanceFormatter
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import app.zerowater.map.data.route.RouteInfo
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
                        text = "請拖曳地圖，將紅針準星對準飲水機位置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "目前準星座標：%.5f, %.5f",
                            uiState.pickedLocation.latitude,
                            uiState.pickedLocation.longitude
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    onClick = viewModel::startPickingNewStationLocation,
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
                    onReportStatus = { status ->
                        viewModel.reportStatus(station.id, status)
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
                    onDismiss = viewModel::dismissAddStationSheet,
                    onSubmit = viewModel::submitNewStation
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
    onRequestLocation: () -> Unit,
    onNavigateClick: () -> Unit,
    onReportStatus: (StationStatus) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = station.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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
    }
}
