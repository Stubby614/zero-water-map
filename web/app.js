/**
 * 拎水地圖 Zero Water Map - Web 應用程式核心邏輯
 * 與 Android 端功能完全一致，支援水溫分類、步行導航、自訂圖釘顏色、評價與照片上傳、雙向資料同步。
 */

// ============================================================================
// 1. 常數與設定
// ============================================================================
const OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
const DEFAULT_CENTER = [121.535, 25.042]; // 台北市中心預設座標 [lng, lat]
const DEFAULT_ZOOM = 14;

const PRESET_MARKER_COLORS = [
  { name: "海洋藍", hex: "#1E88E5" },
  { name: "亮麗黃", hex: "#FDD835" },
  { name: "熱情紅", hex: "#E53935" },
  { name: "活力橘", hex: "#FB8C00" },
  { name: "森林綠", hex: "#43A047" },
  { name: "冰川青", hex: "#00ACC1" },
  { name: "極光紫", hex: "#8E24AA" },
  { name: "櫻花粉", hex: "#D81B60" },
];

const DEFAULT_CATEGORY_COLORS = {
  ICED_TRIPLE: "#1E88E5",
  WARM_HOT: "#FDD835",
  HOT_OR_SINGLE: "#E53935",
};

const QUICK_REVIEW_TAGS = [
  "無異味好喝",
  "出水量大",
  "2L大水壺好裝",
  "位置好找",
  "熱水夠燙",
  "冰水夠冰",
  "濾心乾淨",
  "常態維修",
];

const STORAGE_KEYS = {
  MARKER_COLORS: "zero_water_marker_colors",
  FAVORITES: "zero_water_favorites",
  LOCAL_STATIONS: "zero_water_local_stations",
  LOCAL_REVIEWS: "zero_water_local_reviews",
};

// ============================================================================
// 2. 狀態管理 (State)
// ============================================================================
const state = {
  map: null,
  stations: [],
  reviews: [],
  favorites: new Set(),
  markerColors: { ...DEFAULT_CATEGORY_COLORS },
  userLocation: null, // [lng, lat]
  userMarker: null,
  stationMarkers: new Map(), // stationId -> maplibregl.Marker
  activeFilters: {
    favorite: false,
    public: false,
    iced: false,
    hot: false,
    "24h": false,
  },
  selectedStation: null,
  activeRoute: null,
  isAddingStation: false,
  addCoords: null, // [lng, lat]
  currentListTab: "all",
  reviewPhotoDataUrl: null,
  addPhotoDataUrl: null,
  reviewRating: 5,
  selectedReviewTags: new Set(),
};

// ============================================================================
// 3. 工具函式 (Utilities)
// ============================================================================
function haversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

function formatDistance(meters) {
  if (meters == null || isNaN(meters)) return "";
  if (meters < 1000) return `${meters} 公尺`;
  return `${(meters / 1000).toFixed(1)} 公里`;
}

function getTemperatureCategory(station) {
  if (station.hasIced) return "ICED_TRIPLE";
  if (station.hasWarm && station.hasHot) return "WARM_HOT";
  return "HOT_OR_SINGLE";
}

function getCategoryColor(category) {
  return state.markerColors[category] || DEFAULT_CATEGORY_COLORS[category] || "#1E88E5";
}

function getCategoryLabel(category) {
  switch (category) {
    case "ICED_TRIPLE":
      return "冰溫熱";
    case "WARM_HOT":
      return "溫熱水";
    case "HOT_OR_SINGLE":
      return "熱水/單溫";
    default:
      return "飲水機";
  }
}

function getAccessLabel(access) {
  switch (access) {
    case "PUBLIC":
      return "公開自由出入";
    case "SEMI_PUBLIC":
      return "半公開 (需登記/消費)";
    case "RESTRICTED":
      return "受限管制區";
    default:
      return "一般出入";
  }
}

function getStatusDetails(status) {
  switch (status) {
    case "OUT_OF_ORDER":
      return { text: "❌ 故障停用中", cls: "status-error" };
    case "FILTER_EXPIRED":
      return { text: "⚠️ 濾心待更換", cls: "status-warning" };
    default:
      return { text: "✅ 正常供水中", cls: "" };
  }
}

/**
 * 使用 HTML5 Canvas 壓縮照片 (等比例縮小、最高 1080px、JPEG 0.78)
 */
