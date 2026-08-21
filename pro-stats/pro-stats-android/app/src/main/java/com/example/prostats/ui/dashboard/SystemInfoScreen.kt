package com.example.prostats.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prostats.data.*
import com.example.prostats.theme.ProStatsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SystemInfoScreen() {
    val context = LocalContext.current
    val colors = ProStatsColors.current
    val hardwareMonitor = remember { HardwareMonitor(context) }
    val systemMonitor = remember { SystemMonitor(context) }

    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var cpuInfo by remember { mutableStateOf<CpuInfo?>(null) }
    var batteryInfo by remember { mutableStateOf<HwBatteryInfo?>(null) }
    var displayInfo by remember { mutableStateOf<DisplayInfo?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfoData?>(null) }
    var sensorInfoList by remember { mutableStateOf<List<SensorInfo>>(emptyList()) }
    var gpuInfo by remember { mutableStateOf<GpuInfo?>(null) }
    var networkInfo by remember { mutableStateOf<NetworkInfo?>(null) }
    var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }
    var memoryDetail by remember { mutableStateOf<MemoryDetailInfo?>(null) }

    // Live sensor readings map: sensorType -> FloatArray of values
    var liveReadings by remember { mutableStateOf<Map<Int, FloatArray>>(emptyMap()) }

    // Register/unregister SensorLiveReader with the screen lifecycle
    val sensorReader = remember { SensorLiveReader(context) }
    DisposableEffect(Unit) {
        sensorReader.start()
        onDispose { sensorReader.stop() }
    }

    // Refresh live readings every 1000ms
    LaunchedEffect(Unit) {
        while (true) {
            liveReadings = sensorReader.getSnapshot()
            batteryInfo = hardwareMonitor.getBatteryInfo()
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dInfo = hardwareMonitor.getDeviceInfo()
            val cInfo = hardwareMonitor.getCpuInfo()
            val bInfo = hardwareMonitor.getBatteryInfo()
            val dispInfo = hardwareMonitor.getDisplayInfo()
            val camInfo = hardwareMonitor.getCameraInfo()
            val sInfoList = hardwareMonitor.getSensorInfo()
            val gInfo = systemMonitor.getGpuInfo()
            val nInfo = systemMonitor.getNetworkInfo()
            val stInfo = systemMonitor.getStorageInfo()
            val memInfo = systemMonitor.getMemoryDetailInfo()

            deviceInfo = dInfo
            cpuInfo = cInfo
            batteryInfo = bInfo
            displayInfo = dispInfo
            cameraInfo = camInfo
            sensorInfoList = sInfoList
            gpuInfo = gInfo
            networkInfo = nInfo
            storageInfo = stInfo
            memoryDetail = memInfo
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "System Information",
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hardware diagnostics and system specifications",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        deviceInfo?.let { di ->
            item {
                InfoCard(title = "Device Identity", icon = Icons.Default.Phone, iconColor = colors.accentBlue) {
                    InfoRow("Manufacturer", di.manufacturer)
                    InfoRow("Model", di.model)
                    InfoRow("Board", di.board)
                    InfoRow("Hardware Platform", di.hardware)
                    InfoRow("Android OS Version", di.androidVersion)
                }
            }
        }

        cpuInfo?.let { cpu ->
            item {
                InfoCard(title = "SoC & CPU Architecture", icon = Icons.Default.Build, iconColor = colors.accentPurple) {
                    InfoRow("Architecture", cpu.architecture)
                    InfoRow("Core Count", "${cpu.cores} Cores")
                    InfoRow("Max Clock Frequency", if (cpu.maxFreqGhz > 0) String.format(java.util.Locale.US, "%.2f GHz", cpu.maxFreqGhz) else "Unknown")
                }
            }
        }

        gpuInfo?.let { gpu ->
            item {
                InfoCard(title = "Graphics Processing Unit (GPU)", icon = Icons.Default.Build, iconColor = colors.accentGreen) {
                    InfoRow("Renderer", gpu.renderer)
                    InfoRow("Vendor", gpu.vendor)
                    if (gpu.openGlVersion.isNotBlank()) {
                        InfoRow("OpenGL ES", gpu.openGlVersion)
                    }
                    if (gpu.maxFreqMhz > 0) InfoRow("Max Frequency", "${gpu.maxFreqMhz} MHz")
                    if (gpu.currentFreqMhz > 0) InfoRow("Current Frequency", "${gpu.currentFreqMhz} MHz")
                }
            }
        }

        batteryInfo?.let { bat ->
            item {
                InfoCard(title = "Battery Hardware", icon = Icons.Default.Info, iconColor = colors.accentOrange) {
                    InfoRow("Current Level", "${bat.level}%")
                    InfoRow("Health State", bat.health)
                    if (bat.cycleCount >= 0) {
                        InfoRow("Charge Cycles", "${bat.cycleCount} (${bat.cycleSource})")
                    }
                    InfoRow("Chemistry Technology", bat.technology)
                    if (bat.capacityMah > 0) {
                        InfoRow("Design Capacity", "${bat.capacityMah.toInt()} mAh")
                    }
                    InfoRow("Voltage", "${bat.voltageMv} mV")
                    InfoRow("Temperature", "${bat.temperatureC} °C")
                }
            }
        }

        displayInfo?.let { disp ->
            item {
                InfoCard(title = "Display Panel", icon = Icons.Default.Info, iconColor = colors.accentBlue) {
                    InfoRow("Resolution", disp.resolution)
                    InfoRow("Refresh Rate", "${disp.refreshRate} Hz")
                    InfoRow("Pixel Density", "${disp.densityDpi} DPI")
                }
            }
        }

        memoryDetail?.let { mem ->
            item {
                InfoCard(title = "Memory & Paging Details", icon = Icons.Default.Info, iconColor = colors.accentPurple) {
                    InfoRow("Total RAM", "${mem.totalRamMb} MB")
                    InfoRow("Used RAM", "${mem.usedRamMb} MB")
                    InfoRow("Available RAM", "${mem.availRamMb} MB")
                    InfoRow("Low Memory Flag", if (mem.lowMemory) "Active ⚠️" else "Normal")
                    if (mem.zramTotalMb > 0) {
                        InfoRow("ZRAM Total", "${mem.zramTotalMb} MB")
                        InfoRow("ZRAM In-Use", "${mem.zramUsedMb} MB")
                    }
                    if (mem.swapTotalMb > 0) {
                        InfoRow("Swap Total", "${mem.swapTotalMb} MB")
                        InfoRow("Swap In-Use", "${mem.swapUsedMb} MB")
                    }
                }
            }
        }

        storageInfo?.let { storage ->
            item {
                InfoCard(title = "Storage Partitions", icon = Icons.Default.Info, iconColor = colors.accentGreen) {
                    InfoRow("Internal Total", String.format(java.util.Locale.US, "%.1f GB", storage.internalTotalGb))
                    InfoRow("Internal Used", String.format(java.util.Locale.US, "%.1f GB", storage.internalUsedGb))
                    InfoRow("Internal Available", String.format(java.util.Locale.US, "%.1f GB", (storage.internalTotalGb - storage.internalUsedGb).coerceAtLeast(0f)))
                    if (storage.externalTotalGb > 0) {
                        InfoRow("External Total", String.format(java.util.Locale.US, "%.1f GB", storage.externalTotalGb))
                        InfoRow("External Used", String.format(java.util.Locale.US, "%.1f GB", storage.externalUsedGb))
                    }
                }
            }
        }

        networkInfo?.let { net ->
            item {
                InfoCard(title = "Network Interfaces", icon = Icons.Default.Info, iconColor = colors.accentBlue) {
                    InfoRow("Connection Type", net.connectionType)
                    if (net.wifiSsid.isNotBlank() && net.wifiSsid != "<unknown ssid>") {
                        InfoRow("Wi-Fi SSID", net.wifiSsid)
                        InfoRow("Signal Quality", "${net.wifiSignalStrength}/4 bars")
                        InfoRow("Link Speed", "${net.linkSpeedMbps} Mbps")
                    }
                    if (net.ipAddress.isNotBlank() && net.ipAddress != "0.0.0.0") {
                        InfoRow("IP Address", net.ipAddress)
                    }
                }
            }
        }

        cameraInfo?.let { cam ->
            item {
                InfoCard(title = "Camera Modules", icon = Icons.Default.Search, iconColor = colors.accentOrange) {
                    InfoRow("Primary Rear Sensor", cam.rearMegapixels?.let { "$it MP" } ?: "Not detected")
                    InfoRow("Front Selfie Sensor", cam.frontMegapixels?.let { "$it MP" } ?: "Not detected")
                }
            }
        }

        if (sensorInfoList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Sensors (${sensorInfoList.size})",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LIVE STREAM",
                        color = colors.accentPurple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            items(sensorInfoList, key = { it.typeInt }) { sensor ->
                SensorRow(sensor = sensor, liveReading = liveReadings[sensor.typeInt])
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ProStatsColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderColor, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconColor.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    val colors = ProStatsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 13.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SensorRow(sensor: SensorInfo, liveReading: FloatArray? = null) {
    val colors = ProStatsColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sensor.name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${sensor.vendor} • ${sensor.type}",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .background(colors.elevatedSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val valueText = if (liveReading != null && liveReading.isNotEmpty()) {
                    val v = liveReading[0]
                    val formatted = if (v % 1f == 0f) v.toInt().toString()
                    else String.format(java.util.Locale.US, "%.2f", v)
                    "$formatted ${sensor.unit}".trim()
                } else {
                    "-- ${sensor.unit}".trim()
                }
                Text(
                    text = valueText,
                    color = colors.accentPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
