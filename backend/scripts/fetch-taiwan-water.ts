import { existsSync, mkdirSync, readFileSync, writeFileSync } from "fs";
import { resolve, dirname } from "path";

interface WaterStation {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  floorHint: string;
  accessType: "PUBLIC" | "SEMI_PUBLIC" | "RESTRICTED";
  hasHot: boolean;
  hasWarm: boolean;
  hasCold: boolean;
  hasIced: boolean;
  is24Hours: boolean;
  openHours: string;
  status: "WORKING" | "OUT_OF_ORDER" | "FILTER_EXPIRED" | "REMOVED";
  photoUri: string | null;
  lastVerifiedEpochMillis: number;
}

interface StationReview {
  id: string;
  stationId: string;
  userId: string;
  userName: string;
  authorName: string;
  rating: number;
  comment: string;
  tags: string[];
  photoUri: string | null;
  createdAtEpochMillis: number;
}

interface OpenWaterDataset {
  version: number;
  updatedAt: string;
  license: string;
  source: string;
  stations: WaterStation[];
  reviews: StationReview[];
}

// Curated high-confidence public water stations across all regions of Taiwan
const CURATED_TAIWAN_STATIONS: WaterStation[] = [
  // === 雙北地區 (Taipei & New Taipei) ===
  {
    id: "tpe-main-station-b1",
    name: "台北車站 B1 誠品捷運連通道飲水機",
    latitude: 25.0462,
    longitude: 121.5174,
    floorHint: "B1 往捷運淡水信義線閘門旁、洗手間外",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "06:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tpe-library-daan",
    name: "臺北市立圖書館總館 1F 飲水機",
    latitude: 25.0289,
    longitude: 121.5378,
    floorHint: "1F 服務台後方茶水間（免換證可直接入內）",
    accessType: "SEMI_PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "08:30 - 21:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ntu-total-library-b1",
    name: "台大總圖書館 B1 自習室外部飲水機",
    latitude: 25.0174,
    longitude: 121.5405,
    floorHint: "B1 國際會議廳走廊旁（免刷學生證區域）",
    accessType: "SEMI_PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "08:00 - 22:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "daan-park-service-center",
    name: "大安森林公園 遊客服務中心旁飲水機",
    latitude: 25.0315,
    longitude: 121.5355,
    floorHint: "公園中央服務中心廁所走廊",
    accessType: "PUBLIC",
    hasHot: false,
    hasWarm: false,
    hasCold: true,
    hasIced: false,
    is24Hours: true,
    openHours: "24 小時開放",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tpe-songshan-station-1f",
    name: "松山車站 1F 大廳台鐵服務台側",
    latitude: 25.0494,
    longitude: 121.5779,
    floorHint: "1F 東出口候車室洗手間側",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "05:30 - 23:50",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tpe-nangang-station-b2",
    name: "南港車站 B2 高鐵候車區飲水機",
    latitude: 25.0521,
    longitude: 121.6068,
    floorHint: "B2 高鐵剪票口外通道洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "06:00 - 23:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ntpc-banqiao-station-1f",
    name: "板橋車站 1F 台鐵／高鐵東門候車大廳",
    latitude: 25.0142,
    longitude: 121.4636,
    floorHint: "1F 售票大廳往東門通道洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "06:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ntpc-banqiao-cityhall-1f",
    name: "新北市政府 1F 東側聯合服務中心飲水機",
    latitude: 25.0125,
    longitude: 121.4657,
    floorHint: "1F 聯合服務大廳近茶水間",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "08:00 - 18:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ntpc-library-banqiao-main",
    name: "新北市立圖書館總館 1F 門廳飲水機",
    latitude: 24.9982,
    longitude: 121.4532,
    floorHint: "1F 服務台後方通道洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "08:30 - 21:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ntpc-tamsui-mrt-1f",
    name: "捷運淡水站 1F 旅客詢問處旁飲水機",
    latitude: 25.1678,
    longitude: 121.4455,
    floorHint: "1F 閘門出口往金色水岸廣場洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "06:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },

  // === 桃竹苗地區 (Taoyuan, Hsinchu, Miaoli) ===
  {
    id: "ty-station-old-front",
    name: "桃園車站 前站候車大廳飲水機",
    latitude: 24.9892,
    longitude: 121.3135,
    floorHint: "1F 前站出入口剪票閘門旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:00 - 23:50",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ty-main-library-1f",
    name: "桃園市立圖書館新總館 1F 服務大廳",
    latitude: 25.0177,
    longitude: 121.3005,
    floorHint: "1F 生命樹入口大廳洗手間走廊",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "08:30 - 21:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ty-hsr-station-1f",
    name: "高鐵桃園站 1F 售票大廳飲水機",
    latitude: 25.0129,
    longitude: 121.2148,
    floorHint: "1F 8號出口旁、近洗手間通道",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "06:00 - 23:45",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "hsinchu-station-main-hall",
    name: "新竹火車站 候車大廳飲水機",
    latitude: 24.8016,
    longitude: 120.9715,
    floorHint: "1F 候車大廳第1月台側洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:30 - 23:40",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "hsinchu-hsr-station-1f",
    name: "高鐵新竹站 1F 候車區飲水機",
    latitude: 24.8083,
    longitude: 121.0401,
    floorHint: "1F 驗票閘門外近4號出口旁洗手間",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "06:00 - 23:45",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "nthu-main-library-1f",
    name: "清華大學 學習資源中心(總圖) 1F 茶水間",
    latitude: 24.7938,
    longitude: 120.9934,
    floorHint: "旺宏館 1F 門廳側洗手間旁（免證入內）",
    accessType: "SEMI_PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "08:00 - 22:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "miaoli-station-1f",
    name: "苗栗火車站 跨站大廳飲水機",
    latitude: 24.5701,
    longitude: 120.8236,
    floorHint: "2F 跨站售票大廳東出口洗手間外",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:30 - 23:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },

  // === 中部地區 (Taichung, Changhua, Nantou) ===
  {
    id: "txg-station-main-2f",
    name: "台中火車站 2F 售票大廳飲水機",
    latitude: 24.1373,
    longitude: 120.6869,
    floorHint: "2F 穿堂層剪票口前洗手間外側",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "05:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "txg-hsr-station-2f",
    name: "高鐵台中烏日站 2F 候車大廳飲水機",
    latitude: 24.1118,
    longitude: 120.6157,
    floorHint: "2F 乘車區洗手間通道旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "05:40 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "txg-public-library-1f",
    name: "國立公共資訊圖書館 (台中總館) 1F 門廳",
    latitude: 24.1264,
    longitude: 120.6685,
    floorHint: "1F 大廳服務台後方飲水區",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "09:00 - 21:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "txg-nchu-library-1f",
    name: "中興大學 圖書暨資訊處 1F 飲水機",
    latitude: 24.1206,
    longitude: 120.6753,
    floorHint: "1F 自習大廳外走廊飲水台",
    accessType: "SEMI_PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "08:00 - 22:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ch-station-1f",
    name: "彰化火車站 1F 候車室飲水機",
    latitude: 24.0818,
    longitude: 120.5385,
    floorHint: "1F 候車剪票閘口旁洗手間外",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:00 - 23:50",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "nantou-county-hall-1f",
    name: "南投縣政府 1F 聯合服務中心茶水間",
    latitude: 23.9098,
    longitude: 120.6865,
    floorHint: "1F 縣民服務大廳洗手間走廊",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "08:00 - 17:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },

  // === 雲嘉南地區 (Yunlin, Chiayi, Tainan) ===
  {
    id: "douliu-station-2f",
    name: "斗六火車站 2F 跨站候車大廳飲水機",
    latitude: 23.7118,
    longitude: 120.5434,
    floorHint: "2F 穿堂剪票口旁洗手間通道",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:20 - 23:40",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "chiayi-station-1f",
    name: "嘉義火車站 前站候車大廳飲水機",
    latitude: 23.4792,
    longitude: 120.4412,
    floorHint: "1F 前站出入口往第1月台通道",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:00 - 23:50",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tainan-station-main-1f",
    name: "台南火車站 前站大廳候車室飲水機",
    latitude: 22.9971,
    longitude: 120.2129,
    floorHint: "1F 前站剪票口側洗手間走廊",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tainan-hsr-station-1f",
    name: "高鐵台南沙崙站 1F 售票大廳飲水機",
    latitude: 22.9248,
    longitude: 120.2858,
    floorHint: "1F 候車大廳近2號出口洗手間外",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "06:00 - 23:45",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tainan-main-library-1f",
    name: "台南市立圖書館新總館 1F 服務台側",
    latitude: 23.0163,
    longitude: 120.2372,
    floorHint: "1F 大廳服務台後方飲水區",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "08:30 - 21:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "ncku-library-1f",
    name: "成功大學 總圖書館 1F 門廳茶水間",
    latitude: 22.9998,
    longitude: 120.2185,
    floorHint: "1F 自修閱覽室旁走廊（免證入內飲水）",
    accessType: "SEMI_PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "08:00 - 22:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },

  // === 高屏地區 (Kaohsiung, Pingtung) ===
  {
    id: "kh-station-b2",
    name: "高雄火車站 B2 捷運／台鐵轉乘穿堂飲水機",
    latitude: 22.6397,
    longitude: 120.3023,
    floorHint: "B2 穿堂層近台鐵剪票口洗手間外",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "05:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "kh-zuoying-hsr-2f",
    name: "高鐵左營站 2F 大廳候車區飲水機",
    latitude: 22.6874,
    longitude: 120.3079,
    floorHint: "2F 乘車閘門外洗手間走廊",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "05:45 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "kh-main-library-1f",
    name: "高雄市立圖書館總館 1F 穿堂飲水機",
    latitude: 22.6097,
    longitude: 120.3015,
    floorHint: "1F 半戶外穿堂近圖書館入口處",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "10:00 - 22:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "pingtung-station-1f",
    name: "屏東火車站 1F 候車大廳飲水機",
    latitude: 22.6698,
    longitude: 120.4862,
    floorHint: "1F 售票剪票大廳洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:00 - 23:45",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "pingtung-main-library-1f",
    name: "屏東縣立圖書館總館 1F 門廳森林飲水區",
    latitude: 22.6853,
    longitude: 120.4952,
    floorHint: "1F 森林大廳洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "09:00 - 21:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },

  // === 宜花東地區 (Yilan, Hualien, Taitung) ===
  {
    id: "yilan-station-1f",
    name: "宜蘭火車站 前站候車大廳飲水機",
    latitude: 24.7547,
    longitude: 121.7584,
    floorHint: "1F 幾米幾何門面旁、候車剪票閘口側",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:30 - 23:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "luodong-station-2f",
    name: "羅東火車站 2F 跨站售票候車大廳",
    latitude: 24.6775,
    longitude: 121.7725,
    floorHint: "2F 售票大廳往第二月台通道旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:20 - 23:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "hualien-station-3f",
    name: "花蓮火車站 3F 候車大廳飲水機",
    latitude: 23.9932,
    longitude: 121.6015,
    floorHint: "3F 挑高跨站候車大廳、剪票口旁洗手間外",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: true,
    is24Hours: false,
    openHours: "05:00 - 24:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "taitung-station-1f",
    name: "台東火車站 1F 候車大廳飲水機",
    latitude: 22.7937,
    longitude: 121.1235,
    floorHint: "1F 候車剪票口前洗手間外側",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "05:20 - 23:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },

  // === 單溫或純熱水代表點位（紅色標記 HOT_OR_SINGLE） ===
  {
    id: "tpe-longshan-temple-service",
    name: "艋舺龍山寺 淨手水台旁純熱水奉茶機",
    latitude: 25.0369,
    longitude: 121.4999,
    floorHint: "廟埕側邊服務台旁奉茶桶（純熱水沖茶）",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: false,
    hasCold: false,
    hasIced: false,
    is24Hours: false,
    openHours: "06:00 - 22:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "txg-dajia-jenn-lann-temple",
    name: "大甲鎮瀾宮 地下室文化館熱水奉茶機",
    latitude: 24.3444,
    longitude: 120.6231,
    floorHint: "B1 媽祖文化館洗手間外側",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: false,
    hasCold: false,
    hasIced: false,
    is24Hours: false,
    openHours: "06:00 - 21:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tpe-elephant-mountain-trail-start",
    name: "象山步道 靈天宮登山口直飲水機",
    latitude: 25.0275,
    longitude: 121.5746,
    floorHint: "靈天宮旁涼亭直飲水台（單一常溫水）",
    accessType: "PUBLIC",
    hasHot: false,
    hasWarm: false,
    hasCold: true,
    hasIced: false,
    is24Hours: true,
    openHours: "24 小時開放",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "tpe-yangmingshan-visitor-center",
    name: "陽明山國家公園遊客中心 1F 飲水機",
    latitude: 25.1558,
    longitude: 121.5475,
    floorHint: "1F 服務台旁洗手間通道",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "09:00 - 16:30",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
  {
    id: "sun-moon-lake-xiangshan-visitor",
    name: "日月潭向山行政暨遊客中心 1F 飲水機",
    latitude: 23.8519,
    longitude: 120.9018,
    floorHint: "1F 迴廊洗手間旁",
    accessType: "PUBLIC",
    hasHot: true,
    hasWarm: true,
    hasCold: true,
    hasIced: false,
    is24Hours: false,
    openHours: "09:00 - 17:00",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  },
];

const CURATED_REVIEWS: StationReview[] = [
  {
    id: "rev-seed-tpe-1",
    stationId: "tpe-main-station-b1",
    userId: "user-taipei-commuter",
    userName: "北捷通勤小資",
    authorName: "北捷通勤小資",
    rating: 5,
    comment: "水溫非常正常，水量也很大，常常在轉乘淡水線前來裝滿一整壺水！",
    tags: ["出水量大", "水質甘甜", "標示清楚"],
    photoUri: null,
    createdAtEpochMillis: Date.now() - 86400000 * 2,
  },
  {
    id: "rev-seed-tpe-2",
    stationId: "tpe-library-daan",
    userId: "user-daan-reader",
    userName: "大安自修族",
    authorName: "大安自修族",
    rating: 5,
    comment: "這台有冰水！夏天讀書必備，免換證就能從大門走進去裝水，冷度很夠。",
    tags: ["有冰水", "安靜乾淨", "水溫適中"],
    photoUri: null,
    createdAtEpochMillis: Date.now() - 86400000 * 5,
  },
  {
    id: "rev-seed-txg-1",
    stationId: "txg-station-main-2f",
    userId: "user-taichung-traveler",
    userName: "台中旅人",
    authorName: "台中旅人",
    rating: 5,
    comment: "2F 穿堂的飲水機維護得很好，熱水夠燙泡麵沒問題，也有冰水可以喝。",
    tags: ["有冰水", "水溫適中", "位置好找"],
    photoUri: null,
    createdAtEpochMillis: Date.now() - 86400000 * 3,
  },
];

interface OsmElement {
  type: string;
  id: number;
  lat?: number;
  lon?: number;
  center?: { lat: number; lon: number };
  tags?: Record<string, string>;
}

const OVERPASS_ENDPOINTS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
  "https://overpass.private.coffee/api/interpreter",
];

const OVERPASS_USER_AGENT =
  "ZeroWaterMap/1.0 (+https://github.com/Stubby614/zero-water-map)";

/** 台灣本島與離島涵蓋範圍（含澎湖、金門、馬祖） */
const TAIWAN_BBOX = { south: 21.85, west: 119.2, north: 25.35, east: 122.1 };

/** 分塊數量（N×N）。單一巨量查詢會被 Overpass 排隊拒收或逾時，切成小區塊較穩定。 */
const TILE_GRID = Number.parseInt(process.env.ZWM_TILE_GRID ?? "5", 10);

interface Tile {
  south: number;
  west: number;
  north: number;
  east: number;
}

function buildTaiwanTiles(grid = TILE_GRID): Tile[] {
  const rows = Math.max(1, Math.min(grid, 12));
  const latStep = (TAIWAN_BBOX.north - TAIWAN_BBOX.south) / rows;
  const lonStep = (TAIWAN_BBOX.east - TAIWAN_BBOX.west) / rows;
  const tiles: Tile[] = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < rows; c++) {
      tiles.push({
        south: Number((TAIWAN_BBOX.south + latStep * r).toFixed(4)),
        north: Number((TAIWAN_BBOX.south + latStep * (r + 1)).toFixed(4)),
        west: Number((TAIWAN_BBOX.west + lonStep * c).toFixed(4)),
        east: Number((TAIWAN_BBOX.west + lonStep * (c + 1)).toFixed(4)),
      });
    }
  }
  return tiles;
}

