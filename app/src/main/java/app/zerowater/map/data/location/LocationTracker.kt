package app.zerowater.map.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun distanceMetersTo(targetLat: Double, targetLng: Double): Float = runCatching {
        val results = FloatArray(1)
        Location.distanceBetween(latitude, longitude, targetLat, targetLng, results)
        results[0]
    }.getOrDefault(0f)
}

object DistanceFormatter {
    private const val WALKING_METERS_PER_MINUTE = 75f

    fun formatDistance(meters: Float): String =
        if (meters < 1000f) {
            "${meters.roundToInt()} 公尺"
        } else {
            String.format("%.1f 公里", meters / 1000f)
        }

    fun formatWalkMinutes(meters: Float): Int =
        (meters / WALKING_METERS_PER_MINUTE).roundToInt().coerceAtLeast(1)

    fun formatSummary(meters: Float, durationSeconds: Float? = null): String {
        val distText = formatDistance(meters)
        val minutes = if (durationSeconds != null && durationSeconds > 0f) {
            (durationSeconds / 60f).roundToInt().coerceAtLeast(1)
        } else {
            formatWalkMinutes(meters)
        }
        return "$distText · 步行約 $minutes 分鐘"
    }
}

@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null

        val fusedPoint = suspendCancellableCoroutine<GeoPoint?> { cont ->
            val tokenSource = CancellationTokenSource()
            cont.invokeOnCancellation { tokenSource.cancel() }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30_000L)
                .setDurationMillis(5_000L)
                .build()

            fusedClient.getCurrentLocation(request, tokenSource.token)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        cont.resume(GeoPoint(location.latitude, location.longitude))
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last: Location? ->
                                cont.resume(last?.let { GeoPoint(it.latitude, it.longitude) })
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }

        if (fusedPoint != null) return fusedPoint

        // Fallback to standard Android LocationManager (useful on emulators without Play Services)
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@runCatching null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.firstNotNullOfOrNull { provider ->
                if (lm.isProviderEnabled(provider)) {
                    lm.getLastKnownLocation(provider)?.let { GeoPoint(it.latitude, it.longitude) }
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
