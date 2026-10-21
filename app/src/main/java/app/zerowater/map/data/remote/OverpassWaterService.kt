package app.zerowater.map.data.remote

import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@Serializable
private data class OverpassElement(
    val type: String = "",
    val id: Long = 0L,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap()
)

@Serializable
private data class OverpassCenter(
    val lat: Double,
    val lon: Double
)

@Singleton
class OverpassWaterService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    suspend fun fetchNearbyDrinkingWater(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 3000
    ): Result<List<WaterStation>> = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:15];
            (
              node["amenity"="drinking_water"](around:$radiusMeters,$latitude,$longitude);
              node["drinking_water"="yes"](around:$radiusMeters,$latitude,$longitude);
              way["amenity"="drinking_water"](around:$radiusMeters,$latitude,$longitude);
            );
            out center 80;
        """.trimIndent()

        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            val result = runCatching {
                val formBody = FormBody.Builder()
                    .add("data", query)
                    .build()
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "ZeroWaterMap-Android/0.1 (https://github.com/Stubby614/zero-water-map)")
                    .post(formBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Overpass HTTP ${response.code}")
                    }
                    val body = response.body?.string() ?: error("Empty Overpass response")
                    val parsed = json.decodeFromString<OverpassResponse>(body)
                    parsed.elements.mapNotNull { it.toWaterStationOrNull() }
                }
            }
            if (result.isSuccess) {
                return@withContext result
            } else {
                lastError = result.exceptionOrNull()
            }
        }
        Result.failure(lastError ?: IllegalStateException("無法連線至 OpenStreetMap Overpass 伺服器"))
    }

    private fun OverpassElement.toWaterStationOrNull(): WaterStation? {
        val latitude = lat ?: center?.lat ?: return null
        val longitude = lon ?: center?.lon ?: return null

        val rawName = tags["name:zh"]
            ?: tags["name"]
            ?: tags["operator"]?.let { "$it 公共飲水機" }
            ?: "OpenStreetMap 公共飲水機 (#${id % 10000})"

        val levelHint = tags["level"]?.let { lvl ->
            if (lvl.startsWith("-")) "B${lvl.removePrefix("-")}樓" else "${lvl}樓"
        }
        val descHint = tags["description"] ?: tags["note"] ?: tags["indoor"]?.let {
            if (it == "yes") "室內公共飲水機" else null
        }
        val floorHint = listOfNotNull(levelHint, descHint, "來源：OpenStreetMap 開放圖資")
            .joinToString(" · ")

        val accessTag = tags["access"]?.lowercase()
        val accessType = when (accessTag) {
            "private", "customers", "members", "students" -> AccessType.RESTRICTED
            "permissive" -> AccessType.SEMI_PUBLIC
            else -> if (tags["indoor"] == "yes") AccessType.SEMI_PUBLIC else AccessType.PUBLIC
        }

        val hasHot = tags["hot_water"] == "yes" || tags["temperature"]?.contains("hot") == true || true
        val hasWarm = true
        val hasCold = tags["cold_water"] != "no"
        val hasIced = tags["iced_water"] == "yes" || tags["cold_water"] == "yes"
        val openHours = tags["opening_hours"] ?: if (accessType == AccessType.PUBLIC) "24 小時開放" else "依場館開放時間"
        val is24Hours = openHours.contains("24/7") || openHours.contains("24 小時")

        return WaterStation(
            id = "osm-$type-$id",
            name = rawName,
            latitude = latitude,
            longitude = longitude,
            floorHint = floorHint,
            accessType = accessType,
            hasHot = hasHot,
            hasWarm = hasWarm,
            hasCold = hasCold,
            hasIced = hasIced,
            is24Hours = is24Hours,
            openHours = openHours,
            status = StationStatus.WORKING
        )
    }
}
