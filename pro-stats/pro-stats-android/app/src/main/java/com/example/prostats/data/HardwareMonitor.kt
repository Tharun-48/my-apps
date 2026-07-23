package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
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
    val typeInt: Int,
    val powerMa: Float,
    val maxRange: Float,
    val unit: String = ""
)

class HardwareMonitor(private val context: Context) {

    // Cached static info — these don't change at runtime
    @Volatile private var cachedDeviceInfo: DeviceInfo? = null
    @Volatile private var cachedCpuInfo: CpuInfo? = null
    @Volatile private var cachedCameraInfo: CameraInfoData? = null
    @Volatile private var cachedSensorInfo: List<SensorInfo>? = null
    
    fun getDeviceInfo(): DeviceInfo {
        cachedDeviceInfo?.let { return it }
        val info = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            androidVersion = Build.VERSION.RELEASE
        )
        cachedDeviceInfo = info
        return info
    }

    suspend fun getCpuInfo(): CpuInfo {
        cachedCpuInfo?.let { return it }
        val info = withContext(Dispatchers.IO) {
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
        cachedCpuInfo = info
        return info
    }

    fun getBatteryInfo(): HwBatteryInfo {
        // Battery info is NOT cached — it changes in real-time
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
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        @Suppress("DEPRECATION")
        val refreshRate = windowManager.defaultDisplay.refreshRate
        return DisplayInfo(
            resolution = "${metrics.widthPixels} x ${metrics.heightPixels}",
            refreshRate = refreshRate,
            densityDpi = metrics.densityDpi
        )
    }

    fun getCameraInfo(): CameraInfoData {
        cachedCameraInfo?.let { return it }
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
        val info = CameraInfoData(rearMp, frontMp)
        cachedCameraInfo = info
        return info
    }

    fun getSensorInfo(): List<SensorInfo> {
        cachedSensorInfo?.let { return it }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val info = sensors.map { s ->
            val typeStr = s.stringType ?: "Unknown"
            val unit = when (s.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
                Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
                Sensor.TYPE_LIGHT -> "lx"
                Sensor.TYPE_PRESSURE -> "hPa"
                Sensor.TYPE_PROXIMITY -> "cm"
                Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_TEMPERATURE -> "°C"
                Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> "steps"
                Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> "rad"
                Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
                else -> when {
                    typeStr.contains("accelerometer") || typeStr.contains("gravity") || typeStr.contains("linear") -> "m/s²"
                    typeStr.contains("gyro") -> "rad/s"
                    typeStr.contains("magnetic") -> "µT"
                    typeStr.contains("light") -> "lx"
                    typeStr.contains("temp") -> "°C"
                    typeStr.contains("pressure") -> "hPa"
                    typeStr.contains("proximity") -> "cm"
                    typeStr.contains("step") -> "steps"
                    else -> ""
                }
            }
            SensorInfo(
                name = s.name,
                vendor = s.vendor,
                type = typeStr.replace("android.sensor.", ""),
                typeInt = s.type,
                powerMa = s.power,
                maxRange = s.maximumRange,
                unit = unit
            )
        }
        cachedSensorInfo = info
        return info
    }
}

/**
 * Registers SensorEventListeners for all device sensors and maintains a map
 * of the latest sensor readings. Call [start] to begin listening and [stop] when done.
 *
 * FIX: Uses a versioned counter so callers can detect changes by comparing
 * the version number, since the map reference itself would be the same object.
 * The [getSnapshot] method returns a defensive copy for Compose state detection.
 */
class SensorLiveReader(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _readings = mutableMapOf<Int, FloatArray>()

    @Volatile
    var version: Long = 0L
        private set

    /** Returns a snapshot copy of current readings. Creates a new map each call so Compose detects state change. */
    fun getSnapshot(): Map<Int, FloatArray> {
        synchronized(_readings) {
            return _readings.mapValues { it.value.copyOf() }
        }
    }

    // Legacy accessor — kept for compatibility but callers should prefer getSnapshot()
    val readings: Map<Int, FloatArray> get() = getSnapshot()

    fun start() {
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        sensors.forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(_readings) {
            _readings[event.sensor.type] = event.values.copyOf()
        }
        version++
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
