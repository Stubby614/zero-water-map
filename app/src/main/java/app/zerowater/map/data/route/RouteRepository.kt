package app.zerowater.map.data.route

import app.zerowater.map.data.location.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class RouteInfo(
    val points: List<GeoPoint>,
    val distanceMeters: Float,
    val durationSeconds: Float,
    val isActualRoadRoute: Boolean
)

@Serializable
private data class OsrmResponse(
    val code: String = "",
    val routes: List<OsrmRoute> = emptyList()
)

@Serializable
private data class OsrmRoute(
    val distance: Float = 0f,
    val duration: Float = 0f,
    val geometry: OsrmGeometry? = null
)

@Serializable
private data class OsrmGeometry(
    @SerialName("coordinates")
    val coordinates: List<List<Double>> = emptyList()
)

@Singleton
class RouteRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun calculateWalkingRoute(start: GeoPoint, end: GeoPoint): RouteInfo =
        withContext(Dispatchers.IO) {
            val straightDistance = start.distanceMetersTo(end.latitude, end.longitude)
            val fallbackRoute = RouteInfo(
                points = listOf(start, end),
                distanceMeters = straightDistance,
                durationSeconds = straightDistance / 1.25f,
                isActualRoadRoute = false
            )

            runCatching {
                val url = "https://router.project-osrm.org/route/v1/foot/" +
                    "${start.longitude},${start.latitude};${end.longitude},${end.latitude}" +
                    "?overview=full&geometries=geojson"
                val request = Request.Builder().url(url).get().build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext fallbackRoute
                    val body = response.body?.string() ?: return@withContext fallbackRoute
                    val parsed = json.decodeFromString<OsrmResponse>(body)
                    val firstRoute = parsed.routes.firstOrNull() ?: return@withContext fallbackRoute
                    val coords = firstRoute.geometry?.coordinates.orEmpty()
                    if (coords.size < 2) return@withContext fallbackRoute

                    val routePoints = coords.mapNotNull { pair ->
                        if (pair.size >= 2) {
                            GeoPoint(latitude = pair[1], longitude = pair[0])
                        } else {
                            null
                        }
                    }
                    if (routePoints.size < 2) return@withContext fallbackRoute

                    RouteInfo(
                        points = routePoints,
                        distanceMeters = firstRoute.distance.takeIf { it > 0f } ?: straightDistance,
                        durationSeconds = firstRoute.duration.takeIf { it > 0f } ?: (straightDistance / 1.25f),
                        isActualRoadRoute = true
                    )
                }
            }.getOrDefault(fallbackRoute)
        }
}