async function compressImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const MAX_DIM = 1080;
        let width = img.width;
        let height = img.height;

        if (width > MAX_DIM || height > MAX_DIM) {
          if (width > height) {
            height = Math.round((height * MAX_DIM) / width);
            width = MAX_DIM;
          } else {
            width = Math.round((width * MAX_DIM) / height);
            height = MAX_DIM;
          }
        }

        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, width, height);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.78);
        resolve(dataUrl);
      };
      img.onerror = reject;
      img.src = e.target.result;
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// ============================================================================
// 4. 資料同步層 (DataSyncService)
// ============================================================================
const DataSyncService = {
  loadStoredPreferences() {
    // 讀取自訂圖釘顏色
    try {
      const savedColors = localStorage.getItem(STORAGE_KEYS.MARKER_COLORS);
      if (savedColors) {
        state.markerColors = { ...DEFAULT_CATEGORY_COLORS, ...JSON.parse(savedColors) };
      }
    } catch (e) {
      console.warn("載入顏色設定失敗", e);
    }
    this.applyCssThemeVariables();

    // 讀取收藏
    try {
      const savedFavs = localStorage.getItem(STORAGE_KEYS.FAVORITES);
      if (savedFavs) {
        state.favorites = new Set(JSON.parse(savedFavs));
      }
    } catch (e) {
      console.warn("載入收藏設定失敗", e);
    }
  },

  applyCssThemeVariables() {
    const root = document.documentElement;
    root.style.setProperty("--temp-iced", state.markerColors.ICED_TRIPLE);
    root.style.setProperty("--temp-warm", state.markerColors.WARM_HOT);
    root.style.setProperty("--temp-hot", state.markerColors.HOT_OR_SINGLE);
  },

  saveColors(colors) {
    state.markerColors = { ...colors };
    localStorage.setItem(STORAGE_KEYS.MARKER_COLORS, JSON.stringify(state.markerColors));
    this.applyCssThemeVariables();
  },

  toggleFavorite(stationId) {
    if (state.favorites.has(stationId)) {
      state.favorites.delete(stationId);
    } else {
      state.favorites.add(stationId);
    }
    localStorage.setItem(STORAGE_KEYS.FAVORITES, JSON.stringify(Array.from(state.favorites)));
    return state.favorites.has(stationId);
  },

  async fetchStationsAndReviews() {
    const endpoints = [
      `/v1/open-data/stations.json?_t=${Date.now()}`,
      `./stations.json?_t=${Date.now()}`,
      `../open-data/stations.json?_t=${Date.now()}`,
    ];

    let dataset = null;
    for (const url of endpoints) {
      try {
        const res = await fetch(url, { cache: "no-store" });
        if (res.ok) {
          dataset = await res.json();
          break;
        }
      } catch {
        // 嘗試下一個路徑
      }
    }

    if (!dataset || !Array.isArray(dataset.stations)) {
      dataset = { stations: [], reviews: [] };
    }

    // 合併本地離線新增的站點（以 id 去重）
    const stationMap = new Map(dataset.stations.map((s) => [s.id, s]));
    try {
      const localStationsRaw = localStorage.getItem(STORAGE_KEYS.LOCAL_STATIONS);
      if (localStationsRaw) {
        const localStations = JSON.parse(localStationsRaw);
        for (const ls of localStations) {
          stationMap.set(ls.id, ls);
        }
      }
    } catch (e) {
      console.warn("載入本地站點失敗", e);
    }
    dataset.stations = Array.from(stationMap.values());

    // 合併本地離線新增的評價（以 id 去重）
    const reviewMap = new Map((dataset.reviews || []).map((r) => [r.id, r]));
    try {
      const localReviewsRaw = localStorage.getItem(STORAGE_KEYS.LOCAL_REVIEWS);
      if (localReviewsRaw) {
        const localReviews = JSON.parse(localReviewsRaw);
        for (const lr of localReviews) {
          if (!reviewMap.has(lr.id)) {
            reviewMap.set(lr.id, lr);
          }
        }
      }
    } catch (e) {
      console.warn("載入本地評論失敗", e);
    }
    dataset.reviews = Array.from(reviewMap.values());

    state.stations = dataset.stations;
    state.reviews = dataset.reviews;
    return dataset;
  },

  async addStation(payload) {
    let created = null;
    let isOffline = false;
    try {
      const res = await fetch("/v1/stations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (res.ok) {
        created = await res.json();
      } else {
        isOffline = true;
      }
    } catch {
      isOffline = true;
    }

    if (!created) {
      created = {
        id: `station-${crypto.randomUUID ? crypto.randomUUID() : Date.now()}`,
        ...payload,
        status: payload.status || "WORKING",
        lastVerifiedEpochMillis: Date.now(),
      };
    }

    // 僅在離線／伺服器失敗時寫入本地離線暫存
    if (isOffline) {
      try {
        const local = JSON.parse(localStorage.getItem(STORAGE_KEYS.LOCAL_STATIONS) || "[]");
        local.unshift(created);
        localStorage.setItem(STORAGE_KEYS.LOCAL_STATIONS, JSON.stringify(local));
      } catch (e) {
        console.warn(e);
      }
    }

    // 更新 state
    const existingIndex = state.stations.findIndex((s) => s.id === created.id);
    if (existingIndex >= 0) {
      state.stations[existingIndex] = created;
    } else {
      state.stations.unshift(created);
    }

    return created;
  },

  async updateStatus(stationId, status) {
    try {
      await fetch(`/v1/stations/${encodeURIComponent(stationId)}/report`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status }),
      });
    } catch {
      // 離線維持前端同步
    }

    const station = state.stations.find((s) => s.id === stationId);
    if (station) {
      station.status = status;
      station.lastVerifiedEpochMillis = Date.now();
      try {
        const local = JSON.parse(localStorage.getItem(STORAGE_KEYS.LOCAL_STATIONS) || "[]");
        const idx = local.findIndex((s) => s.id === stationId);
        if (idx >= 0) {
          local[idx].status = status;
        } else {
          local.push(station);
        }
        localStorage.setItem(STORAGE_KEYS.LOCAL_STATIONS, JSON.stringify(local));
      } catch (e) {
        console.warn(e);
      }
    }
  },

  async addReview(stationId, reviewPayload) {
    let created = null;
    let isOffline = false;
    try {
      const res = await fetch(`/v1/stations/${encodeURIComponent(stationId)}/reviews`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(reviewPayload),
      });
      if (res.ok) {
        created = await res.json();
      } else {
        isOffline = true;
      }
    } catch {
      isOffline = true;
    }

    if (!created) {
      created = {
        id: `review-${crypto.randomUUID ? crypto.randomUUID() : Date.now()}`,
        stationId,
        authorName: reviewPayload.authorName || reviewPayload.userName || "租屋拎水客",
        userName: reviewPayload.authorName || reviewPayload.userName || "租屋拎水客",
        rating: reviewPayload.rating,
        comment: reviewPayload.comment,
        tags: reviewPayload.tags || [],
        photoUri: reviewPayload.photoUri || null,
        createdAtEpochMillis: Date.now(),
      };
    }

    // 僅在離線／伺服器失敗時寫入本地離線暫存
    if (isOffline) {
      try {
        const localReviews = JSON.parse(localStorage.getItem(STORAGE_KEYS.LOCAL_REVIEWS) || "[]");
        localReviews.unshift(created);
        localStorage.setItem(STORAGE_KEYS.LOCAL_REVIEWS, JSON.stringify(localReviews));
      } catch (e) {
        console.warn(e);
      }
    }

    const existingIdx = state.reviews.findIndex((r) => r.id === created.id);
    if (existingIdx >= 0) {
      state.reviews[existingIdx] = created;
    } else {
      state.reviews.unshift(created);
    }
    return created;
  },
};