/** 對 Overpass 依序嘗試端點；每個端點只試一次，避免單一分塊卡住整個流程 */
async function queryOverpass(query: string): Promise<OsmElement[]> {
  let lastError: Error | null = null;

  for (const endpoint of OVERPASS_ENDPOINTS) {
    try {
      const resp = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "User-Agent": OVERPASS_USER_AGENT,
          Accept: "*/*",
        },
        body: `data=${encodeURIComponent(query)}`,
        signal: AbortSignal.timeout(70_000),
      });

      if (!resp.ok) {
        lastError = new Error(`${endpoint} 回應 HTTP ${resp.status}`);
        continue;
      }

      const json = (await resp.json()) as { elements?: OsmElement[] };
      return json.elements ?? [];
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
    }
  }

  throw lastError ?? new Error("Overpass 查詢失敗");
}

/** 依人口密度排序分塊：西岸都會走廊優先，確保時間不足時先拿到最多點位 */
const PRIORITY_ANCHORS = [
  [25.05, 121.55],
  [24.8, 120.97],
  [24.15, 120.67],
  [23.48, 120.45],
  [23.0, 120.23],
  [22.63, 120.3],
  [25.13, 121.74],
  [24.75, 121.75],
  [23.98, 121.6],
  [22.76, 121.14],
];

