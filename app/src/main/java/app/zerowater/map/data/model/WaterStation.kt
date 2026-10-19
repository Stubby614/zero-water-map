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
    val lastVerifiedEpochMillis: Long = System.currentTimeMillis()
)
