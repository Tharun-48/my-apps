package com.example.prostats.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    // Refresh live readings every 1000ms — uses getSnapshot() for a new map reference
    LaunchedEffect(Unit) {
        while (true) {
            liveReadings = sensorReader.getSnapshot()
            // Also refresh battery info for real-time readings
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "System Info",
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, top = 32.dp)
            )
        }

        deviceInfo?.let { di ->
            item {
                InfoCard(title = "Device", icon = Icons.Default.Phone) {
                    InfoRow("Manufacturer", di.manufacturer)
                    InfoRow("Model", di.model)
                    InfoRow("Board", di.board)
                    InfoRow("Hardware", di.hardware)
                    InfoRow("Android Version", di.androidVersion)
                }
            }
        }

        cpuInfo?.let { cpu ->
            item {
                InfoCard(title = "CPU & SoC", icon = Icons.Default.Build) {
                    InfoRow("Architecture", cpu.architecture)
                    InfoRow("Cores", cpu.cores.toString())
                    InfoRow("Max Frequency", if (cpu.maxFreqGhz > 0) String.format(java.util.Locale.US, "%.2f GHz", cpu.maxFreqGhz) else "Unknown")
                }
            }
        }

        // GPU Info (NEW — DevCheck inspired)
        gpuInfo?.let { gpu ->
            item {
                InfoCard(title = "GPU", icon = Icons.Default.Build) {
                    InfoRow("Renderer", gpu.renderer)
                    InfoRow("Vendor", gpu.vendor)
                    if (gpu.maxFreqMhz > 0) InfoRow("Max Frequency", "${gpu.maxFreqMhz} MHz")
                    if (gpu.currentFreqMhz > 0) InfoRow("Current Frequency", "${gpu.currentFreqMhz} MHz")
                }
            }
        }

        batteryInfo?.let { bat ->
            item {
                InfoCard(title = "Battery", icon = Icons.Default.Info) {
                    InfoRow("Level", "${bat.level}%")
                    InfoRow("Health", bat.health)
                    InfoRow("Technology", bat.technology)
                    InfoRow("Voltage", "${bat.voltageMv} mV")
                    InfoRow("Temperature", "${bat.temperatureC} °C")
                }
            }
        }

        displayInfo?.let { disp ->
            item {
                InfoCard(title = "Display", icon = Icons.Default.Info) {
                    InfoRow("Resolution", disp.resolution)
                    InfoRow("Refresh Rate", "${disp.refreshRate} Hz")
                    InfoRow("Density", "${disp.densityDpi} DPI")
                }
            }
        }

        // Memory Details (NEW)
        memoryDetail?.let { mem ->
            item {
                InfoCard(title = "Memory Details", icon = Icons.Default.Info) {
                    InfoRow("Total RAM", "${mem.totalRamMb} MB")
                    InfoRow("Used RAM", "${mem.usedRamMb} MB")
                    InfoRow("Available RAM", "${mem.availRamMb} MB")
                    InfoRow("Low Memory", if (mem.lowMemory) "Yes ⚠️" else "No")
                    if (mem.zramTotalMb > 0) {
                        InfoRow("ZRAM Total", "${mem.zramTotalMb} MB")
                        InfoRow("ZRAM Used", "${mem.zramUsedMb} MB")
                    }
                    if (mem.swapTotalMb > 0) {
                        InfoRow("Swap Total", "${mem.swapTotalMb} MB")
                        InfoRow("Swap Used", "${mem.swapUsedMb} MB")
                    }
                }
            }
        }

        // Storage (NEW)
        storageInfo?.let { storage ->
            item {
                InfoCard(title = "Storage", icon = Icons.Default.Info) {
                    InfoRow("Internal Total", String.format(java.util.Locale.US, "%.1f GB", storage.internalTotalGb))
                    InfoRow("Internal Used", String.format(java.util.Locale.US, "%.1f GB", storage.internalUsedGb))
                    InfoRow("Internal Free", String.format(java.util.Locale.US, "%.1f GB", storage.internalTotalGb - storage.internalUsedGb))
                    if (storage.externalTotalGb > 0) {
                        InfoRow("External Total", String.format(java.util.Locale.US, "%.1f GB", storage.externalTotalGb))
                        InfoRow("External Used", String.format(java.util.Locale.US, "%.1f GB", storage.externalUsedGb))
                    }
                }
            }
        }

        // Network (NEW)
        networkInfo?.let { net ->
            item {
                InfoCard(title = "Network", icon = Icons.Default.Info) {
                    InfoRow("Connection Type", net.connectionType)
                    if (net.wifiSsid.isNotBlank() && net.wifiSsid != "<unknown ssid>") {
                        InfoRow("Wi-Fi SSID", net.wifiSsid)
                        InfoRow("Signal Strength", "${net.wifiSignalStrength}/4")
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
                InfoCard(title = "Camera", icon = Icons.Default.Search) {
                    InfoRow("Rear Sensor", cam.rearMegapixels?.let { "$it MP" } ?: "Unknown")
                    InfoRow("Front Sensor", cam.frontMegapixels?.let { "$it MP" } ?: "Unknown")
                }
            }
        }

        if (sensorInfoList.isNotEmpty()) {
            item {
                Text(
                    text = "Sensors (${sensorInfoList.size})",
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(sensorInfoList) { sensor ->
                SensorRow(sensor = sensor, liveReading = liveReadings[sensor.typeInt])
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun InfoCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val colors = ProStatsColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, contentDescription = null, tint = colors.accentPurple, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 14.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SensorRow(sensor: SensorInfo, liveReading: FloatArray? = null) {
    val colors = ProStatsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.cardSurface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.borderColor.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sensor.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = "${sensor.vendor} • ${sensor.type}", color = colors.textSecondary, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            // Live sensor value — primary axis (values[0])
            val valueText = if (liveReading != null && liveReading.isNotEmpty()) {
                val v = liveReading[0]
                val formatted = if (v % 1f == 0f) v.toInt().toString()
                                else String.format(java.util.Locale.US, "%.3f", v)
                "$formatted ${sensor.unit}".trim()
            } else {
                "-- ${sensor.unit}".trim()
            }
            Text(text = valueText, color = colors.accentPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
