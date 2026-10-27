package app.zerowater.map.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccessType(val label: String) {
    @SerialName("PUBLIC")
    PUBLIC("完全開放"),

    @SerialName("SEMI_PUBLIC")
    SEMI_PUBLIC("半開放場館 (圖書館/運動中心/捷運)"),

    @SerialName("RESTRICTED")
    RESTRICTED("需門禁/證件")
}

@Serializable
enum class StationStatus(val label: String) {
    @SerialName("WORKING")
    WORKING("正常出水"),

    @SerialName("OUT_OF_ORDER")
    OUT_OF_ORDER("故障維修中"),

    @SerialName("FILTER_EXPIRED")
    FILTER_EXPIRED("濾心亮紅燈"),

    @SerialName("REMOVED")
    REMOVED("已撤除")
}

@Serializable
data class WaterStation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val floorHint: String,
    val accessType: AccessType,
    val hasHot: Boolean,
    val hasWarm: Boolean,
    val hasCold: Boolean,
    val hasIced: Boolean,
    val is24Hours: Boolean = false,
    val openHours: String = "依場館開放時間",
    val status: StationStatus = StationStatus.WORKING,
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val lastVerifiedEpochMillis: Long = System.currentTimeMillis()
)

@Serializable
enum class TemperatureCategory(
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val defaultColorHex: String
) {
    @SerialName("ICED_TRIPLE")
    ICED_TRIPLE(
        title = "含冰水（冰／溫／熱）",
        shortLabel = "冰溫熱",
        subtitle = "有冰水（通常同時具備冰水、溫水與熱水）",
        defaultColorHex = "#1E88E5"
    ),

    @SerialName("WARM_HOT")
    WARM_HOT(
        title = "溫熱水（溫水＋熱水）",
        shortLabel = "溫熱水",
        subtitle = "無冰水，提供溫水與熱水為主",
        defaultColorHex = "#FDD835"
    ),

    @SerialName("HOT_OR_SINGLE")
    HOT_OR_SINGLE(
        title = "純熱水／單溫",
        shortLabel = "熱水/單溫",
        subtitle = "以熱水或單一常溫水為主",
        defaultColorHex = "#E53935"
    )
}

val WaterStation.temperatureCategory: TemperatureCategory
    get() = when {
        hasIced -> TemperatureCategory.ICED_TRIPLE
        hasWarm && hasHot -> TemperatureCategory.WARM_HOT
        else -> TemperatureCategory.HOT_OR_SINGLE
    }
