package app.zerowater.map.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.zerowater.map.data.model.StationReview

@Entity(
    tableName = "station_reviews",
    indices = [
        Index(value = ["stationId"]),
        Index(value = ["createdAtEpochMillis"])
    ]
)
data class StationReviewEntity(
    @PrimaryKey val id: String,
    val stationId: String,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val tagsPipeSeparated: String,
    val photoUri: String?,
    val createdAtEpochMillis: Long
) {
    fun toDomain(): StationReview = StationReview(
        id = id,
        stationId = stationId,
        authorName = authorName,
        rating = rating,
        comment = comment,
        tags = tagsPipeSeparated.split("|").filter { it.isNotBlank() },
        photoUri = photoUri,
        createdAtEpochMillis = createdAtEpochMillis
    )

    companion object {
        fun fromDomain(review: StationReview): StationReviewEntity = StationReviewEntity(
            id = review.id,
            stationId = review.stationId,
            authorName = review.authorName,
            rating = review.rating,
            comment = review.comment,
            tagsPipeSeparated = review.tags.joinToString("|"),
            photoUri = review.photoUri,
            createdAtEpochMillis = review.createdAtEpochMillis
        )
    }
}
