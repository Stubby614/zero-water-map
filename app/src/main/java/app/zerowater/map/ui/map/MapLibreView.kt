package app.zerowater.map.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.zerowater.map.BuildConfig
import app.zerowater.map.data.location.DistanceFormatter
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

@Composable
fun MapLibreView(
    stations: List<WaterStation>,
    selectedStationId: String?,
    userLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    cameraTarget: CameraTargetEvent?,
    onStationClick: (WaterStation) -> Unit,
    onCameraCenterChanged: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    initialCenter: LatLng = LatLng(25.0375, 121.5320),
    initialZoom: Double = 13.5
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastHandledCameraSeq = remember { mutableIntStateOf(0) }

    val iconFactory = remember(context) { IconFactory.getInstance(context) }
    val userDotIcon = remember(iconFactory) {
        iconFactory.fromBitmap(createCircleMarkerBitmap(fillColor = Color.parseColor("#1E88E5"), radiusPx = 22f, strokeWidthPx = 8f))
    }
    val workingStationIcon = remember(iconFactory) {
        iconFactory.fromBitmap(createCircleMarkerBitmap(fillColor = Color.parseColor("#00897B"), radiusPx = 26f, strokeWidthPx = 6f))
    }
    val selectedStationIcon = remember(iconFactory) {
        iconFactory.fromBitmap(createCircleMarkerBitmap(fillColor = Color.parseColor("#FB8C00"), radiusPx = 32f, strokeWidthPx = 8f))
    }
    val warningStationIcon = remember(iconFactory) {
        iconFactory.fromBitmap(createCircleMarkerBitmap(fillColor = Color.parseColor("#E53935"), radiusPx = 26f, strokeWidthPx = 6f))
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setStyle(BuildConfig.MAP_STYLE_URL)
                map.cameraPosition = CameraPosition.Builder()
                    .target(initialCenter)
                    .zoom(initialZoom)
                    .build()
                map.addOnCameraIdleListener {
                    val target = map.cameraPosition.target
                    if (target != null) {
                        onCameraCenterChanged(GeoPoint(target.latitude, target.longitude))
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                map.clear()

                // Draw walking route polyline if active
                if (routePoints.size >= 2) {
                    val latLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(latLngs)
                            .color(Color.parseColor("#0277BD"))
                            .width(6f)
                    )
                }

                // Draw user location marker
                if (userLocation != null) {
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(userLocation.latitude, userLocation.longitude))
                            .title("你的目前位置")
                            .icon(userDotIcon)
                    )
                }

                // Draw water stations
                val markerToStation = mutableMapOf<Long, WaterStation>()
                stations.forEach { station ->
                    val icon = when {
                        station.id == selectedStationId -> selectedStationIcon
                        station.status != StationStatus.WORKING -> warningStationIcon
                        else -> workingStationIcon
                    }
                    val distPrefix = userLocation?.let {
                        val meters = it.distanceMetersTo(station.latitude, station.longitude)
                        "[${DistanceFormatter.formatDistance(meters)}] "
                    }.orEmpty()

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(station.latitude, station.longitude))
                            .title(station.name)
                            .snippet("$distPrefix${station.floorHint}")
                            .icon(icon)
                    )
                    markerToStation[marker.id] = station
                }

                map.setOnMarkerClickListener { marker ->
                    val clickedStation = markerToStation[marker.id]
                    if (clickedStation != null) {
                        onStationClick(clickedStation)
                        true
                    } else {
                        false
                    }
                }

                // Animate camera if a new target event arrived
                if (cameraTarget != null && cameraTarget.seq > lastHandledCameraSeq.intValue) {
                    lastHandledCameraSeq.intValue = cameraTarget.seq
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(cameraTarget.point.latitude, cameraTarget.point.longitude),
                            cameraTarget.zoom
                        ),
                        750
                    )
                }
            }
        }
    )
}

private fun createCircleMarkerBitmap(
    fillColor: Int,
    radiusPx: Float,
    strokeWidthPx: Float
): Bitmap {
    val size = ((radiusPx + strokeWidthPx) * 2f).toInt().coerceAtLeast(16)
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(4f, 0f, 2f, Color.argb(80, 0, 0, 0))
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    canvas.drawCircle(center, center, radiusPx + strokeWidthPx / 2f, strokePaint)
    canvas.drawCircle(center, center, radiusPx, fillPaint)
    return bitmap
}