function tilePriority(tile: Tile): number {
  const lat = (tile.south + tile.north) / 2;
  const lon = (tile.west + tile.east) / 2;
  let best = Number.POSITIVE_INFINITY;
  for (const [aLat, aLon] of PRIORITY_ANCHORS) {
    const d = (lat - aLat) ** 2 + (lon - aLon) ** 2;
    if (d < best) best = d;
  }
  return best;
}

function osmElementToStation(el: OsmElement): WaterStation | null {
  const lat = el.lat ?? el.center?.lat;
  const lon = el.lon ?? el.center?.lon;
  if (lat == null || lon == null) return null;

  const tags = el.tags ?? {};
  const nameRaw =
    tags.name || tags["name:zh"] || tags["name:en"] || tags.operator || tags.description;
  const title = nameRaw ? `${nameRaw} 飲水點` : "公共飲水機 (OSM)";
  const floor =
    (tags.level ? `${tags.level}F` : "") ||
    tags.location ||
    tags["addr:floor"] ||
    "戶外 / 地面層公開區域";

  // 僅在 OSM 有明確標記時才判定水溫，避免把未知誤判為「有冰水／有熱水」
  const hasIced = tags.iced === "yes" || tags["drinking_water:cold"] === "iced";
  const hasHot = tags.hot === "yes" || tags["drinking_water:hot"] === "yes";
  const hasWarm =
    tags.warm === "yes" || tags["drinking_water:warm"] === "yes" || hasHot;
  const hasCold = tags.cold !== "no";

  return {
    id: `osm-${el.type}-${el.id}`,
    name: title,
    latitude: Number(lat.toFixed(6)),
    longitude: Number(lon.toFixed(6)),
    floorHint: floor,
    accessType:
      tags.access === "private" || tags.access === "no"
        ? "RESTRICTED"
        : tags.access === "customers" || tags.fee === "yes"
        ? "SEMI_PUBLIC"
        : "PUBLIC",
    hasHot,
    hasWarm,
    hasCold,
    hasIced,
    is24Hours: tags.opening_hours === "24/7",
    openHours: tags.opening_hours || "公共開放",
    status: "WORKING",
    photoUri: null,
    lastVerifiedEpochMillis: Date.now(),
  };
}

