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
    val level: Int,
    val capacityMah: Double = 0.0,
    val cycleCount: Int = -1,
    val cycleSource: String = ""
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
    val unit: String = "",
    val category: String = "General",
    val hardwareSource: String = "Hardware Sensor",
    val isWakeUp: Boolean = false
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

        var capacityMah = 0.0
        try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass).getConstructor(Context::class.java).newInstance(context)
            val capResult = Class.forName(powerProfileClass).getMethod("getBatteryCapacity").invoke(mPowerProfile)
            capacityMah = (capResult as? Number)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            capacityMah = 0.0
        }

        var cycleCount = -1
        var cycleSource = ""
        try {
            val sysCycles = BatteryHealthEstimator.getSystemCycleCount(context)
            if (sysCycles > 0) {
                cycleCount = sysCycles
                cycleSource = "System"
            } else {
                val healthData = BatteryHealthEstimator.getHealthData(context)
                cycleCount = healthData.chargeCycles
                cycleSource = if (healthData.cycleSourceIsSystem) "System" else "Estimated"
            }
        } catch (e: Exception) {}

        return HwBatteryInfo(healthString, technology, voltage, temperature, level, capacityMah, cycleCount, cycleSource)
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

    private fun detectProcessorVendor(): String {
        val hardware = (Build.HARDWARE + " " + Build.BOARD + " " + Build.MANUFACTURER).lowercase()
        return when {
            hardware.contains("qcom") || hardware.contains("qualcomm") || hardware.contains("snapdragon") || hardware.contains("sm") -> "Qualcomm Snapdragon"
            hardware.contains("mt") || hardware.contains("mediatek") || hardware.contains("helio") || hardware.contains("dimensity") -> "MediaTek"
            hardware.contains("exynos") || hardware.contains("samsung") || hardware.contains("universal") -> "Samsung Exynos"
            hardware.contains("tensor") || hardware.contains("gs") || hardware.contains("zuma") -> "Google Tensor"
            hardware.contains("unisoc") || hardware.contains("sprd") || hardware.contains("sc") -> "Unisoc"
            else -> "System SoC"
        }
    }

    fun getSensorInfo(): List<SensorInfo> {
        cachedSensorInfo?.let { return it }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val procVendor = detectProcessorVendor()

        val info = sensors.map { s ->
            val typeStr = s.stringType ?: "Unknown"
            val unit = when (s.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
                Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
                Sensor.TYPE_LIGHT -> "lx"
                Sensor.TYPE_PRESSURE -> "hPa"
                Sensor.TYPE_PROXIMITY -> "cm"
                @Suppress("DEPRECATION") Sensor.TYPE_TEMPERATURE, Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
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

            val category = when (s.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION,
                Sensor.TYPE_SIGNIFICANT_MOTION, Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER -> "Motion & Kinematics"

                Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Dynamics & Gyro"

                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                @Suppress("DEPRECATION") Sensor.TYPE_ORIENTATION -> "Magnetics & Compass"

                Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE,
                @Suppress("DEPRECATION") Sensor.TYPE_TEMPERATURE, Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_RELATIVE_HUMIDITY -> "Environment & Climate"

                Sensor.TYPE_PROXIMITY, Sensor.TYPE_HEART_RATE, Sensor.TYPE_HEART_BEAT,
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Biometrics & Presence"

                else -> "Processor & System Hub"
            }

            val vendorLower = s.vendor.lowercase()
            val nameLower = s.name.lowercase()

            val source = when {
                vendorLower.contains("bosch") || nameLower.contains("bmi") || nameLower.contains("bma") || nameLower.contains("bmp") -> "Bosch Sensortec (MEMS)"
                vendorLower.contains("stm") || vendorLower.contains("stmicro") || nameLower.contains("lsm") || nameLower.contains("lis") -> "STMicroelectronics (MEMS)"
                vendorLower.contains("invensense") || vendorLower.contains("tdk") || nameLower.contains("icm") || nameLower.contains("mpu") -> "InvenSense / TDK"
                vendorLower.contains("asahi") || vendorLower.contains("akm") || nameLower.contains("ak") -> "Asahi Kasei (AKM)"
                vendorLower.contains("ams") || vendorLower.contains("taos") || nameLower.contains("tmd") -> "ams OSRAM"
                vendorLower.contains("goodix") || nameLower.contains("goodix") -> "Goodix Hardware"
                vendorLower.contains("sensortek") || nameLower.contains("stk") -> "Sensortek"
                vendorLower.contains("sitronix") || nameLower.contains("stk") -> "Sitronix"
                vendorLower.contains("qti") || vendorLower.contains("qualcomm") || nameLower.contains("qti") -> "Snapdragon Sensor Core (ADSP)"
                vendorLower.contains("mediatek") || vendorLower.contains("mtk") || nameLower.contains("mtk") -> "MediaTek SCP Sensor Hub"
                vendorLower.contains("google") -> "Google CHRE Context Hub"
                vendorLower.contains("samsung") -> "Samsung Sensor Hub"
                else -> "$procVendor Sensor Subsystem"
            }

            SensorInfo(
                name = s.name,
                vendor = s.vendor,
                type = typeStr.replace("android.sensor.", ""),
                typeInt = s.type,
                powerMa = s.power,
                maxRange = s.maximumRange,
                unit = unit,
                category = category,
                hardwareSource = source,
                isWakeUp = s.isWakeUpSensor
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
    private val _readings = mutableMapOf<String, FloatArray>()

    @Volatile
    var version: Long = 0L
        private set

    /** Returns a snapshot copy of current readings keyed by "${sensor.type}_${sensor.name}". */
    fun getSnapshot(): Map<String, FloatArray> {
        synchronized(_readings) {
            return _readings.mapValues { it.value.copyOf() }
        }
    }

    fun start() {
        try {
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            sensors.forEach { sensor ->
                try {
                    // Use SENSOR_DELAY_NORMAL (5Hz) to drastically reduce RAM/CPU usage compared to 60Hz UI delay
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                } catch (e: Exception) {
                    // Ignore unregisterable OEM sensors
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val key = "${event.sensor.type}_${event.sensor.name}"
        synchronized(_readings) {
            _readings[key] = event.values.copyOf()
        }
        version++
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
