package app.zerowater.map.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zerowater.map.data.location.DistanceFormatter
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.model.StationRatingSummary
import app.zerowater.map.data.model.WaterStation
import java.util.Locale

@Composable
fun StationListSheet(
    stations: List<WaterStation>,
    userLocation: GeoPoint?,
    filter: StationFilter,
    ratingSummaries: Map<String, StationRatingSummary>,
    isDiscoveringOsm: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleNoCardFilter: () -> Unit,
    onToggleIcedFilter: () -> Unit,
    onToggleHotFilter: () -> Unit,
    onDiscoverOsmClick: () -> Unit,
    onToggleStationFavorite: (WaterStation) -> Unit,
    onSelectStation: (WaterStation) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "附近取水點清單 (${stations.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (userLocation != null) "已依與你的距離由近到遠排序" else "開啟定位即可依距離排序",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onDiscoverOsmClick,
                enabled = !isDiscoveringOsm
            ) {
                if (isDiscoveringOsm) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(" 抓周邊點位", style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedTextField(
            value = filter.searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("搜尋飲水機名稱或位置提示") },
            placeholder = { Text("例：圖書館、捷運、運動中心、B1...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (filter.searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "清除搜尋")
                    }
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.favoritesOnly,
                onClick = onToggleFavoritesFilter,
                label = { Text("★ 已收藏") }
            )
            FilterChip(
                selected = filter.noCardOnly,
                onClick = onToggleNoCardFilter,
                label = { Text("免門禁") }
            )
            FilterChip(
                selected = filter.requireIced,
                onClick = onToggleIcedFilter,
                label = { Text("有冰水") }
            )
            FilterChip(
                selected = filter.requireHot,
                onClick = onToggleHotFilter,
                label = { Text("有熱水") }
            )
        }

        if (stations.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "沒有符合篩選條件的取水點",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "試著取消部分篩選條件，或點擊上方「抓周邊點位」從 OpenStreetMap 匯入附近真實飲水機！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(stations, key = { it.id }) { station ->
                    val distMeters = userLocation?.distanceMetersTo(station.latitude, station.longitude)
                    val ratingSummary = ratingSummaries[station.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStation(station) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (distMeters != null) {
                                        Text(
                                            text = DistanceFormatter.formatSummary(distMeters),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onToggleStationFavorite(station) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (station.isFavorite) {
                                            Icons.Default.Favorite
                                        } else {
                                            Icons.Default.FavoriteBorder
                                        },
                                        contentDescription = "收藏取水點",
                                        tint = if (station.isFavorite) {
                                            Color(0xFFE53935)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Text(
                                text = station.floorHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { onSelectStation(station) },
                                    label = { Text(station.accessType.label, style = MaterialTheme.typography.labelSmall) }
                                )

                                val temps = buildList {
                                    if (station.hasHot) add("熱")
                                    if (station.hasWarm) add("溫")
                                    if (station.hasCold) add("冷")
                                    if (station.hasIced) add("冰")
                                }.joinToString("/")

                                if (temps.isNotEmpty()) {
                                    Text(
                                        text = "水溫：$temps",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                if (ratingSummary != null && ratingSummary.reviewCount > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.TAIWAN,
                                                "%.1f (%d)",
                                                ratingSummary.averageRating,
                                                ratingSummary.reviewCount
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
