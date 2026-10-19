package app.zerowater.map.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF002F4C),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF01579B),
    onPrimaryContainer = Color(0xFFE1F5FE),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF003731),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B)
)

@Composable
fun ZeroWaterMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
