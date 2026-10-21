package app.zerowater.map.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation

@Entity(
    tableName = "water_stations",
    indices = [
        Index(value = ["latitude", "longitude"]),
        Index(value = ["accessType"]),
        Index(value = ["status"])
    ]
)
data class WaterStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val floorHint: String,
    val accessType: String,
    val hasHot: Boolean,
    val hasWarm: Boolean,
    val hasCold: Boolean,
    val hasIced: Boolean,
    val is24Hours: Boolean,
    val openHours: String,
    val status: String,
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val lastVerifiedEpochMillis: Long
) {
    fun toDomain(): WaterStation = WaterStation(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        floorHint = floorHint,
        accessType = runCatching { AccessType.valueOf(accessType) }.getOrDefault(AccessType.PUBLIC),
        hasHot = hasHot,
        hasWarm = hasWarm,
        hasCold = hasCold,
        hasIced = hasIced,
        is24Hours = is24Hours,
        openHours = openHours,
        status = runCatching { StationStatus.valueOf(status) }.getOrDefault(StationStatus.WORKING),
        photoUri = photoUri,
        isFavorite = isFavorite,
        lastVerifiedEpochMillis = lastVerifiedEpochMillis
    )

    companion object {
        fun fromDomain(station: WaterStation): WaterStationEntity = WaterStationEntity(
            id = station.id,
            name = station.name,
            latitude = station.latitude,
            longitude = station.longitude,
            floorHint = station.floorHint,
            accessType = station.accessType.name,
            hasHot = station.hasHot,
            hasWarm = station.hasWarm,
            hasCold = station.hasCold,
            hasIced = station.hasIced,
            is24Hours = station.is24Hours,
            openHours = station.openHours,
            status = station.status.name,
            photoUri = station.photoUri,
            isFavorite = station.isFavorite,
            lastVerifiedEpochMillis = station.lastVerifiedEpochMillis
        )
    }
}
