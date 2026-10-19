package app.zerowater.map.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerowater.map.data.model.StationStatus
import app.zerowater.map.data.model.WaterStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreView(
            stations = uiState.stations,
            onStationClick = { viewModel.selectStation(it.id) },
            modifier = Modifier.fillMaxSize()
        )

        // Top Filter Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "拎水地圖 (${uiState.stations.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FilterChip(
                    selected = uiState.filter.noCardOnly,
                    onClick = viewModel::toggleNoCardFilter,
                    label = { Text("免門禁/免證件") },
                    colors = FilterChipDefaults.filterChipColors()
                )
                FilterChip(
                    selected = uiState.filter.requireIced,
                    onClick = viewModel::toggleIcedFilter,
                    label = { Text("有冰水") }
                )
                FilterChip(
                    selected = uiState.filter.requireHot,
                    onClick = viewModel::toggleHotFilter,
                    label = { Text("有熱水 (泡麵)") }
                )
            }
        }

        // Station Details BottomSheet
        uiState.selectedStation?.let { station ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectStation(null) }
            ) {
                StationDetailSheet(
                    station = station,
                    onNavigateClick = {
                        val uri = Uri.parse(
                            "google.navigation:q=${station.latitude},${station.longitude}&mode=w"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=walking")
                                )
                            )
                        }
                    },
                    onReportStatus = { status ->
                        viewModel.reportStatus(station.id, status)
                    }
                )
            }
        }
    }
}

@Composable
private fun StationDetailSheet(
    station: WaterStation,
    onNavigateClick: () -> Unit,
    onReportStatus: (StationStatus) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = station.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(station.accessType.label) })
            AssistChip(onClick = {}, label = { Text(station.status.label) })
        }

        Text(
            text = "位置提示：${station.floorHint}",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "開放時間：${station.openHours}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val temps = buildList {
            if (station.hasHot) add("熱水")
            if (station.hasWarm) add("溫水")
            if (station.hasCold) add("冷水")
            if (station.hasIced) add("冰水")
        }.joinToString(" / ")

        Text(
            text = "供應水溫：$temps",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onNavigateClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("步行導航去裝水")
        }

        Text(
            text = "回報目前飲水機狀態：",
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onReportStatus(StationStatus.WORKING) },
                modifier = Modifier.weight(1f)
            ) {
                Text("正常出水")
            }
            OutlinedButton(
                onClick = { onReportStatus(StationStatus.FILTER_EXPIRED) },
                modifier = Modifier.weight(1f)
            ) {
                Text("濾心紅燈")
            }
            OutlinedButton(
                onClick = { onReportStatus(StationStatus.OUT_OF_ORDER) },
                modifier = Modifier.weight(1f)
            ) {
                Text("故障")
            }
        }
    }
}