// ============================================================================
// 5. 導航、地圖與外部服務 (OSRM, Overpass, Nominatim)
// ============================================================================
const ExternalServices = {
  /**
   * 計算步行路線 (OSRM)
   */
  async fetchWalkingRoute(startLng, startLat, endLng, endLat) {
    const url = `https://router.project-osrm.org/route/v1/foot/${startLng},${startLat};${endLng},${endLat}?overview=full&geometries=geojson`;
    try {
      const res = await fetch(url);
      if (res.ok) {
        const data = await res.json();
        if (data.code === "Ok" && data.routes && data.routes.length > 0) {
          const route = data.routes[0];
          return {
            coordinates: route.geometry.coordinates,
            distanceMeters: Math.round(route.distance),
            durationSeconds: Math.round(route.duration),
            isActualRoad: true,
          };
        }
      }
    } catch (e) {
      console.warn("OSRM 步行路線取得失敗，退回直線距離", e);
    }

    // 直線備案 (Fallback)
    const straightDist = haversineMeters(startLat, startLng, endLat, endLng);
    return {
      coordinates: [
        [startLng, startLat],
        [endLng, endLat],
      ],
      distanceMeters: straightDist,
      durationSeconds: Math.round(straightDist / 1.25),
      isActualRoad: false,
    };
  },

  /**
   * OpenStreetMap Overpass API 探索周邊飲水機
   */
  async exploreNearbyOsmWater(lat, lng, radiusMeters = 2500) {
    const query = `
      [out:json][timeout:15];
      (
        node["amenity"="drinking_water"](around:${radiusMeters},${lat},${lng});
        node["drinking_water"="yes"](around:${radiusMeters},${lat},${lng});
        way["amenity"="drinking_water"](around:${radiusMeters},${lat},${lng});
      );
      out center 60;
    `.trim();

    const endpoints = [
      "https://overpass-api.de/api/interpreter",
      "https://overpass.kumi.systems/api/interpreter",
    ];

    let lastErr = null;
    for (const ep of endpoints) {
      try {
        const res = await fetch(ep, {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: `data=${encodeURIComponent(query)}`,
        });
        if (res.ok) {
          const json = await res.json();
          return this.parseOverpassElements(json.elements || []);
        }
      } catch (err) {
        lastErr = err;
      }
    }
    throw lastErr || new Error("無法連線至 Overpass 伺服器");
  },

  parseOverpassElements(elements) {
    const discovered = [];
    for (const el of elements) {
      const latitude = el.lat ?? el.center?.lat;
      const longitude = el.lon ?? el.center?.lon;
      if (latitude == null || longitude == null) continue;

      const tags = el.tags || {};
      const name = tags.name || tags["name:zh"] || tags.description || "公共飲水機 (OSM)";
      const level = tags.level || tags["addr:floor"];
      const floorHint = level ? `${level}F 飲水區` : "1F 飲水機";

      discovered.push({
        id: `osm-${el.type}-${el.id}`,
        name,
        latitude,
        longitude,
        floorHint,
        accessType: tags.access === "private" ? "RESTRICTED" : "PUBLIC",
        hasHot: tags.bottle === "yes" || tags.fee === "no" || true,
        hasWarm: true,
        hasCold: true,
        hasIced: tags.chilled === "yes" || false,
        is24Hours: tags.opening_hours === "24/7",
        openHours: tags.opening_hours || "全天開放",
        status: "WORKING",
        photoUri: null,
        lastVerifiedEpochMillis: Date.now(),
      });
    }
    return discovered;
  },

  /**
   * Nominatim 反向門牌查詢
   */
  async reverseGeocode(lat, lng) {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`;
    try {
      const res = await fetch(url, { headers: { "Accept-Language": "zh-TW,zh;q=0.9" } });
      if (res.ok) {
        const data = await res.json();
        return data.display_name || "台灣未知地址";
      }
    } catch {
      // ignore
    }
    return `座標 (${lat.toFixed(4)}, ${lng.toFixed(4)})`;
  },

  /**
   * Nominatim 地址搜尋
   */
  async searchAddress(query) {
    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(
      query
    )}&countrycodes=tw&limit=5`;
    try {
      const res = await fetch(url, { headers: { "Accept-Language": "zh-TW,zh;q=0.9" } });
      if (res.ok) {
        return await res.json();
      }
    } catch {
      // ignore
    }
    return [];
  },
};

// ============================================================================
// 6. 地圖控制器與圖釘管理 (Map & Markers)
// ============================================================================
function createMarkerElement(station) {
  const category = getTemperatureCategory(station);
  const color = getCategoryColor(category);

  const el = document.createElement("div");
  el.className = "custom-station-marker";
  el.dataset.id = station.id;
  el.title = `${station.name} (${getCategoryLabel(category)})`;

  // 精緻水滴圖釘 SVG
  el.innerHTML = `
    <svg class="marker-pin-svg" viewBox="0 0 36 44" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M18 42C18 42 32 26.5 32 16C32 8.268 25.732 2 18 2C10.268 2 4 8.268 4 16C4 26.5 18 42 18 42Z" fill="${color}" stroke="#FFFFFF" stroke-width="2.5" stroke-linejoin="round"/>
      <circle cx="18" cy="16" r="6" fill="#FFFFFF"/>
      <circle cx="18" cy="16" r="3.2" fill="${color}"/>
    </svg>
  `;

  el.addEventListener("click", (e) => {
    e.stopPropagation();
    selectStation(station);
  });

  return el;
}

