import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

export type AccessType = "PUBLIC" | "SEMI_PUBLIC" | "RESTRICTED";
export type StationStatus = "WORKING" | "OUT_OF_ORDER" | "FILTER_EXPIRED" | "REMOVED";

export interface WaterStation {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  floorHint: string;
  accessType: AccessType;
  hasHot: boolean;
  hasWarm: boolean;
  hasCold: boolean;
  hasIced: boolean;
  is24Hours: boolean;
  openHours: string;
  status: StationStatus;
  photoUri?: string | null;
  lastVerifiedEpochMillis: number;
}

export interface StationReview {
  id: string;
  stationId: string;
  authorName: string;
  userName?: string;
  rating: number;
  comment: string;
  tags: string[];
  photoUri?: string | null;
  createdAtEpochMillis: number;
}

export interface OpenWaterDataset {
  version: number;
  updatedAt: string;
  license: string;
  source: string;
  stations: WaterStation[];
  reviews: StationReview[];
}

const DATA_FILE_PATH = resolve(
  process.env.OPEN_DATA_PATH ?? resolve(import.meta.dir, "../../open-data/stations.json")
);
const GEOJSON_FILE_PATH = resolve(
  process.env.OPEN_DATA_GEOJSON_PATH ?? resolve(import.meta.dir, "../../open-data/stations.geojson")
);
const ANDROID_ASSETS_PATH = resolve(
  import.meta.dir,
  "../../app/src/main/assets/taiwan_water_stations.json"
);
const WEB_DIR = resolve(
  process.env.WEB_DIR ?? resolve(import.meta.dir, "../../web")
);
const WEB_DATA_PATH = resolve(WEB_DIR, "stations.json");
function loadInitialDataset(): OpenWaterDataset {
  if (existsSync(DATA_FILE_PATH)) {
    const raw = readFileSync(DATA_FILE_PATH, "utf-8");
    return JSON.parse(raw) as OpenWaterDataset;
  }
  return {
    version: 1,
    updatedAt: new Date().toISOString(),
    license: "CC-BY-4.0",
    source: "Zero Water Map (拎水地圖) Open Data",
    stations: [],
    reviews: [],
  };
}

const dataset: OpenWaterDataset = loadInitialDataset();

function persistSnapshotBestEffort() {
  dataset.updatedAt = new Date().toISOString();
  const jsonStr = JSON.stringify(dataset, null, 2) + "\n";
  try {
    writeFileSync(DATA_FILE_PATH, jsonStr, "utf-8");
  } catch {
    // On read-only container filesystems (e.g. Cloud Run without mounted volume), keep in-memory state
  }
  try {
    const geoJson = toGeoJsonFeatureCollection(dataset.stations);
    writeFileSync(GEOJSON_FILE_PATH, JSON.stringify(geoJson, null, 2) + "\n", "utf-8");
  } catch {
    // Best-effort
  }
  try {
    if (existsSync(ANDROID_ASSETS_PATH)) {
      writeFileSync(ANDROID_ASSETS_PATH, jsonStr, "utf-8");
    }
  } catch {
    // Best-effort
  }
  try {
    if (existsSync(WEB_DIR)) {
      writeFileSync(WEB_DATA_PATH, jsonStr, "utf-8");
    }
  } catch {
    // Best-effort
  }
}

// Haversine distance in meters
function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function toGeoJsonFeatureCollection(stations: WaterStation[]) {
  return {
    type: "FeatureCollection",
    metadata: {
      name: "Zero Water Map (拎水地圖) Taiwan Drinking Water Stations",
      license: "CC-BY-4.0",
      updatedAt: dataset.updatedAt,
    },
    features: stations
      .filter((s) => s.status !== "REMOVED")
      .map((s) => ({
        type: "Feature",
        id: s.id,
        geometry: {
          type: "Point",
          coordinates: [s.longitude, s.latitude],
        },
        properties: {
          name: s.name,
          floorHint: s.floorHint,
          accessType: s.accessType,
          hasHot: s.hasHot,
          hasWarm: s.hasWarm,
          hasCold: s.hasCold,
          hasIced: s.hasIced,
          is24Hours: s.is24Hours,
          openHours: s.openHours,
          status: s.status,
          lastVerifiedEpochMillis: s.lastVerifiedEpochMillis,
        },
      })),
  };
}

// Sliding-window IP rate limiter for write endpoints (prevents bot abuse on Cloud Run)
const writeRateMap = new Map<string, number[]>();
const RATE_WINDOW_MS = 10 * 60 * 1000; // 10 minutes
const MAX_WRITES_PER_WINDOW = 20;

function checkWriteRateLimit(ip: string): boolean {
  const now = Date.now();
  const recent = (writeRateMap.get(ip) ?? []).filter((t) => now - t < RATE_WINDOW_MS);
  if (recent.length >= MAX_WRITES_PER_WINDOW) {
    writeRateMap.set(ip, recent);
    return false;
  }
  recent.push(now);
  writeRateMap.set(ip, recent);
  return true;
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-App-Token",
};