async function fetchAllOsmDrinkingWater(): Promise<WaterStation[]> {
  if (process.env.ZWM_OSM === "0") {
    console.log("略過 Overpass（ZWM_OSM=0）");
    return [];
  }

  const tiles = buildTaiwanTiles().sort((a, b) => tilePriority(a) - tilePriority(b));
  const byId = new Map<string, WaterStation>();
  const budgetMs = Number.parseInt(process.env.ZWM_FETCH_BUDGET_MS ?? "1200000", 10);
  const startedAt = Date.now();
  let failedTiles = 0;
  let skippedTiles = 0;

  for (const [index, tile] of tiles.entries()) {
    if (Date.now() - startedAt > budgetMs) {
      skippedTiles = tiles.length - index;
      console.warn(`⏱️ 已達抓取時間預算，剩餘 ${skippedTiles} 個分塊略過`);
      break;
    }

    const bbox = `${tile.south},${tile.west},${tile.north},${tile.east}`;
    const query = `[out:json][timeout:60];
(
  node["amenity"="drinking_water"](${bbox});
  node["drinking_water"="yes"](${bbox});
);
out body;`;

    process.stdout.write(`[${index + 1}/${tiles.length}] Overpass ${bbox} ... `);
    try {
      const elements = await queryOverpass(query);
      let added = 0;
      for (const el of elements) {
        const station = osmElementToStation(el);
        if (!station || byId.has(station.id)) continue;
        byId.set(station.id, station);
        added++;
      }
      console.log(`${elements.length} 筆原始節點，新增 ${added} 處（累計 ${byId.size}）`);
    } catch (err) {
      failedTiles++;
      console.log(`失敗（${(err as Error).message}）`);
    }

    await Bun.sleep(1000);
  }

  if (failedTiles > 0 || skippedTiles > 0) {
    console.warn(
      `⚠️ ${failedTiles} 個分塊查詢失敗、${skippedTiles} 個分塊因時間預算略過，資料可能不完整`
    );
  }

  return [...byId.values()];
}