function updateMarkerColorsOnMap() {
  for (const [stationId, marker] of state.stationMarkers.entries()) {
    const station = state.stations.find((s) => s.id === stationId);
    if (!station) continue;
    const category = getTemperatureCategory(station);
    const color = getCategoryColor(category);
    const el = marker.getElement();
    const paths = el.querySelectorAll("path, circle[fill]:not([fill='#FFFFFF'])");
    for (const p of paths) {
      p.setAttribute("fill", color);
    }
  }

  // 同步更新頂部與 Modal 中的預覽色點
  document.getElementById("dot-iced-legend").style.backgroundColor = state.markerColors.ICED_TRIPLE;
  document.getElementById("dot-warm-legend").style.backgroundColor = state.markerColors.WARM_HOT;
  document.getElementById("dot-hot-legend").style.backgroundColor = state.markerColors.HOT_OR_SINGLE;

  const dotIcedPrev = document.getElementById("dot-iced-preview");
  if (dotIcedPrev) dotIcedPrev.style.backgroundColor = state.markerColors.ICED_TRIPLE;
  const dotWarmPrev = document.getElementById("dot-warm-preview");
  if (dotWarmPrev) dotWarmPrev.style.backgroundColor = state.markerColors.WARM_HOT;
  const dotHotPrev = document.getElementById("dot-hot-preview");
  if (dotHotPrev) dotHotPrev.style.backgroundColor = state.markerColors.HOT_OR_SINGLE;
}

function renderAllMarkers() {
  // 清除舊圖釘
  for (const marker of state.stationMarkers.values()) {
    marker.remove();
  }
  state.stationMarkers.clear();

  // 篩選站點
  const filtered = state.stations.filter((s) => {
    if (s.status === "REMOVED") return false;
    if (state.activeFilters.favorite && !state.favorites.has(s.id)) return false;
    if (state.activeFilters.public && s.accessType !== "PUBLIC") return false;
    if (state.activeFilters.iced && !s.hasIced) return false;
    if (state.activeFilters.hot && !s.hasHot) return false;
    if (state.activeFilters["24h"] && !s.is24Hours) return false;
    return true;
  });

  // 建立新圖釘
  for (const station of filtered) {
    const el = createMarkerElement(station);
    const marker = new maplibregl.Marker({ element: el, anchor: "bottom" })
      .setLngLat([station.longitude, station.latitude])
      .addTo(state.map);
    state.stationMarkers.set(station.id, marker);
  }

  // 更新計數
  document.getElementById("station-count-badge").textContent = filtered.length;
  document.getElementById("list-badge").textContent = filtered.length;
}

function clearWalkingRoute() {
  if (!state.map) return;
  if (state.map.getSource("walking-route")) {
    state.map.getSource("walking-route").setData({
      type: "FeatureCollection",
      features: [],
    });
  }
}

function drawWalkingRoute(coordinates) {
  if (!state.map) return;
  const geojson = {
    type: "Feature",
    properties: {},
    geometry: {
      type: "LineString",
      coordinates,
    },
  };

  if (state.map.getSource("walking-route")) {
    state.map.getSource("walking-route").setData(geojson);
  } else {
    state.map.addSource("walking-route", {
      type: "geojson",
      data: geojson,
    });
    state.map.addLayer({
      id: "walking-route-line",
      type: "line",
      source: "walking-route",
      layout: {
        "line-join": "round",
        "line-cap": "round",
      },
      paint: {
        "line-color": "#0277BD",
        "line-width": 5,
        "line-dasharray": [2, 2],
      },
    });
  }
}

// ============================================================================
// 7. 互動詳情與面板邏輯 (Sheets & Modals)
// ============================================================================
function showBackdrop(show = true) {
  const bd = document.getElementById("backdrop");
  if (show) bd.classList.remove("hidden");
  else bd.classList.add("hidden");
}

function closeAllSheets() {
  document.getElementById("detail-sheet").classList.add("hidden");
  document.getElementById("list-sheet").classList.add("hidden");
  document.getElementById("add-sheet").classList.add("hidden");
  document.getElementById("color-modal").classList.add("hidden");
  document.getElementById("photo-modal").classList.add("hidden");
  document.getElementById("center-crosshair").classList.add("hidden");
  showBackdrop(false);
  state.isAddingStation = false;
  clearWalkingRoute();
}

async function selectStation(station) {
  state.selectedStation = station;
  closeAllSheets();

  // 移動地圖中心
  state.map.easeTo({
    center: [station.longitude, station.latitude],
    zoom: Math.max(state.map.getZoom(), 15),
    offset: window.innerWidth >= 768 ? [-120, 0] : [0, -100],
    duration: 600,
  });

  // 渲染詳情面板
  const sheet = document.getElementById("detail-sheet");
  const nameEl = document.getElementById("detail-name");
  const statusCapsule = document.getElementById("detail-status-capsule");
  const tagsContainer = document.getElementById("detail-tags");
  const favBtn = document.getElementById("btn-toggle-favorite");

  nameEl.textContent = station.name;
  const statusInfo = getStatusDetails(station.status);
  statusCapsule.textContent = statusInfo.text;
  statusCapsule.className = `station-status-capsule ${statusInfo.cls}`;

  // 收藏狀態
  const isFav = state.favorites.has(station.id);
  favBtn.textContent = isFav ? "❤️" : "🤍";
  favBtn.classList.toggle("favorited", isFav);

  // 屬性標籤
  const category = getTemperatureCategory(station);
  const color = getCategoryColor(category);

  tagsContainer.innerHTML = `
    <span class="meta-chip meta-chip-temp" style="background-color: ${color}">
      ● ${getCategoryLabel(category)}
    </span>
    <span class="meta-chip">🏢 ${station.floorHint || "1F 飲水機"}</span>
    <span class="meta-chip">🚪 ${getAccessLabel(station.accessType)}</span>
    <span class="meta-chip">🕒 ${station.is24Hours ? "24H 全天開放" : station.openHours || "依場館時間"}</span>
  `;

  // Google Maps 導航按鈕連結
  const googleNav = document.getElementById("btn-google-nav");
  googleNav.href = `https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=walking`;

  // 計算步行導航
  const routeCard = document.getElementById("route-card");
  const routeSummary = document.getElementById("route-summary");
  if (state.userLocation) {
    routeCard.classList.remove("hidden");
    routeSummary.textContent = "正在計算步行路線...";
    const route = await ExternalServices.fetchWalkingRoute(
      state.userLocation[0],
      state.userLocation[1],
      station.longitude,
      station.latitude
    );
    routeSummary.textContent = `步行約 ${formatDistance(route.distanceMeters)}・約 ${Math.max(
      1,
      Math.round(route.durationSeconds / 60)
    )} 分鐘`;
    drawWalkingRoute(route.coordinates);
  } else {
    routeCard.classList.add("hidden");
    clearWalkingRoute();
  }

  // 渲染照片牆
  renderPhotoGallery(station);

  // 渲染評價
  renderReviews(station.id);

  sheet.classList.remove("hidden");
  showBackdrop(true);
}

