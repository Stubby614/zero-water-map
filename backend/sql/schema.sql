-- ============================================================================
-- 拎水地圖 (Zero Water Map) — PostgreSQL + PostGIS 資料庫 Schema
-- 適用於：Supabase (免費 500MB)、Neon PostgreSQL (免費方案)、或 GCP Cloud SQL
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. 取水點主表 (water_stations)
CREATE TABLE IF NOT EXISTS water_stations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    location GEOGRAPHY(Point, 4326) GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
    ) STORED,
    floor_hint TEXT NOT NULL DEFAULT '1F 飲水機',
    access_type TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (access_type IN ('PUBLIC', 'SEMI_PUBLIC', 'RESTRICTED')),
    has_hot BOOLEAN NOT NULL DEFAULT TRUE,
    has_warm BOOLEAN NOT NULL DEFAULT TRUE,
    has_cold BOOLEAN NOT NULL DEFAULT TRUE,
    has_iced BOOLEAN NOT NULL DEFAULT FALSE,
    is_24_hours BOOLEAN NOT NULL DEFAULT FALSE,
    open_hours TEXT NOT NULL DEFAULT '依場館開放時間',
    status TEXT NOT NULL DEFAULT 'WORKING' CHECK (status IN ('WORKING', 'OUT_OF_ORDER', 'FILTER_EXPIRED', 'REMOVED')),
    photo_uri TEXT,
    last_verified_epoch_millis BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 建立 R-Tree (GIST) 空間索引，加速 ST_DWithin 半徑搜尋
CREATE INDEX IF NOT EXISTS idx_water_stations_location_gist
    ON water_stations USING GIST (location);

CREATE INDEX IF NOT EXISTS idx_water_stations_status
    ON water_stations (status)
    WHERE status != 'REMOVED';

-- 2. 取水點評論與位置照片表 (station_reviews)
CREATE TABLE IF NOT EXISTS station_reviews (
    id TEXT PRIMARY KEY,
    station_id TEXT NOT NULL REFERENCES water_stations(id) ON DELETE CASCADE,
    author_name TEXT NOT NULL DEFAULT '租屋拎水客' CHECK (char_length(author_name) <= 50),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL DEFAULT '' CHECK (char_length(comment) <= 1000),
    tags TEXT[] NOT NULL DEFAULT '{}',
    photo_uri TEXT,
    created_at_epoch_millis BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_station_reviews_station_id
    ON station_reviews (station_id, created_at_epoch_millis DESC);

-- 3. 空間半徑查詢輔助函式：查詢 (lat, lng) 方圓 radius_meters 公尺內的飲水機
CREATE OR REPLACE FUNCTION find_nearby_water_stations(
    user_lat DOUBLE PRECISION,
    user_lng DOUBLE PRECISION,
    radius_meters DOUBLE PRECISION DEFAULT 2000
)
RETURNS TABLE (
    id TEXT,
    name TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_meters DOUBLE PRECISION,
    floor_hint TEXT,
    access_type TEXT,
    has_hot BOOLEAN,
    has_warm BOOLEAN,
    has_cold BOOLEAN,
    has_iced BOOLEAN,
    is_24_hours BOOLEAN,
    open_hours TEXT,
    status TEXT,
    photo_uri TEXT,
    last_verified_epoch_millis BIGINT
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        s.id,
        s.name,
        s.latitude,
        s.longitude,
        ST_Distance(
            s.location,
            ST_SetSRID(ST_MakePoint(user_lng, user_lat), 4326)::geography
        ) AS distance_meters,
        s.floor_hint,
        s.access_type,
        s.has_hot,
        s.has_warm,
        s.has_cold,
        s.has_iced,
        s.is_24_hours,
        s.open_hours,
        s.status,
        s.photo_uri,
        s.last_verified_epoch_millis
    FROM water_stations s
    WHERE s.status != 'REMOVED'
      AND ST_DWithin(
          s.location,
          ST_SetSRID(ST_MakePoint(user_lng, user_lat), 4326)::geography,
          radius_meters
      )
    ORDER BY distance_meters ASC
    LIMIT 200;
$$;
