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

@Composable
fun SystemInfoScreen() {
    val context = LocalContext.current
    val hardwareMonitor = remember { HardwareMonitor(context) }
    
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var cpuInfo by remember { mutableStateOf<CpuInfo?>(null) }
    var batteryInfo by remember { mutableStateOf<HwBatteryInfo?>(null) }
    var displayInfo by remember { mutableStateOf<DisplayInfo?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfoData?>(null) }
    var sensorInfoList by remember { mutableStateOf<List<SensorInfo>>(emptyList()) }

    // Live sensor readings map: sensorType -> FloatArray of values
    var liveReadings by remember { mutableStateOf<Map<Int, FloatArray>>(emptyMap()) }

    // Register/unregister SensorLiveReader with the screen lifecycle
    val sensorReader = remember { SensorLiveReader(context) }
    DisposableEffect(Unit) {
        sensorReader.start()
        onDispose { sensorReader.stop() }
    }

    // Refresh live readings every 500ms
    LaunchedEffect(Unit) {
        while (true) {
            liveReadings = sensorReader.readings
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(Unit) {
        deviceInfo = hardwareMonitor.getDeviceInfo()
        cpuInfo = hardwareMonitor.getCpuInfo()
        batteryInfo = hardwareMonitor.getBatteryInfo()
        displayInfo = hardwareMonitor.getDisplayInfo()
        cameraInfo = hardwareMonitor.getCameraInfo()
        sensorInfoList = hardwareMonitor.getSensorInfo()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "System Info",
                color = Color.White,
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
                    color = Color.White,
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SensorRow(sensor: SensorInfo, liveReading: FloatArray? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sensor.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = "${sensor.vendor} • ${sensor.type}", color = Color.Gray, fontSize = 11.sp)
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
            Text(text = valueText, color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            val maxStr = if (sensor.maxRange % 1f == 0f) sensor.maxRange.toInt().toString()
                        else String.format(java.util.Locale.US, "%.2f", sensor.maxRange)
            Text(text = "Max: $maxStr ${sensor.unit}".trim(), color = Color.Gray, fontSize = 10.sp)
        }
    }
}
