# 拎水地圖 (Zero Water Map)

> 專為台灣租屋族打造的開源免費飲水機地圖 Android App —— 出門散步順便拎一壺水回家！

## 專案特色
- **100% 開源且免 API Key 即可運行**：採用 **MapLibre Native** + **OpenFreeMap** 向量圖磚，Clone 下來無須設定 Google Maps API Key 或綁定信用卡即可直接編譯並顯示地圖。
- **租屋族真正需要的飲水機資訊**：
  - **進入門檻 (`AccessType`)**：區分「完全開放」、「半開放場館（圖書館／運動中心／捷運站）」與「需門禁或學生證」。
  - **水溫篩選**：一鍵篩選「有冰水」、「有熱水（泡麵族必備）」、「免門禁」。
  - **詳細樓層指引 (`floorHint`)**：例如「1F 服務台後方茶水間」、「B1 往捷運連通道洗手間外」。
  - **群眾外包通報與狀態回報**：支援拖曳地圖中心紅針準星「通報新的取水點」，以及一鍵回報「正常出水」、「濾心亮紅燈」、「故障維修中」。
  - **即時定位、最近點位推薦與步行路線繪製**：自動計算使用者與各取水點的距離與預估步行時間，點擊取水點後直接在地圖上繪製 **OSRM 最佳步行路線（含離線直線估算備援）**。
- **離線優先 (Offline-first)**：使用 **Room SQLite** 快取飲水機點位，租屋處周邊點位載入後無網路也能查詢。

## 技術棧 (Tech Stack)
- **語言**：Kotlin 2.1 (JVM Target 17)
- **UI 框架**：Jetpack Compose (Material 3)
- **地圖引擎**：MapLibre Android SDK + OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`)
- **架構**：MVVM + Repository Pattern + Hilt Dependency Injection
- **本地資料庫**：Room (`ZeroWaterDatabase`)
- **網路層**：Retrofit 2 + OkHttp + Kotlinx Serialization
- **預定後端 (GCP)**：Cloud Run + PostgreSQL (PostGIS)

## 專案結構
```text
app/src/main/java/app/zerowater/map/
├── data/
│   ├── location/      # LocationTracker (FusedLocation + 定位權限與距離/步行時間格式化)
│   ├── route/         # RouteRepository (OSRM 步行路線規劃與離線直線估算備援)
│   ├── model/         # WaterStation, AccessType, StationStatus 資料模型
│   ├── local/         # Room Entity, DAO (支援經緯度 Bounding Box 查詢), Database
│   ├── remote/        # Retrofit API 介面 (預留對接 GCP Cloud Run)
│   └── repository/    # WaterStationRepository (含預設台灣種子點位)
├── di/                # Hilt DI Module (AppModule)
├── ui/
│   ├── map/           # MapScreen, MapLibreView, AddStationSheet, MapViewModel
│   └── theme/         # Material 3 主題配色
├── MainActivity.kt
└── ZeroWaterApplication.kt
```

## 快速開始
1. 使用 **Android Studio Ladybug (2024.2.1)** 或更新版本開啟此專案目錄。
2. 等待 Gradle Sync 完成（無須設定任何 API Key）。
3. 連接 Android 手機或啟動模擬器（API 26+），點擊 **Run (`app`)** 即可看到內建種子點位與互動式地圖。

## 開源授權
本專案採用 [MIT License](LICENSE) 授權。
