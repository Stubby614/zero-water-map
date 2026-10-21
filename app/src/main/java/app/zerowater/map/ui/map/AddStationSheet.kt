package app.zerowater.map.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zerowater.map.data.location.AddressCandidate
import app.zerowater.map.data.location.GeoPoint
import app.zerowater.map.data.model.AccessType
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun AddStationSheet(
    pickedLocation: GeoPoint,
    pickedAddress: String?,
    isLocating: Boolean,
    isSearchingAddress: Boolean,
    addressSearchResults: List<AddressCandidate>,
    draftPhotoUri: String?,
    onPickPhotoFromGallery: () -> Unit,
    onTakePhotoWithCamera: () -> Unit,
    onRemoveDraftPhoto: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSearchAddress: (String) -> Unit,
    onSelectAddressCandidate: (AddressCandidate) -> Unit,
    onPickOnMap: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        floorHint: String,
        accessType: AccessType,
        hasHot: Boolean,
        hasWarm: Boolean,
        hasCold: Boolean,
        hasIced: Boolean,
        is24Hours: Boolean,
        openHours: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var floorHint by remember { mutableStateOf("") }
    var addressQuery by remember { mutableStateOf("") }
    var accessType by remember { mutableStateOf(AccessType.PUBLIC) }
    var hasHot by remember { mutableStateOf(true) }
    var hasWarm by remember { mutableStateOf(true) }
    var hasCold by remember { mutableStateOf(true) }
    var hasIced by remember { mutableStateOf(false) }
    var is24Hours by remember { mutableStateOf(false) }
    var openHours by remember { mutableStateOf("08:00 - 22:00") }
    var showNameError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "通報新的取水點",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Location Selection Card (Current GPS / Address Search / Map Pin)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📍 取水點位置",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!pickedAddress.isNullOrBlank()) {
                    Text(
                        text = pickedAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "經緯度：%.5f, %.5f",
                        pickedLocation.latitude,
                        pickedLocation.longitude
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onUseCurrentLocation,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(" 用目前定位", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onPickOnMap,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" 地圖拖曳選點", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = { addressQuery = it },
                    label = { Text("或輸入地址／地標搜尋位置") },
                    placeholder = { Text("例：台北市中正區北平西路3號 或 台大總圖") },
                    singleLine = true,
                    trailingIcon = {
                        if (isSearchingAddress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { onSearchAddress(addressQuery) }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜尋地址"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (addressSearchResults.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            addressSearchResults.forEachIndexed { index, candidate ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectAddressCandidate(candidate)
                                            addressQuery = candidate.fullAddress
                                            if (name.isBlank()) {
                                                name = "${candidate.title} 飲水機"
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = candidate.fullAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (index < addressSearchResults.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (it.isNotBlank()) showNameError = false
            },
            label = { Text("取水點名稱 *") },
            placeholder = { Text("例：大安運動中心 1F 飲水機") },
            isError = showNameError,
            supportingText = if (showNameError) {
                { Text("請輸入取水點名稱") }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = floorHint,
            onValueChange = { floorHint = it },
            label = { Text("樓層與詳細位置提示") },
            placeholder = { Text("例：1F 櫃台後方洗手間旁、水壺好裝") },
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📷 現場位置／飲水機照片（選填，幫助大家找路）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTakePhotoWithCamera,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" 拍照標示位置", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onPickPhotoFromGallery,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" 從相簿選擇", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (draftPhotoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = draftPhotoUri,
                            contentDescription = "飲水機位置預覽照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = onRemoveDraftPhoto,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "移除照片",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "進入門檻",
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = accessType == AccessType.PUBLIC,
                onClick = { accessType = AccessType.PUBLIC },
                label = { Text("完全開放") }
            )
            FilterChip(
                selected = accessType == AccessType.SEMI_PUBLIC,
                onClick = { accessType = AccessType.SEMI_PUBLIC },
                label = { Text("半開放場館 (圖書館/捷運)") }
            )
            FilterChip(
                selected = accessType == AccessType.RESTRICTED,
                onClick = { accessType = AccessType.RESTRICTED },
                label = { Text("需門禁/證件") }
            )
        }

        Text(
            text = "供應水溫（可複選）",
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = hasHot,
                onClick = { hasHot = !hasHot },
                label = { Text("熱水") }
            )
            FilterChip(
                selected = hasWarm,
                onClick = { hasWarm = !hasWarm },
                label = { Text("溫水") }
            )
            FilterChip(
                selected = hasCold,
                onClick = { hasCold = !hasCold },
                label = { Text("冷水") }
            )
            FilterChip(
                selected = hasIced,
                onClick = { hasIced = !hasIced },
                label = { Text("冰水") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "24 小時全天開放",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = is24Hours,
                onCheckedChange = { is24Hours = it }
            )
        }

        if (!is24Hours) {
            OutlinedTextField(
                value = openHours,
                onValueChange = { openHours = it },
                label = { Text("開放時間") },
                placeholder = { Text("例：06:00 - 22:00") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSubmit(
                            name,
                            floorHint,
                            accessType,
                            hasHot,
                            hasWarm,
                            hasCold,
                            hasIced,
                            is24Hours,
                            openHours
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("確認新增")
            }
        }
    }
}
