package app.zerowater.map.data.repository

import android.content.Context
import app.zerowater.map.BuildConfig
import app.zerowater.map.data.local.StationReviewDao
import app.zerowater.map.data.local.StationReviewEntity
import app.zerowater.map.data.local.WaterStationDao
import app.zerowater.map.data.local.WaterStationEntity
import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationReview
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import app.zerowater.map.data.remote.OpenWaterDatasetDto
import app.zerowater.map.data.remote.OverpassWaterService
import app.zerowater.map.data.remote.StatusReportRequest
import app.zerowater.map.data.remote.WaterStationApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterStationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val dao: WaterStationDao,
    private val reviewDao: StationReviewDao,
    private val api: WaterStationApi,
    private val overpassWaterService: OverpassWaterService
) {
    fun observeStations(): Flow<List<WaterStation>> =
        dao.observeAllActive().map { list -> list.map { it.toDomain() } }

    fun observeReviewsForStation(stationId: String): Flow<List<StationReview>> =
        reviewDao.observeReviewsForStation(stationId).map { list -> list.map { it.toDomain() } }

    fun observeAllReviews(): Flow<List<StationReview>> =
        reviewDao.observeAllReviews().map { list -> list.map { it.toDomain() } }

    private fun loadBundledTaiwanStations(): OpenWaterDatasetDto? = runCatching {
        context.assets.open("taiwan_water_stations.json").use { stream ->
            val content = stream.bufferedReader().use { it.readText() }
            json.decodeFromString<OpenWaterDatasetDto>(content)
        }
    }.getOrNull()

    suspend fun ensureSeedDataLoaded() {
        val bundled = loadBundledTaiwanStations()
        val stationsToSeed = bundled?.stations?.takeIf { it.isNotEmpty() } ?: defaultTaiwanSeedStations()
        val reviewsToSeed = bundled?.reviews?.takeIf { it.isNotEmpty() } ?: defaultSeedReviews()

        if (dao.count() < stationsToSeed.size) {
            dao.insertIgnoreAll(stationsToSeed.map(WaterStationEntity::fromDomain))
        }
        if (reviewDao.count() < reviewsToSeed.size) {
            reviewDao.insertAll(reviewsToSeed.map(StationReviewEntity::fromDomain))
        }
    }

    suspend fun syncNearby(latitude: Double, longitude: Double, radiusMeters: Int = 2000): Result<Unit> =
        runCatching {
            val remoteStations = api.getNearbyStations(latitude, longitude, radiusMeters)
            dao.insertIgnoreAll(remoteStations.map(WaterStationEntity::fromDomain))
        }

    suspend fun syncFromOpenData(openDataUrl: String = BuildConfig.OPEN_DATA_URL): Result<Int> =
        runCatching {
            val snapshot = api.fetchOpenDataSnapshot(openDataUrl)
            if (snapshot.stations.isNotEmpty()) {
                dao.insertIgnoreAll(snapshot.stations.map(WaterStationEntity::fromDomain))
            }
            if (snapshot.reviews.isNotEmpty()) {
                reviewDao.insertAll(snapshot.reviews.map(StationReviewEntity::fromDomain))
            }
            snapshot.stations.size
        }
    suspend fun discoverNearbyFromOsm(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 3000
    ): Result<Int> = overpassWaterService.fetchNearbyDrinkingWater(
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters
    ).mapCatching { discovered ->
        if (discovered.isNotEmpty()) {
            dao.insertIgnoreAll(discovered.map(WaterStationEntity::fromDomain))
        }
        discovered.size
    }

    suspend fun toggleFavorite(stationId: String, isFavorite: Boolean) {
        dao.updateFavorite(stationId, isFavorite)
    }

    suspend fun addStation(station: WaterStation) {
        dao.upsert(WaterStationEntity.fromDomain(station))
        runCatching { api.createStation(station) }
    }
    suspend fun addReview(review: StationReview) {
        reviewDao.insertReview(StationReviewEntity.fromDomain(review))
        runCatching { api.createStationReview(review.stationId, review) }
    }

    suspend fun reportStationStatus(stationId: String, status: StationStatus) {
        val now = System.currentTimeMillis()
        dao.updateStatus(stationId, status.name, now)
        runCatching { api.reportStatus(stationId, StatusReportRequest(status)) }
    }

    private fun defaultTaiwanSeedStations(): List<WaterStation> = listOf(
        WaterStation(
            id = "tpe-main-station-b1",
            name = "台北車站 B1 誠品捷運連通道飲水機",
            latitude = 25.0462,
            longitude = 121.5174,
            floorHint = "B1 往捷運淡水信義線閘門旁、洗手間外",
            accessType = AccessType.PUBLIC,
            hasHot = true,
            hasWarm = true,
            hasCold = true,
            hasIced = false,
            is24Hours = false,
            openHours = "06:00 - 24:00"
        ),
        WaterStation(
            id = "tpe-library-daan",
            name = "臺北市立圖書館總館 1F 飲水機",
            latitude = 25.0289,
            longitude = 121.5378,
            floorHint = "1F 服務台後方茶水間（免換證可直接入內）",
            accessType = AccessType.SEMI_PUBLIC,
            hasHot = true,
            hasWarm = true,
            hasCold = true,
            hasIced = true,
            is24Hours = false,
            openHours = "08:30 - 21:00"
        ),
        WaterStation(
            id = "ntu-total-library-b1",
            name = "台大總圖書館 B1 自習室外部飲水機",
            latitude = 25.0174,
            longitude = 121.5405,
            floorHint = "B1 國際會議廳走廊旁（免刷學生證區域）",
            accessType = AccessType.SEMI_PUBLIC,
            hasHot = true,
            hasWarm = true,
            hasCold = true,
            hasIced = true,
            is24Hours = false,
            openHours = "08:00 - 22:30"
        ),
        WaterStation(
            id = "zhongshan-sports-center-1f",
            name = "中山運動中心 1F 大廳飲水機",
            latitude = 25.0548,
            longitude = 121.5214,
            floorHint = "1F 進門右手邊販賣機旁，大口徑水壺好裝",
            accessType = AccessType.SEMI_PUBLIC,
            hasHot = true,
            hasWarm = true,
            hasCold = true,
            hasIced = true,
            is24Hours = false,
            openHours = "06:00 - 22:00"
        )
    )

    private fun defaultSeedReviews(): List<StationReview> {
        val now = System.currentTimeMillis()
        val hourMs = 3_600_000L
        return listOf(
            StationReview(
                id = "seed-review-1",
                stationId = "tpe-main-station-b1",
                authorName = "北車通勤族",
                rating = 5,
                comment = "在誠品連通道往淡水信義線閘門前的男廁/女廁外面，水壓很穩，無異味好喝！",
                tags = listOf("無異味好喝", "出水量大", "位置好找"),
                createdAtEpochMillis = now - 36 * hourMs
            ),
            StationReview(
                id = "seed-review-2",
                stationId = "tpe-main-station-b1",
                authorName = "省錢租屋客",
                rating = 4,
                comment = "有溫水跟熱水，下班順路裝一壺回租屋處很方便，不過尖峰時段偶爾要排隊。",
                tags = listOf("熱水夠燙", "位置好找"),
                createdAtEpochMillis = now - 72 * hourMs
            ),
            StationReview(
                id = "seed-review-3",
                stationId = "tpe-library-daan",
                authorName = "大安拎水人",
                rating = 5,
                comment = "走進總館 1F 大門右手邊服務台後面就是茶水間，不用借書證就能進來裝，2L 水壺高度剛好放得下！",
                tags = listOf("無異味好喝", "2L大水壺好裝", "冰水夠冰"),
                createdAtEpochMillis = now - 24 * hourMs
            ),
            StationReview(
                id = "seed-review-4",
                stationId = "ntu-total-library-b1",
                authorName = "公館研究生",
                rating = 5,
                comment = "從總圖旁邊樓梯下 B1 國際會議廳走廊就能看到，免刷卡，冰水超冰、出水超快。",
                tags = listOf("出水量大", "冰水夠冰", "無異味好喝"),
                createdAtEpochMillis = now - 18 * hourMs
            ),
            StationReview(
                id = "seed-review-5",
                stationId = "zhongshan-sports-center-1f",
                authorName = "中山健身仔",
                rating = 5,
                comment = "一進大門右轉販賣機旁邊，出水口很高，裝大水桶或搖搖杯完全不會卡住！",
                tags = listOf("2L大水壺好裝", "出水量大", "位置好找"),
                createdAtEpochMillis = now - 12 * hourMs
            )
        )
    }
}