function renderPhotoGallery(station) {
  const gallery = document.getElementById("photo-gallery");
  const stationReviews = state.reviews.filter((r) => r.stationId === station.id && r.photoUri);
  const photos = [];
  if (station.photoUri) photos.push(station.photoUri);
  for (const r of stationReviews) {
    if (r.photoUri && !photos.includes(r.photoUri)) photos.push(r.photoUri);
  }

  if (photos.length === 0) {
    gallery.innerHTML = `<div class="no-photo-hint">尚無照片，歡迎在下方新增評價時拍照上傳！</div>`;
    return;
  }

  gallery.innerHTML = photos
    .map(
      (src) => `<img src="${src}" class="gallery-thumbnail" alt="飲水機照片" onclick="openPhotoViewer('${src}')">`
    )
    .join("");
}

function renderReviews(stationId) {
  const stationReviews = state.reviews.filter((r) => r.stationId === stationId);
  const summaryBadge = document.getElementById("rating-summary-badge");
  const list = document.getElementById("reviews-list");

  if (stationReviews.length === 0) {
    summaryBadge.textContent = "⭐ 尚無評分";
    list.innerHTML = `<div class="no-photo-hint" style="text-align: center;">成為第一位留下評價或回報水質的使用者！</div>`;
    return;
  }

  const avg = (
    stationReviews.reduce((sum, r) => sum + (r.rating || 5), 0) / stationReviews.length
  ).toFixed(1);
  summaryBadge.textContent = `⭐ ${avg} (${stationReviews.length} 則)`;

  list.innerHTML = stationReviews
    .map((r) => {
      const stars = "★".repeat(r.rating || 5) + "☆".repeat(5 - (r.rating || 5));
      const dateStr = new Date(r.createdAtEpochMillis || Date.now()).toLocaleDateString("zh-TW");
      const tagsHtml = (r.tags || [])
        .map((t) => `<span class="review-tag-mini">${t}</span>`)
        .join("");
      const photoHtml = r.photoUri
        ? `<img src="${r.photoUri}" class="review-photo" alt="評價照片" onclick="openPhotoViewer('${r.photoUri}')">`
        : "";

      return `
        <div class="review-card">
          <div class="review-card-header">
            <span class="review-author">👤 ${r.authorName || r.userName || "租屋拎水客"}</span>
            <span class="review-date">${dateStr}</span>
          </div>
          <div class="review-stars">${stars}</div>
          ${tagsHtml ? `<div class="review-tags">${tagsHtml}</div>` : ""}
          <div class="review-comment">${r.comment || "點位正常供水。"}</div>
          ${photoHtml}
        </div>
      `;
    })
    .join("");
}

window.openPhotoViewer = function (src) {
  const modal = document.getElementById("photo-modal");
  document.getElementById("photo-viewer-img").src = src;
  modal.classList.remove("hidden");
};

// ============================================================================
// 8. 列表檢視面板 (Station List Sheet)
// ============================================================================
function openStationListSheet() {
  closeAllSheets();
  renderStationListItems();
  document.getElementById("list-sheet").classList.remove("hidden");
  showBackdrop(true);
}

function renderStationListItems() {
  const container = document.getElementById("station-list-items");
  const query = document.getElementById("list-search-input").value.trim().toLowerCase();

  let list = [...state.stations].filter((s) => s.status !== "REMOVED");

  // 分頁過濾
  if (state.currentListTab === "favorite") {
    list = list.filter((s) => state.favorites.has(s.id));
  } else if (state.currentListTab === "iced") {
    list = list.filter((s) => s.hasIced);
  } else if (state.currentListTab === "warm") {
    list = list.filter((s) => s.hasWarm && s.hasHot && !s.hasIced);
  } else if (state.currentListTab === "hot") {
    list = list.filter((s) => !s.hasIced && !(s.hasWarm && s.hasHot));
  }

  // 搜尋過濾
  if (query) {
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(query) ||
        (s.floorHint && s.floorHint.toLowerCase().includes(query))
    );
  }

  // 依與使用者位置距離排序
  if (state.userLocation) {
    list.sort((a, b) => {
      const distA = haversineMeters(state.userLocation[1], state.userLocation[0], a.latitude, a.longitude);
      const distB = haversineMeters(state.userLocation[1], state.userLocation[0], b.latitude, b.longitude);
      return distA - distB;
    });
  }

  if (list.length === 0) {
    container.innerHTML = `<div class="no-photo-hint" style="text-align: center; padding: 32px 0;">找不到相符的飲水機點位</div>`;
    return;
  }

  container.innerHTML = list
    .map((s) => {
      const cat = getTemperatureCategory(s);
      const color = getCategoryColor(cat);
      let distText = "";
      if (state.userLocation) {
        const d = haversineMeters(state.userLocation[1], state.userLocation[0], s.latitude, s.longitude);
        distText = formatDistance(d);
      }

      return `
        <div class="station-list-item" onclick="selectStationById('${s.id}')">
          <div class="station-item-dot" style="background-color: ${color}"></div>
          <div class="station-item-info">
            <div class="station-item-name">${s.name}</div>
            <div class="station-item-sub">${s.floorHint || "1F 飲水機"}・${getAccessLabel(s.accessType)}</div>
            <div class="station-item-tags">
              <span class="tag-badge">${getCategoryLabel(cat)}</span>
              ${s.is24Hours ? '<span class="tag-badge">24H</span>' : ""}
              ${state.favorites.has(s.id) ? '<span class="tag-badge" style="color:#E53935">★ 收藏</span>' : ""}
            </div>
          </div>
          ${distText ? `<div class="station-item-distance">${distText}</div>` : ""}
        </div>
      `;
    })
    .join("");
}

