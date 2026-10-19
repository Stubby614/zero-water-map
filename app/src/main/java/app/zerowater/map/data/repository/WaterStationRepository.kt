package app.zerowater.map.data.repository

import app.zerowater.map.data.local.WaterStationDao
import app.zerowater.map.data.local.WaterStationEntity
import app.zerowater.map.data.model.AccessType
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation
import app.zerowater.map.data.remote.StatusReportRequest
import app.zerowater.map.data.remote.WaterStationApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterStationRepository @Inject constructor(
    private val dao: WaterStationDao,
    private val api: WaterStationApi
) {
    fun observeStations(): Flow<List<WaterStation>> =
        dao.observeAllActive().map { list -> list.map { it.toDomain() } }

    suspend fun ensureSeedDataLoaded() {
        if (dao.count() == 0) {
            dao.upsertAll(defaultTaiwanSeedStations().map(WaterStationEntity::fromDomain))
        }
    }

    suspend fun syncNearby(latitude: Double, longitude: Double, radiusMeters: Int = 2000): Result<Unit> =
        runCatching {
            val remoteStations = api.getNearbyStations(latitude, longitude, radiusMeters)
            dao.upsertAll(remoteStations.map(WaterStationEntity::fromDomain))
        }

    suspend fun addStation(station: WaterStation) {
        dao.upsert(WaterStationEntity.fromDomain(station))
        runCatching { api.createStation(station) }
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
}
