package app.zerowater.map.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import app.zerowater.map.BuildConfig
import app.zerowater.map.data.model.WaterStation
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

@Composable
fun MapLibreView(
    stations: List<WaterStation>,
    onStationClick: (WaterStation) -> Unit,
    modifier: Modifier = Modifier,
    initialCenter: LatLng = LatLng(25.0375, 121.5320),
    initialZoom: Double = 13.0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setStyle(BuildConfig.MAP_STYLE_URL)
                map.cameraPosition = CameraPosition.Builder()
                    .target(initialCenter)
                    .zoom(initialZoom)
                    .build()
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
                val markerToStation = mutableMapOf<Long, WaterStation>()
                stations.forEach { station ->
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(station.latitude, station.longitude))
                            .title(station.name)
                            .snippet(station.floorHint)
                    )
                    markerToStation[marker.id] = station
                }
                map.setOnMarkerClickListener { marker ->
                    markerToStation[marker.id]?.let(onStationClick)
                    true
                }
            }
        }
    )
}
