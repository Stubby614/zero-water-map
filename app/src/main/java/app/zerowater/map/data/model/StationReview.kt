package app.zerowater.map.data.model

import kotlinx.serialization.Serializable

val QUICK_REVIEW_TAGS = listOf(
    "無異味好喝",
    "出水量大",
    "2L大水壺好裝",
    "位置好找",
    "熱水夠燙",
    "冰水夠冰"
)

@Serializable
data class StationReview(
    val id: String,
    val stationId: String,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val tags: List<String> = emptyList(),
    val photoUri: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

data class StationRatingSummary(
    val averageRating: Float = 0f,
    val reviewCount: Int = 0
)
