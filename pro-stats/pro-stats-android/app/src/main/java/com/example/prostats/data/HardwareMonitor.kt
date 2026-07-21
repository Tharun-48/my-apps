package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val board: String,
    val hardware: String,
    val androidVersion: String
)

data class CpuInfo(
    val architecture: String,
    val cores: Int,
    val maxFreqGhz: Double
)

data class HwBatteryInfo(
    val health: String,
    val technology: String,
    val voltageMv: Int,
    val temperatureC: Float,
    val level: Int
)

data class DisplayInfo(
    val resolution: String,
    val refreshRate: Float,
    val densityDpi: Int
)

data class CameraInfoData(
    val rearMegapixels: Float?,
    val frontMegapixels: Float?
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: String,
    val powerMa: Float,
    val maxRange: Float,
    val unit: String = ""
)

class HardwareMonitor(private val context: Context) {
    
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            androidVersion = Build.VERSION.RELEASE
        )
    }

    suspend fun getCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        val cores = Runtime.getRuntime().availableProcessors()
        var maxFreq = 0.0
        try {
            // Check max frequency across ALL available CPU cores (cpu0 .. cpuN-1)
            for (i in 0 until cores) {
                for (path in listOf("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq", "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")) {
                    val maxFreqFile = File(path)
                    if (maxFreqFile.exists() && maxFreqFile.canRead()) {
                        val freqKhz = maxFreqFile.readText().trim().toLongOrNull() ?: 0L
                        val freqGhz = freqKhz / 1000000.0
                        if (freqGhz > maxFreq) {
                            maxFreq = freqGhz
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (maxFreq == 0.0) maxFreq = 2.84 // fallback standard SoC max frequency
        CpuInfo(arch, cores, maxFreq)
    }

    fun getBatteryInfo(): HwBatteryInfo {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val healthInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthString = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        val technology = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperature = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10f
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

        return HwBatteryInfo(healthString, technology, voltage, temperature, level)
    }

    fun getDisplayInfo(): DisplayInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val refreshRate = windowManager.defaultDisplay.refreshRate
        return DisplayInfo(
            resolution = "${metrics.widthPixels} x ${metrics.heightPixels}",
            refreshRate = refreshRate,
            densityDpi = metrics.densityDpi
        )
    }

    fun getCameraInfo(): CameraInfoData {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var rearMp: Float? = null
        var frontMp: Float? = null
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if (activeArray != null) {
                    val mp = (activeArray.width() * activeArray.height()) / 1000000f
                    val roundedMp = (mp * 10).roundToInt() / 10f // round to 1 decimal place
                    if (facing == CameraCharacteristics.LENS_FACING_BACK && rearMp == null) {
                        rearMp = roundedMp
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT && frontMp == null) {
                        frontMp = roundedMp
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return CameraInfoData(rearMp, frontMp)
    }

    fun getSensorInfo(): List<SensorInfo> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        return sensors.map { s ->
            val typeStr = s.stringType ?: "Unknown"
            val unit = when {
                typeStr.contains("accelerometer") || typeStr.contains("gravity") || typeStr.contains("linear_acceleration") -> "m/s²"
                typeStr.contains("gyroscope") -> "rad/s"
                typeStr.contains("magnetic") -> "µT"
                typeStr.contains("light") -> "lx"
                typeStr.contains("temperature") || typeStr.contains("temp") -> "°C"
                typeStr.contains("pressure") -> "hPa"
                typeStr.contains("proximity") -> "cm"
                typeStr.contains("step") || typeStr.contains("count") -> "steps"
                else -> ""
            }
            SensorInfo(
                name = s.name,
                vendor = s.vendor,
                type = typeStr,
                powerMa = s.power,
                maxRange = s.maximumRange,
                unit = unit
            )
        }
    }
}
