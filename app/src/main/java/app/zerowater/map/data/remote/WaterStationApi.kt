package app.zerowater.map.data.remote

import app.zerowater.map.data.model.StationReview
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

@Serializable
data class StatusReportRequest(
    val status: StationStatus
)

@Serializable
data class OpenWaterDatasetDto(
    val version: Int = 1,
    val updatedAt: String = "",
    val license: String = "CC-BY-4.0",
    val stations: List<WaterStation> = emptyList(),
    val reviews: List<StationReview> = emptyList()
)

interface WaterStationApi {
    @GET
    suspend fun fetchOpenDataSnapshot(
        @Url url: String
    ): OpenWaterDatasetDto
    @GET("stations")
    suspend fun getNearbyStations(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radius_m") radiusMeters: Int = 2000
    ): List<WaterStation>

    @POST("stations")
    suspend fun createStation(
        @Body station: WaterStation
    ): WaterStation

    @POST("stations/{id}/report")
    suspend fun reportStatus(
        @Path("id") stationId: String,
        @Body request: StatusReportRequest
    ): WaterStation

    @GET("stations/{id}/reviews")
    suspend fun getStationReviews(@Path("id") stationId: String): List<StationReview>

    @POST("stations/{id}/reviews")
    suspend fun createStationReview(
        @Path("id") stationId: String,
        @Body review: StationReview
    ): StationReview
}
