package app.zerowater.map.data.location

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AddressCandidate(
    val title: String,
    val fullAddress: String,
    val point: GeoPoint
)

@Serializable
private data class NominatimSearchItem(
    @SerialName("lat") val lat: String = "",
    @SerialName("lon") val lon: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("name") val name: String = ""
)

@Serializable
private data class NominatimReverseResponse(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("name") val name: String = ""
)

@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun searchAddress(query: String): List<AddressCandidate> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()

            // 1. Try OpenStreetMap Nominatim (great coverage for Taiwan addresses & landmarks)
            val nominatimResults = runCatching {
                val url = "https://nominatim.openstreetmap.org/search".toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addQueryParameter("q", trimmed)
                    ?.addQueryParameter("format", "jsonv2")
                    ?.addQueryParameter("countrycodes", "tw")
                    ?.addQueryParameter("limit", "5")
                    ?.addQueryParameter("accept-language", "zh-TW")
                    ?.build()
                    ?: return@runCatching emptyList()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ZeroWaterMap-Android/0.1 (https://github.com/Stubby614/zero-water-map)")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string() ?: return@use emptyList()
                    val items = json.decodeFromString<List<NominatimSearchItem>>(body)
                    items.mapNotNull { item ->
                        val lat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = item.lon.toDoubleOrNull() ?: return@mapNotNull null
                        val cleanAddr = item.displayName
                            .removeSuffix(", 臺灣")
                            .removeSuffix(", 台灣")
                        val shortTitle = item.name.ifBlank {
                            cleanAddr.substringBefore(",")
                        }
                        AddressCandidate(
                            title = shortTitle,
                            fullAddress = cleanAddr,
                            point = GeoPoint(lat, lon)
                        )
                    }
                }
            }.getOrDefault(emptyList())

            if (nominatimResults.isNotEmpty()) {
                return@withContext nominatimResults
            }

            // 2. Fallback to Android built-in Geocoder
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching emptyList()
                val geocoder = Geocoder(context, Locale.TAIWAN)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(trimmed, 5).orEmpty()
                addresses.mapNotNull { addr ->
                    if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapNotNull null
                    val line = (0..addr.maxAddressLineIndex).joinToString("") {
                        addr.getAddressLine(it)
                    }.ifBlank { trimmed }
                    AddressCandidate(
                        title = addr.featureName ?: line,
                        fullAddress = line,
                        point = GeoPoint(addr.latitude, addr.longitude)
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun reverseGeocode(point: GeoPoint): String? =
        withContext(Dispatchers.IO) {
            // 1. Try Android Geocoder first for fast local reverse lookup
            val localResult = runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                val geocoder = Geocoder(context, Locale.TAIWAN)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty()
                val first = addresses.firstOrNull() ?: return@runCatching null
                (0..first.maxAddressLineIndex).joinToString("") { first.getAddressLine(it) }
                    .takeIf { it.isNotBlank() }
            }.getOrNull()

            if (!localResult.isNullOrBlank()) {
                return@withContext localResult
            }

            // 2. Fallback to Nominatim reverse geocoding
            runCatching {
                val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addQueryParameter("lat", point.latitude.toString())
                    ?.addQueryParameter("lon", point.longitude.toString())
                    ?.addQueryParameter("format", "jsonv2")
                    ?.addQueryParameter("accept-language", "zh-TW")
                    ?.build()
                    ?: return@runCatching null

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ZeroWaterMap-Android/0.1 (https://github.com/Stubby614/zero-water-map)")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val parsed = json.decodeFromString<NominatimReverseResponse>(body)
                    parsed.displayName
                        .removeSuffix(", 臺灣")
                        .removeSuffix(", 台灣")
                        .takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }
}