function jsonResponse(body: unknown, status = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

export async function handleRequest(req: Request, clientIp = "127.0.0.1"): Promise<Response> {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  const url = new URL(req.url);
  const path = url.pathname;

  // 0. Static Web App Files (PWA)
  if (req.method === "GET") {
    if (path === "/" || path === "/index.html") {
      const indexPath = resolve(WEB_DIR, "index.html");
      if (existsSync(indexPath)) {
        return new Response(readFileSync(indexPath, "utf-8"), {
          headers: { "Content-Type": "text/html; charset=utf-8", ...CORS_HEADERS },
        });
      }
    }
    if (path === "/styles.css") {
      const cssPath = resolve(WEB_DIR, "styles.css");
      if (existsSync(cssPath)) {
        return new Response(readFileSync(cssPath, "utf-8"), {
          headers: { "Content-Type": "text/css; charset=utf-8", ...CORS_HEADERS },
        });
      }
    }
    if (path === "/app.js") {
      const jsPath = resolve(WEB_DIR, "app.js");
      if (existsSync(jsPath)) {
        return new Response(readFileSync(jsPath, "utf-8"), {
          headers: { "Content-Type": "application/javascript; charset=utf-8", ...CORS_HEADERS },
        });
      }
    }
    if (path === "/manifest.webmanifest" || path === "/manifest.json") {
      const manifestPath = resolve(WEB_DIR, "manifest.webmanifest");
      if (existsSync(manifestPath)) {
        return new Response(readFileSync(manifestPath, "utf-8"), {
          headers: { "Content-Type": "application/manifest+json; charset=utf-8", ...CORS_HEADERS },
        });
      }
    }
    if (path === "/stations.json") {
      return jsonResponse(dataset, 200, {
        "Cache-Control": "public, max-age=300",
      });
    }
    if (path.startsWith("/icons/") || path.endsWith(".png") || path.endsWith(".svg") || path.endsWith(".ico")) {
      const filePath = resolve(WEB_DIR, "." + path);
      if (existsSync(filePath)) {
        const ext = path.split(".").pop()?.toLowerCase();
        const mimeMap: Record<string, string> = {
          svg: "image/svg+xml",
          png: "image/png",
          ico: "image/x-icon",
          webp: "image/webp",
        };
        const mime = ext ? mimeMap[ext] ?? "application/octet-stream" : "application/octet-stream";
        return new Response(readFileSync(filePath), {
          headers: { "Content-Type": mime, ...CORS_HEADERS },
        });
      }
    }
  }

  // 1. Health check (Cloud Run readiness/liveness probe)
  if (req.method === "GET" && path === "/healthz") {
    return jsonResponse({
      status: "ok",
      stationCount: dataset.stations.length,
      reviewCount: dataset.reviews.length,
      updatedAt: dataset.updatedAt,
    });
  }

  // 2. Open Data Full Snapshot (JSON) - CDN cacheable
  if (req.method === "GET" && (path === "/v1/open-data/stations.json" || path === "/open-data/stations.json")) {
    return jsonResponse(dataset, 200, {
      "Cache-Control": "public, max-age=300",
    });
  }

  // 3. Open Data GeoJSON FeatureCollection - RFC 7946 standard for 3rd-party developers
  if (req.method === "GET" && (path === "/v1/open-data/stations.geojson" || path === "/open-data/stations.geojson")) {
    return jsonResponse(toGeoJsonFeatureCollection(dataset.stations), 200, {
      "Cache-Control": "public, max-age=300",
    });
  }

  // 4. GET /v1/stations?lat=...&lng=...&radius_m=2000
  if (req.method === "GET" && path === "/v1/stations") {
    const lat = Number.parseFloat(url.searchParams.get("lat") ?? "");
    const lng = Number.parseFloat(url.searchParams.get("lng") ?? "");
    const radiusMeters = Number.parseFloat(url.searchParams.get("radius_m") ?? "2000");

    const active = dataset.stations.filter((s) => s.status !== "REMOVED");
    if (Number.isFinite(lat) && Number.isFinite(lng)) {
      const nearby = active
        .map((station) => ({
          station,
          dist: haversineMeters(lat, lng, station.latitude, station.longitude),
        }))
        .filter((item) => item.dist <= radiusMeters)
        .sort((a, b) => a.dist - b.dist)
        .map((item) => item.station);
      return jsonResponse(nearby, 200, { "Cache-Control": "public, max-age=60" });
    }
    return jsonResponse(active, 200, { "Cache-Control": "public, max-age=60" });
  }

  // Write endpoints require rate-limit check
  if (req.method === "POST") {
    if (!checkWriteRateLimit(clientIp)) {
      return jsonResponse(
        { error: "Too many submissions. Please try again later." },
        429
      );
    }
  }

  // 5. POST /v1/stations — Report a new water station
  if (req.method === "POST" && path === "/v1/stations") {
    const body = (await req.json().catch(() => null)) as Partial<WaterStation> | null;
    if (
      !body ||
      typeof body.name !== "string" ||
      body.name.trim().length === 0 ||
      typeof body.latitude !== "number" ||
      typeof body.longitude !== "number" ||
      body.latitude < -90 ||
      body.latitude > 90 ||
      body.longitude < -180 ||
      body.longitude > 180
    ) {
      return jsonResponse({ error: "Invalid water station payload" }, 400);
    }

    const created: WaterStation = {
      id: body.id && body.id.trim().length > 0 ? body.id.trim() : `station-${crypto.randomUUID()}`,
      name: body.name.trim().slice(0, 120),
      latitude: body.latitude,
      longitude: body.longitude,
      floorHint: (body.floorHint ?? "1F 飲水機").trim().slice(0, 240),
      accessType: body.accessType ?? "PUBLIC",
      hasHot: Boolean(body.hasHot ?? true),
      hasWarm: Boolean(body.hasWarm ?? true),
      hasCold: Boolean(body.hasCold ?? true),
      hasIced: Boolean(body.hasIced ?? false),
      is24Hours: Boolean(body.is24Hours ?? false),
      openHours: (body.openHours ?? "依場館開放時間").trim().slice(0, 100),
      status: body.status ?? "WORKING",
      photoUri: body.photoUri ?? null,
      lastVerifiedEpochMillis: Date.now(),
    };

    const existingIdx = dataset.stations.findIndex((s) => s.id === created.id);
    if (existingIdx >= 0) {
      dataset.stations[existingIdx] = created;
    } else {
      dataset.stations.push(created);
    }
    persistSnapshotBestEffort();
    return jsonResponse(created, 201);
  }

  // 6. POST /v1/stations/:id/report — Update station operational status
  const reportMatch = path.match(/^\/v1\/stations\/([^/]+)\/report$/);
  if (req.method === "POST" && reportMatch) {
    const stationId = decodeURIComponent(reportMatch[1]);
    const station = dataset.stations.find((s) => s.id === stationId);
    if (!station) {
      return jsonResponse({ error: "Water station not found" }, 404);
    }
    const body = (await req.json().catch(() => null)) as { status?: StationStatus } | null;
    if (!body?.status) {
      return jsonResponse({ error: "Missing status field" }, 400);
    }
    station.status = body.status;
    station.lastVerifiedEpochMillis = Date.now();
    persistSnapshotBestEffort();
    return jsonResponse(station, 200);
  }

  // 7. GET & POST /v1/stations/:id/reviews
  const reviewsMatch = path.match(/^\/v1\/stations\/([^/]+)\/reviews$/);
  if (reviewsMatch) {
    const stationId = decodeURIComponent(reviewsMatch[1]);
    if (req.method === "GET") {
      const stationReviews = dataset.reviews
        .filter((r) => r.stationId === stationId)
        .sort((a, b) => b.createdAtEpochMillis - a.createdAtEpochMillis);
      return jsonResponse(stationReviews, 200);
    }

    if (req.method === "POST") {
      const body = (await req.json().catch(() => null)) as Partial<StationReview> | null;
      if (!body || typeof body.rating !== "number" || body.rating < 1 || body.rating > 5) {
        return jsonResponse({ error: "Rating must be between 1 and 5" }, 400);
      }
      const author = (body.authorName ?? (body as Record<string, unknown>).userName ?? "租屋拎水客").toString().trim().slice(0, 50) || "租屋拎水客";
      const createdReview: StationReview = {
        id: body.id && body.id.trim().length > 0 ? body.id.trim() : `review-${crypto.randomUUID()}`,
        stationId,
        authorName: author,
        userName: author,
        rating: Math.round(body.rating),
        comment: (body.comment ?? "").trim().slice(0, 1000),
        tags: Array.isArray(body.tags) ? body.tags.map(String).slice(0, 10) : [],
        photoUri: body.photoUri ?? null,
        createdAtEpochMillis: Date.now(),
      };
      dataset.reviews.unshift(createdReview);
      persistSnapshotBestEffort();
      return jsonResponse(createdReview, 201);
    }
  }

  return jsonResponse({ error: "Not Found" }, 404);
}

if (import.meta.main) {
  const port = Number.parseInt(process.env.PORT ?? "8080", 10);
  const server = Bun.serve({
    port,
    fetch(req, srv) {
      const forwardedFor = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
      const clientIp = forwardedFor || srv.requestIP(req)?.address || "127.0.0.1";
      return handleRequest(req, clientIp);
    },
  });
  console.log(`Zero Water Map API listening on http://localhost:${server.port}`);
}