const ROOT_DIR = resolve(
  dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1")),
  "../.."
);
const OPEN_DATA_DIR = resolve(ROOT_DIR, "open-data");
const STATIONS_JSON_PATH = resolve(OPEN_DATA_DIR, "stations.json");
const STATIONS_GEOJSON_PATH = resolve(OPEN_DATA_DIR, "stations.geojson");
const ASSETS_JSON_PATH = resolve(ROOT_DIR, "app/src/main/assets/taiwan_water_stations.json");
const WEB_JSON_PATH = resolve(ROOT_DIR, "web/stations.json");

/** 讀取既有資料集，用於保留使用者貢獻的點位與評價 */
function loadExistingDataset(): OpenWaterDataset | null {
  if (!existsSync(STATIONS_JSON_PATH)) return null;
  try {
    return JSON.parse(readFileSync(STATIONS_JSON_PATH, "utf-8")) as OpenWaterDataset;
  } catch (err) {
    console.warn(`讀取既有資料集失敗，將視為全新產生：${(err as Error).message}`);
    return null;
  }
}

/** 取得兩點間距離（公尺），用於 OSM 與策展點位的重複判定 */
function distanceMeters(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(bLat - aLat);
  const dLon = toRad(bLon - aLon);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** 合併策展點位、使用者貢獻點位與 OSM 點位，並以 30 公尺門檻去除重複 */
function mergeStations(
  existing: WaterStation[],
  curated: WaterStation[],
  osm: WaterStation[]
): WaterStation[] {
  const curatedIds = new Set(curated.map((s) => s.id));
  // 既有資料中不是策展清單、也不是上次 OSM 匯入的，視為使用者貢獻
  const userContributed = existing.filter(
    (s) => !curatedIds.has(s.id) && !s.id.startsWith("osm-")
  );
  // 既有的 OSM 點位保留：擴充資料時分塊可能失敗，不該因此丟掉上次已抓到的城市
  const previousOsm = existing.filter(
    (s) => s.id.startsWith("osm-") && !curatedIds.has(s.id)
  );

  const merged: WaterStation[] = [...curated, ...userContributed];
  const mergedIds = new Set(merged.map((s) => s.id));
  let skippedByProximity = 0;

  const candidates = [...previousOsm, ...osm];
  for (const station of candidates) {
    if (mergedIds.has(station.id)) continue;
    const tooClose = merged.some(
      (kept) =>
        distanceMeters(kept.latitude, kept.longitude, station.latitude, station.longitude) < 30
    );
    if (tooClose) {
      skippedByProximity++;
      continue;
    }
    merged.push(station);
    mergedIds.add(station.id);
  }

  console.log(`  策展點位 ${curated.length} 處、使用者貢獻 ${userContributed.length} 處`);
  console.log(
    `  OSM 既有 ${previousOsm.length} 處 + 本次 ${osm.length} 處，其中 ${skippedByProximity} 處與既有點位重疊已略過`
  );
  return merged;
}

export async function main() {
  console.log("=== 正在整合全台灣公開飲水機開放資料庫 ===");

  const existing = loadExistingDataset();
  if (existing) {
    console.log(
      `讀取既有資料集：${existing.stations.length} 處點位、${existing.reviews.length} 則評價`
    );
  }

  const osmStations = await fetchAllOsmDrinkingWater();
  const mergedStations = mergeStations(
    existing?.stations ?? [],
    CURATED_TAIWAN_STATIONS,
    osmStations
  );

  // 保留既有評價（含使用者從 App/Web 送出者），策展種子評價僅補上缺少的 id
  const reviewById = new Map<string, StationReview>();
  for (const review of existing?.reviews ?? []) reviewById.set(review.id, review);
  for (const seed of CURATED_REVIEWS) {
    if (!reviewById.has(seed.id)) reviewById.set(seed.id, seed);
  }
  // 清掉指向已不存在點位的孤兒評價
  const stationIds = new Set(mergedStations.map((s) => s.id));
  const reviews = [...reviewById.values()].filter((r) => stationIds.has(r.stationId));

  console.log(`總計取得 ${mergedStations.length} 處全台灣公開飲水機點位！`);

  const dataset: OpenWaterDataset = {
    version: 2,
    updatedAt: new Date().toISOString(),
    license: "CC-BY-4.0",
    source: "Zero Water Map (拎水地圖) Open Data — https://github.com/Stubby614/zero-water-map",
    stations: mergedStations,
    reviews,
  };

  const geojson = {
    type: "FeatureCollection",
    features: mergedStations.map((station) => ({
      type: "Feature",
      geometry: {
        type: "Point",
        coordinates: [station.longitude, station.latitude],
      },
      properties: {
        id: station.id,
        name: station.name,
        floorHint: station.floorHint,
        accessType: station.accessType,
        hasHot: station.hasHot,
        hasWarm: station.hasWarm,
        hasCold: station.hasCold,
        hasIced: station.hasIced,
        temperatureCategory: station.hasIced
          ? "ICED_TRIPLE"
          : station.hasWarm && station.hasHot
          ? "WARM_HOT"
          : "HOT_OR_SINGLE",
        is24Hours: station.is24Hours,
        openHours: station.openHours,
        status: station.status,
      },
    })),
  };

  mkdirSync(OPEN_DATA_DIR, { recursive: true });
  mkdirSync(dirname(ASSETS_JSON_PATH), { recursive: true });
  mkdirSync(dirname(WEB_JSON_PATH), { recursive: true });

  const jsonString = JSON.stringify(dataset, null, 2);
  writeFileSync(STATIONS_JSON_PATH, jsonString, "utf8");
  writeFileSync(STATIONS_GEOJSON_PATH, JSON.stringify(geojson, null, 2), "utf8");
  writeFileSync(ASSETS_JSON_PATH, jsonString, "utf8");
  writeFileSync(WEB_JSON_PATH, jsonString, "utf8");

  console.log(`✅ 已寫入 ${STATIONS_JSON_PATH}`);
  console.log(`✅ 已寫入 ${STATIONS_GEOJSON_PATH}`);
  console.log(`✅ 已寫入 ${ASSETS_JSON_PATH}`);
  console.log(`✅ 已寫入 ${WEB_JSON_PATH}`);
}

main().catch((err) => {
  console.error("執行失敗:", err);
  process.exit(1);
});
