# 拎水地圖 (Zero Water Map) — 後端 API 與 Open Data 架構說明

本目錄包含「拎水地圖」的 **GCP Cloud Run 後端 API 伺服器** 與 **PostgreSQL + PostGIS 空間資料庫 Schema**，採用「**讀寫分離 + Open Data 開放資料集**」設計，讓外部開發者（iOS、LINE Bot、網頁地圖）能免費串接全台飲水機資料，同時保護後端免於高額雲端帳單與機器人洗版。

---

## 1. 開放資料集 (`open-data/`)
位於專案根目錄的 [`/open-data`](../open-data) 提供兩種標準格式（採用 **CC-BY-4.0** 開放授權）：
- **`open-data/stations.json`**：包含完整飲水機點位 (`stations`) 與使用者評價 (`reviews`)，可供 Android App 離線快取同步。
- **`open-data/stations.geojson`**：符合 **RFC 7946 GeoJSON `FeatureCollection`** 國際標準，外部開發者可直接匯入 uMap、Mapbox、MapLibre、QGIS 或 GitHub 預覽地圖。

### 免費靜態 API 網址 (GitHub Raw / Pages CDN)
無須架設伺服器，任何人與 Android App 皆可直接透過以下免費網址讀取最新開放資料：
```text
https://raw.githubusercontent.com/Stubby614/zero-water-map/main/open-data/stations.json
https://raw.githubusercontent.com/Stubby614/zero-water-map/main/open-data/stations.geojson
```

---

## 2. Cloud Run 動態 API 端點規格 (`backend/src/server.ts`)

| 方法 | 路徑 | 用途 | 快取 / 防護機制 |
| :--- | :--- | :--- | :--- |
| `GET` | `/healthz` | Cloud Run 存活檢查與點位統計 | 無 |
| `GET` | `/v1/open-data/stations.json` | 匯出完整開放資料集 JSON | `Cache-Control: public, max-age=300` |
| `GET` | `/v1/open-data/stations.geojson` | 匯出標準 GeoJSON `FeatureCollection` | `Cache-Control: public, max-age=300` |
| `GET` | `/v1/stations?lat=25.04&lng=121.51&radius_m=2000` | 查詢指定座標半徑內的飲水機（依距離由近到遠排序） | `Cache-Control: public, max-age=60` |
| `POST` | `/v1/stations` | 通報新增取水點 | IP 滑動視窗 Rate Limit（防機器人洗版） |
| `POST` | `/v1/stations/:id/report` | 回報飲水機狀態 (`WORKING` / `FILTER_EXPIRED` / `OUT_OF_ORDER`) | IP Rate Limit |
| `GET` | `/v1/stations/:id/reviews` | 取得指定取水點的所有評論與星等 | 公開讀取 |
| `POST` | `/v1/stations/:id/reviews` | 新增取水點星級評論、口感標籤與照片 | IP Rate Limit + 參數驗證 |

---

## 3. 本地啟動與測試
```bash
cd backend
bun run src/server.ts
```
啟動後預設監聽 `http://localhost:8080`。

## 4. 部署至 GCP Cloud Run（一行指令）
在專案根目錄執行：
```bash
gcloud run deploy zero-water-api \
  --source . \
  --dockerfile backend/Dockerfile \
  --region asia-east1 \
  --allow-unauthenticated \
  --max-instances 2 \
  --memory 256Mi
```
> 設定 `--max-instances 2` 與 `--memory 256Mi` 可確保完全落在 GCP Cloud Run 每月免費額度內，沒流量時自動縮放至 0 台（$0 費用）。