window.selectStationById = function (id) {
  const station = state.stations.find((s) => s.id === id);
  if (station) {
    selectStation(station);
  }
};

// ============================================================================
// 9. 新增飲水機模式 (Add Station Sheet)
// ============================================================================
async function enterAddStationMode() {
  closeAllSheets();
  state.isAddingStation = true;
  document.getElementById("center-crosshair").classList.remove("hidden");
  document.getElementById("add-sheet").classList.remove("hidden");
  showBackdrop(false); // 保持地圖可自由拖曳移動

  updateAddPickerPosition();
}

async function updateAddPickerPosition() {
  if (!state.isAddingStation || !state.map) return;
  const center = state.map.getCenter();
  state.addCoords = [center.lng, center.lat];

  const coordsText = document.getElementById("picker-coords-text");
  const addressText = document.getElementById("picker-address-text");
  coordsText.textContent = `經度: ${center.lng.toFixed(5)} , 緯度: ${center.lat.toFixed(5)}`;
  addressText.textContent = "正在取得門牌地址...";

  const addr = await ExternalServices.reverseGeocode(center.lat, center.lng);
  addressText.textContent = addr;
}

// ============================================================================
// 10. 圖釘調色盤設定 (Color Preferences Modal)
// ============================================================================
function openColorPreferencesModal() {
  closeAllSheets();
  renderColorPaletteOptions();
  document.getElementById("color-modal").classList.remove("hidden");
  showBackdrop(true);
}

function renderColorPaletteOptions() {
  const categories = ["ICED_TRIPLE", "WARM_HOT", "HOT_OR_SINGLE"];
  const ids = {
    ICED_TRIPLE: "palette-iced",
    WARM_HOT: "palette-warm",
    HOT_OR_SINGLE: "palette-hot",
  };

  for (const cat of categories) {
    const container = document.getElementById(ids[cat]);
    const currentColor = state.markerColors[cat];

    container.innerHTML = PRESET_MARKER_COLORS.map(
      (opt) => `
        <div class="palette-swatch ${opt.hex.toUpperCase() === currentColor.toUpperCase() ? "active" : ""}"
             style="background-color: ${opt.hex}"
             title="${opt.name}"
             onclick="selectPaletteColor('${cat}', '${opt.hex}')">
        </div>
      `
    ).join("");
  }
}

window.selectPaletteColor = function (category, hex) {
  state.markerColors[category] = hex;
  DataSyncService.saveColors(state.markerColors);
  renderColorPaletteOptions();
  updateMarkerColorsOnMap();
};

// ============================================================================
// 11. 初始化與事件綁定 (App Initialization)
// ============================================================================
async function initApp() {
  // 1. 載入本地設定與偏好
  DataSyncService.loadStoredPreferences();

  // 2. 初始化 MapLibre 地圖
  state.map = new maplibregl.Map({
    container: "map",
    style: OPENFREEMAP_STYLE,
    center: DEFAULT_CENTER,
    zoom: DEFAULT_ZOOM,
    attributionControl: false,
  });

  state.map.addControl(new maplibregl.AttributionControl({ compact: true }), "bottom-left");

  // 3. 載入站點資料庫
  const dataset = await DataSyncService.fetchStationsAndReviews();
  renderAllMarkers();

  // 地圖移動事件 (更新新增飲水機時的十字準星座標)
  let moveTimeout = null;
  state.map.on("move", () => {
    if (state.isAddingStation) {
      clearTimeout(moveTimeout);
      moveTimeout = setTimeout(() => {
        updateAddPickerPosition();
      }, 250);
    }
  });

  state.map.on("click", () => {
    if (!state.isAddingStation) {
      closeAllSheets();
    }
  });

  // 4. 定位使用者位置 (HTML5 Geolocation)
  initUserLocation();

  // 5. 綁定按鈕與互動事件
  bindEventHandlers();
}

function initUserLocation() {
  if (!navigator.geolocation) return;

  navigator.geolocation.getCurrentPosition(
    (pos) => {
      const lng = pos.coords.longitude;
      const lat = pos.coords.latitude;
      state.userLocation = [lng, lat];

      // 建立使用者位置標記
      if (!state.userMarker) {
        const el = document.createElement("div");
        el.className = "user-location-marker";
        state.userMarker = new maplibregl.Marker({ element: el })
          .setLngLat([lng, lat])
          .addTo(state.map);
      } else {
        state.userMarker.setLngLat([lng, lat]);
      }

      state.map.flyTo({ center: [lng, lat], zoom: 15 });
      updateNearestStationBanner();
    },
    (err) => {
      console.log("定位提示:", err.message);
    },
    { enableHighAccuracy: true, timeout: 8000 }
  );
}

function updateNearestStationBanner() {
  if (!state.userLocation || state.stations.length === 0) return;
  let nearest = null;
  let minDist = Infinity;

  for (const s of state.stations) {
    if (s.status === "REMOVED") continue;
    const d = haversineMeters(state.userLocation[1], state.userLocation[0], s.latitude, s.longitude);
    if (d < minDist) {
      minDist = d;
      nearest = s;
    }
  }

  if (nearest && minDist < 5000) {
    const banner = document.getElementById("nearest-banner");
    document.getElementById("nearest-name").textContent = nearest.name;
    document.getElementById("nearest-distance").textContent = formatDistance(minDist);
    banner.classList.remove("hidden");

    document.getElementById("btn-nearest-go").onclick = () => {
      selectStation(nearest);
    };
  }
}

