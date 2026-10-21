package app.zerowater.map.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StationReviewDao {
    @Query("SELECT * FROM station_reviews WHERE stationId = :stationId ORDER BY createdAtEpochMillis DESC")
    fun observeReviewsForStation(stationId: String): Flow<List<StationReviewEntity>>

    @Query("SELECT * FROM station_reviews ORDER BY createdAtEpochMillis DESC")
    fun observeAllReviews(): Flow<List<StationReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: StationReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<StationReviewEntity>)

    @Query("SELECT COUNT(*) FROM station_reviews")
    suspend fun count(): Int
}
