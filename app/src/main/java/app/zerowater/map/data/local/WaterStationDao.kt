package app.zerowater.map.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterStationDao {
    @Query("SELECT * FROM water_stations WHERE status != 'REMOVED'")
    fun observeAllActive(): Flow<List<WaterStationEntity>>

    @Query(
        """
        SELECT * FROM water_stations
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLng AND :maxLng
          AND status != 'REMOVED'
        """
    )
    fun observeInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<WaterStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<WaterStationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(stations: List<WaterStationEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: WaterStationEntity)

    @Query(
        """
        UPDATE water_stations
        SET status = :status, lastVerifiedEpochMillis = :verifiedAt
        WHERE id = :stationId
        """
    )
    suspend fun updateStatus(stationId: String, status: String, verifiedAt: Long)

    @Query("UPDATE water_stations SET isFavorite = :isFavorite WHERE id = :stationId")
    suspend fun updateFavorite(stationId: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM water_stations")
    suspend fun count(): Int
}
