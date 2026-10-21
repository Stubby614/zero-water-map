package app.zerowater.map.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WaterStationEntity::class, StationReviewEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ZeroWaterDatabase : RoomDatabase() {
    abstract fun waterStationDao(): WaterStationDao
    abstract fun stationReviewDao(): StationReviewDao
}