function bindEventHandlers() {
  // 篩選晶片
  const filterChips = document.querySelectorAll(".filter-chip[data-filter]");
  filterChips.forEach((chip) => {
    chip.addEventListener("click", () => {
      const f = chip.dataset.filter;
      state.activeFilters[f] = !state.activeFilters[f];
      chip.classList.toggle("active", state.activeFilters[f]);
      renderAllMarkers();
    });
  });

  // 圖釘調色盤按鈕
  document.getElementById("legend-btn").addEventListener("click", () => {
    openColorPreferencesModal();
  });

  // FAB 按鈕群
  document.getElementById("btn-zoom-in").addEventListener("click", () => {
    state.map.zoomIn();
  });
  document.getElementById("btn-zoom-out").addEventListener("click", () => {
    state.map.zoomOut();
  });
  document.getElementById("btn-locate").addEventListener("click", () => {
    initUserLocation();
  });
  document.getElementById("btn-open-list").addEventListener("click", () => {
    openStationListSheet();
  });
  document.getElementById("btn-open-add").addEventListener("click", () => {
    enterAddStationMode();
  });

  // 關閉各面板
  document.getElementById("backdrop").addEventListener("click", closeAllSheets);
  document.getElementById("btn-close-detail").addEventListener("click", closeAllSheets);
  document.getElementById("btn-close-list").addEventListener("click", closeAllSheets);
  document.getElementById("btn-close-add").addEventListener("click", closeAllSheets);
  document.getElementById("btn-close-color").addEventListener("click", closeAllSheets);
  document.getElementById("btn-close-photo").addEventListener("click", () => {
    document.getElementById("photo-modal").classList.add("hidden");
  });

  // 收藏按鈕切換
  document.getElementById("btn-toggle-favorite").addEventListener("click", () => {
    if (!state.selectedStation) return;
    const isFav = DataSyncService.toggleFavorite(state.selectedStation.id);
    const favBtn = document.getElementById("btn-toggle-favorite");
    favBtn.textContent = isFav ? "❤️" : "🤍";
    favBtn.classList.toggle("favorited", isFav);
    renderAllMarkers();
  });

  // 回報機況按鈕與狀態切換
  const reportPanel = document.getElementById("status-report-panel");
  document.getElementById("btn-report-status").addEventListener("click", () => {
    reportPanel.classList.toggle("hidden");
  });

  document.querySelectorAll(".status-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      if (!state.selectedStation) return;
      const status = btn.dataset.status;
      await DataSyncService.updateStatus(state.selectedStation.id, status);
      const statusInfo = getStatusDetails(status);
      const capsule = document.getElementById("detail-status-capsule");
      capsule.textContent = statusInfo.text;
      capsule.className = `station-status-capsule ${statusInfo.cls}`;
      reportPanel.classList.add("hidden");
      alert("感謝您的回報！機況已更新並同步至伺服器。");
    });
  });

  // 分享點位
  document.getElementById("btn-share-station").addEventListener("click", () => {
    if (!state.selectedStation) return;
    const shareText = `【拎水地圖】${state.selectedStation.name}\n位置：${state.selectedStation.floorHint}\nhttps://www.google.com/maps/search/?api=1&query=${state.selectedStation.latitude},${state.selectedStation.longitude}`;
    if (navigator.share) {
      navigator.share({ title: state.selectedStation.name, text: shareText }).catch(() => {});
    } else {
      navigator.clipboard.writeText(shareText).then(() => {
        alert("已複製飲水機點位資訊至剪貼簿！");
      });
    }
  });

  // 快速標籤清單生成與選擇
  const tagsContainer = document.getElementById("quick-tags-container");
  tagsContainer.innerHTML = QUICK_REVIEW_TAGS.map(
    (tag) => `<button type="button" class="tag-chip" data-tag="${tag}">${tag}</button>`
  ).join("");

  tagsContainer.querySelectorAll(".tag-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      const tag = chip.dataset.tag;
      if (state.selectedReviewTags.has(tag)) {
        state.selectedReviewTags.delete(tag);
        chip.classList.remove("selected");
      } else {
        state.selectedReviewTags.add(tag);
        chip.classList.add("selected");
      }
    });
  });

  // 星星評分選擇
  const starEls = document.querySelectorAll("#star-input .star");
  starEls.forEach((star) => {
    star.addEventListener("click", () => {
      const val = Number(star.dataset.val);
      state.reviewRating = val;
      starEls.forEach((s) => {
        const sVal = Number(s.dataset.val);
        s.classList.toggle("active", sVal <= val);
      });
    });
  });

  // 評價照片上傳 (Canvas 壓縮)
  const reviewPhotoInput = document.getElementById("review-photo-input");
  reviewPhotoInput.addEventListener("change", async (e) => {
    const file = e.target.files[0];
    if (file) {
      try {
        state.reviewPhotoDataUrl = await compressImage(file);
        document.getElementById("review-preview-img").src = state.reviewPhotoDataUrl;
        document.getElementById("review-photo-preview").classList.remove("hidden");
      } catch (err) {
        alert("照片讀取失敗：" + err.message);
      }
    }
  });

  document.getElementById("btn-remove-review-photo").addEventListener("click", () => {
    state.reviewPhotoDataUrl = null;
    reviewPhotoInput.value = "";
    document.getElementById("review-photo-preview").classList.add("hidden");
  });

  // 送出評價
  document.getElementById("add-review-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!state.selectedStation) return;

    const author = document.getElementById("review-author").value.trim() || "租屋拎水客";
    const comment = document.getElementById("review-comment").value.trim();
    const tags = Array.from(state.selectedReviewTags);

    const payload = {
      authorName: author,
      userName: author,
      rating: state.reviewRating,
      comment: comment || "點位出水正常！",
      tags,
      photoUri: state.reviewPhotoDataUrl || null,
    };

    const submitBtn = document.getElementById("btn-submit-review");
    submitBtn.disabled = true;
    submitBtn.textContent = "傳送中...";

    try {
      await DataSyncService.addReview(state.selectedStation.id, payload);
      renderReviews(state.selectedStation.id);
      renderPhotoGallery(state.selectedStation);

      // 重設表單
      document.getElementById("review-comment").value = "";
      state.reviewPhotoDataUrl = null;
      reviewPhotoInput.value = "";
      document.getElementById("review-photo-preview").classList.add("hidden");
      state.selectedReviewTags.clear();
      tagsContainer.querySelectorAll(".tag-chip").forEach((c) => c.classList.remove("selected"));

      alert("評價發布成功！已同步至拎水資料庫。");
    } catch (err) {
      alert("評價送出失敗：" + err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = "送出評價與照片";
    }
  });

  // 列表搜尋與 Tab 切換
  document.getElementById("list-search-input").addEventListener("input", () => {
    renderStationListItems();
  });

  document.querySelectorAll(".list-tab-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      document.querySelectorAll(".list-tab-chip").forEach((c) => c.classList.remove("active"));
      chip.classList.add("active");
      state.currentListTab = chip.dataset.tab;
      renderStationListItems();
    });
  });

  // 新增飲水機 - 照片上傳
  const addPhotoInput = document.getElementById("add-photo-input");
  addPhotoInput.addEventListener("change", async (e) => {
    const file = e.target.files[0];
    if (file) {
      try {
        state.addPhotoDataUrl = await compressImage(file);
        document.getElementById("add-preview-img").src = state.addPhotoDataUrl;
        document.getElementById("add-photo-preview").classList.remove("hidden");
      } catch (err) {
        alert("照片讀取失敗：" + err.message);
      }
    }
  });

  document.getElementById("btn-remove-add-photo").addEventListener("click", () => {
    state.addPhotoDataUrl = null;
    addPhotoInput.value = "";
    document.getElementById("add-photo-preview").classList.add("hidden");
  });

  // 新增飲水機 - 定位按鈕與地址搜尋
  document.getElementById("btn-use-my-location").addEventListener("click", () => {
    if (state.userLocation) {
      state.map.flyTo({ center: state.userLocation, zoom: 17 });
      setTimeout(updateAddPickerPosition, 400);
    } else {
      initUserLocation();
    }
  });

  document.getElementById("btn-search-address").addEventListener("click", async () => {
    const query = document.getElementById("add-address-search").value.trim();
    if (!query) return;
    const results = await ExternalServices.searchAddress(query);
    if (results.length > 0) {
      const top = results[0];
      const lng = Number(top.lon);
      const lat = Number(top.lat);
      state.map.flyTo({ center: [lng, lat], zoom: 17 });
      setTimeout(updateAddPickerPosition, 500);
    } else {
      alert("找不到該地址，請嘗試輸入更詳細的路名或地標。");
    }
  });

  // 新增飲水機 - 送出表單
  document.getElementById("add-station-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!state.addCoords) return;

    const name = document.getElementById("add-name").value.trim();
    const floor = document.getElementById("add-floor").value.trim() || "1F 飲水機";
    const access = document.getElementById("add-access").value;
    const hours = document.getElementById("add-hours").value.trim() || "全天開放";
    const is24h = document.getElementById("add-is-24h").checked;

    const hasIced = document.getElementById("add-temp-iced").checked;
    const hasCold = document.getElementById("add-temp-cold").checked;
    const hasWarm = document.getElementById("add-temp-warm").checked;
    const hasHot = document.getElementById("add-temp-hot").checked;

    const payload = {
      name,
      latitude: state.addCoords[1],
      longitude: state.addCoords[0],
      floorHint: floor,
      accessType: access,
      hasHot,
      hasWarm,
      hasCold,
      hasIced,
      is24Hours: is24h,
      openHours: hours,
      status: "WORKING",
      photoUri: state.addPhotoDataUrl || null,
    };

    const submitBtn = document.getElementById("btn-submit-station");
    submitBtn.disabled = true;
    submitBtn.textContent = "儲存中...";

    try {
      const created = await DataSyncService.addStation(payload);
      renderAllMarkers();
      closeAllSheets();
      alert(`新增飲水機「${created.name}」成功！`);
      selectStation(created);
    } catch (err) {
      alert("新增失敗：" + err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = "確認新增並發布";
    }
  });

  // 調色盤設定按鈕
  document.getElementById("btn-reset-colors").addEventListener("click", () => {
    state.markerColors = { ...DEFAULT_CATEGORY_COLORS };
    DataSyncService.saveColors(state.markerColors);
    renderColorPaletteOptions();
    updateMarkerColorsOnMap();
  });

  document.getElementById("btn-confirm-colors").addEventListener("click", () => {
    closeAllSheets();
  });

  // OSM Overpass 探索此區域飲水機
  document.getElementById("chip-explore-osm").addEventListener("click", async () => {
    const banner = document.getElementById("osm-banner");
    const center = state.map.getCenter();
    banner.textContent = "🔍 正在向 OpenStreetMap Overpass 查詢此區域真實飲水機...";
    banner.classList.remove("hidden");

    try {
      const discovered = await ExternalServices.exploreNearbyOsmWater(center.lat, center.lng, 2500);
      let newCount = 0;
      for (const d of discovered) {
        // 檢查是否已存在 (以 20 公尺內近似座標判定)
        const exists = state.stations.some(
          (s) => haversineMeters(s.latitude, s.longitude, d.latitude, d.longitude) < 20
        );
        if (!exists) {
          state.stations.push(d);
          newCount++;
        }
      }

      renderAllMarkers();
      banner.textContent = `✅ 探索完成！共發現 ${discovered.length} 個點位，新增 ${newCount} 處飲水機！`;
      setTimeout(() => {
        banner.classList.add("hidden");
      }, 5000);
    } catch (err) {
      banner.textContent = `⚠️ 探索失敗：${err.message || "伺服器忙碌中"}`;
      setTimeout(() => {
        banner.classList.add("hidden");
      }, 4000);
    }
  });
}

// 啟動 App
document.addEventListener("DOMContentLoaded", initApp);
