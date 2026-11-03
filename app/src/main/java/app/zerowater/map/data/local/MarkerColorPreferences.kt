package app.zerowater.map.data.local

import android.content.Context
import android.graphics.Color
import app.zerowater.map.data.model.TemperatureCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class MarkerColorOption(
    val name: String,
    val hex: String
)

val PRESET_MARKER_COLORS = listOf(
    MarkerColorOption("海洋藍", "#1E88E5"),
    MarkerColorOption("亮麗黃", "#FDD835"),
    MarkerColorOption("熱情紅", "#E53935"),
    MarkerColorOption("活力橘", "#FB8C00"),
    MarkerColorOption("森林綠", "#43A047"),
    MarkerColorOption("冰川青", "#00ACC1"),
    MarkerColorOption("極光紫", "#8E24AA"),
    MarkerColorOption("櫻花粉", "#D81B60"),
    MarkerColorOption("霧面灰", "#9E9E9E")
)

/** 顯示範圍半徑選項（0 = 不限，僅依目前視窗範圍） */
val RADIUS_OPTIONS_METERS = listOf(0, 500, 1000, 2000, 5000)

data class MarkerColorConfig(
    val icedColorHex: String = TemperatureCategory.ICED_TRIPLE.defaultColorHex,
    val warmHotColorHex: String = TemperatureCategory.WARM_HOT.defaultColorHex,
    val hotOnlyColorHex: String = TemperatureCategory.HOT_OR_SINGLE.defaultColorHex,
    val untaggedColorHex: String = TemperatureCategory.UNTAGGED.defaultColorHex,
    val showMarkers: Boolean = true,
    val viewportOnly: Boolean = true,
    val radiusMeters: Int = 1000,
    val minZoomForPins: Double = 13.0
) {
    fun hexFor(category: TemperatureCategory): String = when (category) {
        TemperatureCategory.ICED_TRIPLE -> icedColorHex
        TemperatureCategory.WARM_HOT -> warmHotColorHex
        TemperatureCategory.HOT_OR_SINGLE -> hotOnlyColorHex
        TemperatureCategory.UNTAGGED -> untaggedColorHex
    }

    fun colorIntFor(category: TemperatureCategory): Int =
        runCatching { Color.parseColor(hexFor(category)) }
            .getOrDefault(Color.parseColor(category.defaultColorHex))
}

@Singleton
class MarkerColorPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("zero_water_marker_colors", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(readCurrentConfig())
    val configFlow: StateFlow<MarkerColorConfig> = _configFlow.asStateFlow()

    private fun readCurrentConfig(): MarkerColorConfig {
        val iced = prefs.getString("iced_color", TemperatureCategory.ICED_TRIPLE.defaultColorHex)
            ?: TemperatureCategory.ICED_TRIPLE.defaultColorHex
        val warmHot = prefs.getString("warm_hot_color", TemperatureCategory.WARM_HOT.defaultColorHex)
            ?: TemperatureCategory.WARM_HOT.defaultColorHex
        val hotOnly = prefs.getString("hot_only_color", TemperatureCategory.HOT_OR_SINGLE.defaultColorHex)
            ?: TemperatureCategory.HOT_OR_SINGLE.defaultColorHex
        val untagged = prefs.getString("untagged_color", TemperatureCategory.UNTAGGED.defaultColorHex)
            ?: TemperatureCategory.UNTAGGED.defaultColorHex
        val defaults = MarkerColorConfig()
        return MarkerColorConfig(
            icedColorHex = iced,
            warmHotColorHex = warmHot,
            hotOnlyColorHex = hotOnly,
            untaggedColorHex = untagged,
            showMarkers = prefs.getBoolean("show_markers", defaults.showMarkers),
            viewportOnly = prefs.getBoolean("viewport_only", defaults.viewportOnly),
            radiusMeters = prefs.getInt("radius_meters", defaults.radiusMeters)
                .takeIf { it in RADIUS_OPTIONS_METERS } ?: defaults.radiusMeters
        )
    }

    fun setCategoryColor(category: TemperatureCategory, hex: String) {
        val key = when (category) {
            TemperatureCategory.ICED_TRIPLE -> "iced_color"
            TemperatureCategory.WARM_HOT -> "warm_hot_color"
            TemperatureCategory.HOT_OR_SINGLE -> "hot_only_color"
            TemperatureCategory.UNTAGGED -> "untagged_color"
        }
        prefs.edit().putString(key, hex).apply()
        _configFlow.update { readCurrentConfig() }
    }

    fun setShowMarkers(show: Boolean) {
        prefs.edit().putBoolean("show_markers", show).apply()
        _configFlow.update { readCurrentConfig() }
    }

    fun setViewportOnly(enabled: Boolean) {
        prefs.edit().putBoolean("viewport_only", enabled).apply()
        _configFlow.update { readCurrentConfig() }
    }

    fun setRadiusMeters(meters: Int) {
        prefs.edit().putInt("radius_meters", meters).apply()
        _configFlow.update { readCurrentConfig() }
    }

    fun resetToDefaults() {
        prefs.edit()
            .remove("iced_color")
            .remove("warm_hot_color")
            .remove("hot_only_color")
            .remove("untagged_color")
            .remove("show_markers")
            .remove("viewport_only")
            .remove("radius_meters")
            .apply()
        _configFlow.update { readCurrentConfig() }
    }
}
