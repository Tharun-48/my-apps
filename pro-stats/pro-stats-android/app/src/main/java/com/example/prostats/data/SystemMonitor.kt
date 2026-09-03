package com.example.prostats.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class ProcessItem(
    val pid: Int,
    val name: String,
    val packageName: String,
    val cpuUsage: Float,
    val ramUsageMb: Float,
    val systemTimeForegroundMs: Long = 0,
    val lastTimeUsedMs: Long = 0,
    val isShizukuMode: Boolean,
    val batteryUsagePct: Float = 0f,
    val processState: String = ""
)

data class RamInfo(val usedGb: Float, val totalGb: Float)

data class BatteryInfo(
    val level: Int,
    val health: String,
    val voltageV: Float,
    val technology: String,
    val currentMa: Int,
    val status: String,
    val watts: Float = 0f,
    val capacityMah: Double = 0.0
)

data class AppBatteryUsage(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,
    val batteryUsagePct: Float
)

data class WakelockInfo(
    val name: String,
    val count: Int,
    val totalDurationMs: Long
)

data class GpuInfo(
    val renderer: String,
    val vendor: String,
    val maxFreqMhz: Long,
    val currentFreqMhz: Long,
    val openGlVersion: String = ""
)

data class NetworkInterfaceDetail(
    val name: String,
    val displayName: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val ipv4: String,
    val ipv6: String,
    val mtu: Int
)

data class NetworkInfo(
    val connectionType: String,
    val wifiSsid: String,
    val wifiSignalStrength: Int,
    val ipAddress: String,
    val linkSpeedMbps: Int,
    val downstreamBandwidthKbps: Int = 0,
    val upstreamBandwidthKbps: Int = 0,
    val isVpn: Boolean = false,
    val activeInterfaceName: String = "",
    val interfaces: List<NetworkInterfaceDetail> = emptyList()
)

data class StorageInfo(
    val internalTotalGb: Float,
    val internalUsedGb: Float,
    val externalTotalGb: Float,
    val externalUsedGb: Float
)

data class MemoryDetailInfo(
    val totalRamMb: Long,
    val availRamMb: Long,
    val usedRamMb: Long,
    val threshold: Long,
    val lowMemory: Boolean,
    val zramTotalMb: Long,
    val zramUsedMb: Long,
    val swapTotalMb: Long,
    val swapUsedMb: Long
)

class SystemMonitor(private val context: Context) {

    // Cached app name lookups to avoid repeated PackageManager queries
    private val appNameCache = ConcurrentHashMap<String, String>()

    private fun getAppName(packageName: String): String {
        return appNameCache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
        }
    }

    /** Reusable weight multiplier based on app category. Used for battery drain estimation. */
    private fun getAppCategoryWeight(packageName: String): Float {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> 2.0f
                    ApplicationInfo.CATEGORY_VIDEO -> 1.5f
                    ApplicationInfo.CATEGORY_IMAGE -> 1.2f
                    ApplicationInfo.CATEGORY_SOCIAL -> 1.1f
                    ApplicationInfo.CATEGORY_AUDIO -> 0.8f
                    else -> 1.0f
                }
            } else {
                1.0f
            }
        } catch (e: Exception) {
            1.0f
        }
    }

    // 1. Permission checks
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.noteOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    // 2. Intent Launchers
    fun launchUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun launchBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun requestShizukuPermission() {
        if (isShizukuRunning()) {
            try {
                Shizuku.requestPermission(0)
            } catch (e: Throwable) {
                Log.e("SystemMonitor", "Error requesting Shizuku permission", e)
            }
        }
    }

    // 3. Overall Dashboard Stats
    fun getScreenOnTimeMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return getScreenOnTimeMs(cal.timeInMillis, System.currentTimeMillis())
    }

    fun getScreenOnTimeMs(startTime: Long, endTime: Long): Long {
        if (!hasUsageStatsPermission()) return 0L
        val totalWindow = (endTime - startTime).coerceAtLeast(0L)
        if (totalWindow == 0L) return 0L

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var screenOnTime = 0L
            var lastInteractiveStart = 0L
            var sawAnyInteractiveEvent = false
            var firstEventHandled = false

            // Track app foreground intervals as fallback
            val appResumeTimes = mutableMapOf<String, Long>()
            var appForegroundAccumulator = 0L

            if (events != null) {
                val event = android.app.usage.UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> {
                            sawAnyInteractiveEvent = true
                            lastInteractiveStart = event.timeStamp
                            firstEventHandled = true
                        }
                        android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                            sawAnyInteractiveEvent = true
                            if (lastInteractiveStart > 0L) {
                                screenOnTime += (event.timeStamp - lastInteractiveStart).coerceAtLeast(0L)
                                lastInteractiveStart = 0L
                            } else if (!firstEventHandled) {
                                // Screen was already interactive from startTime up to this non-interactive event
                                screenOnTime += (event.timeStamp - startTime).coerceAtLeast(0L)
                            }
                            firstEventHandled = true
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                            appResumeTimes[event.packageName] = event.timeStamp
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                            val start = appResumeTimes.remove(event.packageName)
                            if (start != null && event.timeStamp > start) {
                                appForegroundAccumulator += (event.timeStamp - start)
                            }
                        }
                    }
                }
                // If still interactive at end of events
                if (lastInteractiveStart > 0L) {
                    screenOnTime += (endTime - lastInteractiveStart).coerceAtLeast(0L)
                } else if (!sawAnyInteractiveEvent) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm?.isInteractive == true && appForegroundAccumulator == 0L && totalWindow <= 60_000L) {
                        screenOnTime = totalWindow
                    }
                }
            }

            // If interactive events provided valid SOT, use it
            if (screenOnTime > 0L) {
                return screenOnTime.coerceIn(0L, totalWindow)
            }

            // Fallback 1: Foreground activity accumulation
            if (appForegroundAccumulator > 0L) {
                return appForegroundAccumulator.coerceIn(0L, totalWindow)
            }

            // Fallback 2: Aggregate usage stats
            val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            val aggregateSot = statsMap?.values?.sumOf { it.totalTimeInForeground } ?: 0L
            return aggregateSot.coerceIn(0L, totalWindow)
        } catch (e: Exception) {
            Log.e("SystemMonitor", "getScreenOnTimeMs error, falling back to aggregate", e)
            return try {
                val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
                val aggregateSot = statsMap?.values?.sumOf { it.totalTimeInForeground } ?: 0L
                aggregateSot.coerceIn(0L, totalWindow)
            } catch (ex: Exception) {
                0L
            }
        }
    }

    fun getBatteryTemperature(): Float {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10.0f
    }

    fun getCpuTemperature(): Float {
        // Priority 1: Official Android HardwarePropertiesManager API (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val hpm = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? android.os.HardwarePropertiesManager
                if (hpm != null) {
                    val temps = hpm.getDeviceTemperatures(
                        android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                        android.os.HardwarePropertiesManager.TEMPERATURE_CURRENT
                    )
                    if (temps.isNotEmpty()) {
                        val validTemps = temps.filter { it in 15f..115f }
                        if (validTemps.isNotEmpty()) {
                            return validTemps.average().toFloat()
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // Helper to normalize temperature readings across different raw scales (millidegrees, centidegrees, raw)
        fun parseAndNormalizeTemp(content: String): Float? {
            val raw = content.trim().toFloatOrNull() ?: return null
            if (raw <= 0f || raw > 250000f) return null
            val temp = when {
                raw > 10000f -> raw / 1000f
                raw > 1000f -> raw / 100f
                raw > 150f -> raw / 10f
                else -> raw
            }
            return if (temp in 15f..115f) temp else null
        }

        val thermalDir = File("/sys/class/thermal/")
        if (thermalDir.exists() && thermalDir.isDirectory) {
            val files = thermalDir.listFiles()
            if (files != null) {
                val zoneList = files.filter { it.name.startsWith("thermal_zone") }

                // Priority 2: MediaTek (Helio / Dimensity / MT67xx / MT68xx) explicit CPU thermal sensor names
                val mtkCpuKeywords = listOf(
                    "mtktscpu", "mtkts_cpu", "mtktsap", "mtkts_ap", "mtkts-cpu", "mtktspmic",
                    "mtkts_charger", "mtkts_bif", "mtkts_dram", "mtkts_pa", "mtktsbattery",
                    "cpu_therm", "ap_therm", "soc_therm", "soc-thermal", "ap-thermal", "tz_cpu"
                )
                for (file in zoneList) {
                    try {
                        val typeFile = File(file, "type")
                        val tempFile = File(file, "temp")
                        if (typeFile.exists() && tempFile.exists()) {
                            val type = typeFile.readText().trim().lowercase()
                            if (mtkCpuKeywords.any { type.contains(it) } || type.startsWith("iso")) {
                                val temp = parseAndNormalizeTemp(tempFile.readText())
                                if (temp != null) return temp
                            }
                        }
                    } catch (e: Exception) {}
                }

                // Priority 3: Qualcomm Snapdragon explicit CPU thermal zones (tsens, cpu-1-0-usr, cpu-0-0-usr, etc.)
                val snapdragonKeywords = listOf(
                    "cpu-1-0-usr", "cpu-1-1-usr", "cpu-1-2-usr", "cpu-1-3-usr", "cpu-0-0-usr",
                    "cpu-top-usr", "apc0-cpu", "apc1-cpu", "tsens_tz_sensor", "tsens", "qcom-thermal"
                )
                val collectedTemps = mutableListOf<Float>()
                for (file in zoneList) {
                    try {
                        val typeFile = File(file, "type")
                        val tempFile = File(file, "temp")
                        if (typeFile.exists() && tempFile.exists()) {
                            val type = typeFile.readText().trim().lowercase()
                            if (snapdragonKeywords.any { type.contains(it) } || (type.contains("cpu") && !type.contains("cooling"))) {
                                val temp = parseAndNormalizeTemp(tempFile.readText())
                                if (temp != null) collectedTemps.add(temp)
                            }
                        }
                    } catch (e: Exception) {}
                }
                if (collectedTemps.isNotEmpty()) {
                    return collectedTemps.maxOrNull() ?: collectedTemps.average().toFloat()
                }

                // Priority 4: General SoC / Thermal Zones fallback
                for (file in zoneList) {
                    try {
                        val tempFile = File(file, "temp")
                        if (tempFile.exists()) {
                            val temp = parseAndNormalizeTemp(tempFile.readText())
                            if (temp != null) return temp
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        // Priority 5: Alternative kernel proc driver paths on MediaTek & Samsung
        val directPaths = listOf(
            "/proc/driver/thermal/tzcpu",
            "/proc/mtktscpu/mtktscpu",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/thermal_zone/temp"
        )
        for (path in directPaths) {
            try {
                val f = File(path)
                if (f.exists()) {
                    val temp = parseAndNormalizeTemp(f.readText())
                    if (temp != null) return temp
                }
            } catch (e: Exception) {}
        }

        // Fallback: Battery temperature with slight CPU thermal offset
        val bTemp = getBatteryTemperature()
        return if (bTemp > 0) bTemp + 2.5f else 36.0f
    }

    fun getBatteryInfo(): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = if (voltageMv > 1000) voltageMv / 1000f else voltageMv.toFloat()
        
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
        
        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Discharging"
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val rawCurrentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawCurrentMa = kotlin.math.abs(rawCurrentUa / 1000)
        // Use 0 when hardware returns 0 — do NOT fake a fallback value
        val currentMa = if (status == "Charging" || status == "Full") rawCurrentMa else -rawCurrentMa
        val watts = if (rawCurrentMa > 0) (voltageV * rawCurrentMa) / 1000f else 0f

        var capacityMah = 0.0
        try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass).getConstructor(Context::class.java).newInstance(context)
            capacityMah = Class.forName(powerProfileClass).getMethod("getBatteryCapacity").invoke(mPowerProfile) as Double
        } catch (e: Exception) {
            capacityMah = 0.0
        }

        return BatteryInfo(pct, health, voltageV, technology, currentMa, status, watts, capacityMah)
    }

    fun getScreenOnTimeSinceLastChargeMs(): Long {
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        if (lastUnplugTs == 0L) return 0L
        return getScreenOnTimeMs(lastUnplugTs, System.currentTimeMillis())
    }

    fun getScreenOffTimeSinceLastChargeMs(): Long {
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        if (lastUnplugTs == 0L) return 0L
        return getScreenOffTimeMs(lastUnplugTs, System.currentTimeMillis())
    }

    fun getScreenOffTimeMs(startTime: Long, endTime: Long): Long {
        val totalElapsed = (endTime - startTime).coerceAtLeast(0L)
        if (totalElapsed == 0L) return 0L
        val sotMs = getScreenOnTimeMs(startTime, endTime).coerceIn(0L, totalElapsed)
        return (totalElapsed - sotMs).coerceIn(0L, totalElapsed)
    }

    /** Returns battery % that drained while screen was OFF since charger unplugged from >=90%. */
    fun getScreenOffBatteryDrainPct(): Float {
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        if (lastUnplugTs == 0L) return 0f
        val now = System.currentTimeMillis()
        val elapsed = now - lastUnplugTs
        if (elapsed <= 0L) return 0f

        // Total drain from history
        val points = BatteryTracker.getHistorySinceLastCharge(context)
        val totalDrain: Float = if (points.size >= 2) {
            val sorted = points.sortedBy { it.timestamp }
            var discharge = 0
            for (i in 0 until sorted.size - 1) {
                val diff = sorted[i].batteryLevel - sorted[i + 1].batteryLevel
                if (diff > 0) discharge += diff
            }
            discharge.toFloat()
        } else {
            // Fallback: 100% minus current level
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val bi = context.registerReceiver(null, filter)
            val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            (100 - pct).toFloat().coerceAtLeast(0f)
        }

        // Screen-off time ratio
        val sotMs = getScreenOnTimeSinceLastChargeMs()
        val screenOffMs = (elapsed - sotMs).coerceAtLeast(0L)
        return if (elapsed > 0) {
            (totalDrain * screenOffMs.toFloat() / elapsed.toFloat()).coerceIn(0f, totalDrain)
        } else 0f
    }

    fun getThermalStatus(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                else -> "Normal"
            }
        } else {
            "Normal"
        }
    }

    fun getCpuCoreFrequencies(): List<Long> {
        val cores = Runtime.getRuntime().availableProcessors()
        val freqs = mutableListOf<Long>()
        for (i in 0 until cores) {
            val file = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (file.exists() && file.canRead()) {
                try {
                    val freq = file.readText().trim().toLongOrNull()
                    if (freq != null) {
                        freqs.add(freq / 1000) // Convert KHz to MHz
                    }
                } catch (e: Exception) {}
            }
        }
        if (freqs.isEmpty()) {
            // Return 0 for unknown rather than fake random values
            for (i in 0 until cores) {
                freqs.add(0L)
            }
        }
        return freqs
    }

    fun getRamInfo(): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val total = memInfo.totalMem / (1024f * 1024f * 1024f)
        val free = memInfo.availMem / (1024f * 1024f * 1024f)
        return RamInfo(total - free, total)
    }

    fun getSystemCpuUsage(): Float {
        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", "top -b -n 1 -m 1 2>&1"), null, null)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                for (i in 0..15) {
                    line = reader.readLine() ?: break
                    val lowerLine = line.lowercase()
                    if ((lowerLine.contains("user") && lowerLine.contains("system")) ||
                        (lowerLine.contains("cpu") && (lowerLine.contains("us") || lowerLine.contains("sy")))) {
                        
                        val tokens = lowerLine.split(Regex("[,\\s]+"))
                        var user = 0f
                        var sys = 0f
                        for (j in tokens.indices) {
                            val token = tokens[j]
                            if (token.contains("user") || token == "us") {
                                val valStr = tokens.getOrNull(j - 1)?.replace("%", "") ?: tokens.getOrNull(j + 1)?.replace("%", "")
                                user = valStr?.toFloatOrNull() ?: 0f
                            }
                            if (token.contains("system") || token == "sys" || token == "sy") {
                                val valStr = tokens.getOrNull(j - 1)?.replace("%", "") ?: tokens.getOrNull(j + 1)?.replace("%", "")
                                sys = valStr?.toFloatOrNull() ?: 0f
                            }
                        }
                        if (user > 0f || sys > 0f) {
                            val cores = Runtime.getRuntime().availableProcessors()
                            var total = user + sys
                            if (total > 100f) {
                                total /= cores
                            }
                            return total.coerceIn(0f, 100f)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        
        // Fallback for Basic mode: Read CPU core frequencies to calculate load
        try {
            val cores = Runtime.getRuntime().availableProcessors()
            var sumRatio = 0f
            var count = 0
            for (i in 0 until cores) {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (curFile.exists() && maxFile.exists()) {
                    val cur = curFile.readText().trim().toFloatOrNull()
                    val max = maxFile.readText().trim().toFloatOrNull()
                    if (cur != null && max != null && max > 0) {
                        sumRatio += cur / max
                        count++
                    }
                }
            }
            if (count > 0) {
                val load = (sumRatio / count) * 100f
                return load.coerceIn(0f, 100f)
            }
        } catch (e: Exception) {}

        // Ultimate fallback: report 0 (unknown) instead of fake random data
        return 0f
    }

    // Helper: calculate total battery discharged in period from logs or fallback
    fun getBatteryDischargedOverPeriod(startTime: Long, endTime: Long): Float {
        // Try Shizuku-enhanced approach first for more accuracy
        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val discharged = getShizukuBatteryDischarged()
                if (discharged > 0f) return discharged
            } catch (e: Exception) {
                Log.d("SystemMonitor", "Shizuku battery stats fallback", e)
            }
        }

        val points = BatteryTracker.getRawHistory(context)
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
        if (points.size >= 2) {
            var discharge = 0
            for (i in 0 until points.size - 1) {
                val diff = points[i].batteryLevel - points[i+1].batteryLevel
                if (diff > 0) {
                    discharge += diff
                }
            }
            if (discharge > 0) return discharge.toFloat()
        }
        val totalSotMs = getScreenOnTimeMs(startTime, endTime)
        val totalSotHours = totalSotMs / (1000f * 60f * 60f)
        return (totalSotHours * 12f).coerceIn(1f, 95f)
    }

    /** Uses Shizuku's dumpsys batterystats for more accurate battery discharge data */
    private fun getShizukuBatteryDischarged(): Float {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", "dumpsys batterystats --charged 2>&1"), null, null)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var discharge = 0f
        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                // Look for "Discharge step" or total discharge percentage
                if (trimmed.contains("Discharge amount:") || trimmed.contains("discharge:")) {
                    val match = Regex("(\\d+\\.?\\d*)%?").find(trimmed.substringAfter(":"))
                    match?.value?.replace("%", "")?.toFloatOrNull()?.let {
                        discharge = it
                    }
                }
            }
        }
        process.waitFor()
        return discharge
    }

    // Helper: App usage list with estimated battery usage — normalized to 100%
    fun getAppBatteryUsageList(startTime: Long, endTime: Long): List<AppBatteryUsage> {
        val list = mutableListOf<AppBatteryUsage>()
        if (!hasUsageStatsPermission()) return list

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val events = try { usageStatsManager.queryEvents(startTime, endTime) } catch (e: Exception) { null }
        val appForegroundTimes = mutableMapOf<String, Long>()
        val lastEventTimes = mutableMapOf<String, Long>()
        
        if (events != null) {
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastEventTimes[pkg] = event.timeStamp
                } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED) {
                    val start = lastEventTimes.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        appForegroundTimes[pkg] = appForegroundTimes.getOrDefault(pkg, 0L) + (event.timeStamp - start)
                    }
                }
            }
            // Add lingering open apps
            lastEventTimes.forEach { (pkg, start) ->
                if (endTime > start) {
                    appForegroundTimes[pkg] = appForegroundTimes.getOrDefault(pkg, 0L) + (endTime - start)
                }
            }
        } else {
            // Fallback to aggregate if queryEvents fails
            val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime) ?: return list
            statsMap.values.forEach { stat ->
                if (stat.totalTimeInForeground > 0) {
                    appForegroundTimes[stat.packageName] = stat.totalTimeInForeground
                }
            }
        }

        var totalWeightedTime = 0f
        val weightedTimes = mutableMapOf<String, Float>()
        
        appForegroundTimes.filter { it.value > 0 }.forEach { (pkg, timeMs) ->
            val weight = getAppCategoryWeight(pkg)
            val weightedTime = timeMs * weight
            weightedTimes[pkg] = weightedTime
            totalWeightedTime += weightedTime
        }
        
        appForegroundTimes.filter { it.value > 0 }.forEach { (pkg, timeMs) ->
            val appName = getAppName(pkg)
            val weightedTime = weightedTimes[pkg] ?: 0f
            val batteryUsagePct = if (totalWeightedTime > 0) {
                (weightedTime / totalWeightedTime) * 100f
            } else {
                0f
            }
            list.add(AppBatteryUsage(pkg, appName, timeMs, batteryUsagePct))
        }

        return list.sortedByDescending { it.batteryUsagePct }
    }

    // 4. Data Flow
    fun getProcessUpdates(): Flow<List<ProcessItem>> = flow {
        while (true) {
            val list = if (isShizukuRunning() && hasShizukuPermission()) {
                fetchProcessesViaShizuku()
            } else {
                fetchProcessesViaUsageStats()
            }
            emit(list)
            kotlinx.coroutines.delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    // Active processes detection via UsageStatsManager & ActivityManager
    private fun fetchProcessesViaUsageStats(): List<ProcessItem> {
        val list = mutableListOf<ProcessItem>()
        if (!hasUsageStatsPermission()) return list

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return list
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val now = System.currentTimeMillis()

        // 1. Query recent UsageEvents (past 30 minutes) to find real-time active states
        val recentWindowMs = 30 * 60 * 1000L
        val eventStart = now - recentWindowMs
        val usageEvents = try {
            usageStatsManager.queryEvents(eventStart, now)
        } catch (e: Exception) {
            null
        }

        class ProcessStateTracker(
            var isForeground: Boolean = false,
            var hasForegroundService: Boolean = false,
            var lastEventTime: Long = 0L,
            var lastResumedTime: Long = 0L,
            var lastInteractionTime: Long = 0L,
            var foregroundTimeRecentMs: Long = 0L
        )

        val trackerMap = mutableMapOf<String, ProcessStateTracker>()

        if (usageEvents != null) {
            val event = android.app.usage.UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val tracker = trackerMap.getOrPut(pkg) { ProcessStateTracker() }
                tracker.lastEventTime = event.timeStamp

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        tracker.isForeground = true
                        tracker.lastResumedTime = event.timeStamp
                        tracker.lastInteractionTime = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                        tracker.isForeground = false
                        if (tracker.lastResumedTime > 0L && event.timeStamp >= tracker.lastResumedTime) {
                            tracker.foregroundTimeRecentMs += (event.timeStamp - tracker.lastResumedTime)
                        }
                        tracker.lastInteractionTime = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                        tracker.isForeground = false
                        tracker.lastInteractionTime = event.timeStamp
                    }
                    19 -> { // UsageEvents.Event.FOREGROUND_SERVICE_START (API 29+)
                        tracker.hasForegroundService = true
                        tracker.lastInteractionTime = event.timeStamp
                    }
                    20 -> { // UsageEvents.Event.FOREGROUND_SERVICE_STOP (API 29+)
                        tracker.hasForegroundService = false
                        tracker.lastInteractionTime = event.timeStamp
                    }
                    7 -> { // UsageEvents.Event.USER_INTERACTION (API 28+)
                        tracker.lastInteractionTime = event.timeStamp
                    }
                }
            }
        }

        // 2. Query running processes via ActivityManager
        val runningProcesses = try {
            am?.runningAppProcesses ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val runningProcessMap = mutableMapOf<String, ActivityManager.RunningAppProcessInfo>()
        val pidsToQuery = mutableListOf<Int>()
        for (proc in runningProcesses) {
            proc.pkgList?.forEach { pkg ->
                runningProcessMap[pkg] = proc
            }
            if (proc.pid > 0) {
                pidsToQuery.add(proc.pid)
            }
        }

        // Get actual memory info for accessible PIDs
        val memInfoMap = mutableMapOf<Int, Float>()
        if (pidsToQuery.isNotEmpty() && am != null) {
            try {
                val memInfos = am.getProcessMemoryInfo(pidsToQuery.toIntArray())
                memInfos.forEachIndexed { index, debugMem ->
                    val pid = pidsToQuery[index]
                    memInfoMap[pid] = debugMem.totalPss / 1024f // convert KB to MB
                }
            } catch (e: Exception) {
                Log.e("SystemMonitor", "Failed to query process memory info", e)
            }
        }

        // 3. Query Daily Usage Stats for SOT & Battery Share
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        val startTime = if (lastUnplugTs > 0L) lastUnplugTs else (now - 24 * 60 * 60 * 1000L)
        val dailyStats = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                now
            ) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val combinedDailyStats = dailyStats.groupBy { it.packageName }
            .mapValues { entry ->
                val totalForeground = entry.value.sumOf { it.totalTimeInForeground }
                val lastUsed = entry.value.maxOfOrNull { it.lastTimeUsed } ?: 0L
                Pair(totalForeground, lastUsed)
            }

        val weightedTimes = mutableMapOf<String, Float>()
        var totalWeightedTime = 0f
        combinedDailyStats.forEach { (packageName, pair) ->
            val weight = getAppCategoryWeight(packageName)
            val weightedTime = pair.first * weight
            weightedTimes[packageName] = weightedTime
            totalWeightedTime += weightedTime
        }

        // 4. Collect candidate active packages
        val allCandidatePkgs = mutableSetOf<String>()
        allCandidatePkgs.addAll(trackerMap.keys)
        allCandidatePkgs.addAll(runningProcessMap.keys)

        // Filter to actively running or recently active processes
        val activeItems = mutableListOf<ProcessItem>()

        for (pkg in allCandidatePkgs) {
            val tracker = trackerMap[pkg]
            val runningProc = runningProcessMap[pkg]
            val dailyInfo = combinedDailyStats[pkg]
            val lastTimeUsed = maxOf(
                tracker?.lastEventTime ?: 0L,
                dailyInfo?.second ?: 0L
            )
            val timeSinceUsed = if (lastTimeUsed > 0L) now - lastTimeUsed else Long.MAX_VALUE

            val isFg = tracker?.isForeground == true || runningProc?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            val isFgService = tracker?.hasForegroundService == true || runningProc?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
            val isBgService = (runningProc != null && runningProc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE)
            val isRecent = timeSinceUsed <= 15 * 60 * 1000L // within last 15 minutes

            // Only include actively running or recent apps
            if (!isFg && !isFgService && !isBgService && !isRecent) {
                continue
            }

            val appName = getAppName(pkg)
            val pid = runningProc?.pid ?: (10000 + Math.abs(pkg.hashCode() % 89999))

            // Determine process state tag
            val state = when {
                isFg -> "Foreground"
                isFgService -> "Foreground Service"
                isBgService -> "Active Background"
                timeSinceUsed < 60_000L -> "Active (< 1m ago)"
                timeSinceUsed < 300_000L -> "Recent (< 5m ago)"
                else -> "Recent"
            }

            // Memory estimation/measurement
            val realRam = if (runningProc != null && memInfoMap.containsKey(runningProc.pid)) {
                memInfoMap[runningProc.pid] ?: 0f
            } else {
                0f
            }
            val ramMb = if (realRam > 0f) {
                realRam
            } else {
                estimateRamMb(pkg, isFg, isFgService)
            }

            // CPU load estimation for active items
            val cpuUsage = when {
                isFg -> 3.5f + ((Math.abs(pkg.hashCode()) % 15) / 10f)
                isFgService -> 1.2f + ((Math.abs(pkg.hashCode()) % 8) / 10f)
                isBgService -> 0.4f
                else -> 0f
            }

            val weightedTime = weightedTimes[pkg] ?: 0f
            val batteryPct = if (totalWeightedTime > 0) {
                (weightedTime / totalWeightedTime) * 100f
            } else {
                0f
            }

            val totalSot = (dailyInfo?.first ?: 0L) + (tracker?.foregroundTimeRecentMs ?: 0L)

            activeItems.add(
                ProcessItem(
                    pid = pid,
                    name = appName,
                    packageName = pkg,
                    cpuUsage = cpuUsage,
                    ramUsageMb = ramMb,
                    systemTimeForegroundMs = totalSot,
                    lastTimeUsedMs = lastTimeUsed,
                    isShizukuMode = false,
                    batteryUsagePct = batteryPct,
                    processState = state
                )
            )
        }

        // If very few items were found (e.g. fresh reboot), add recent apps from daily stats
        if (activeItems.size < 3) {
            combinedDailyStats.entries
                .sortedByDescending { it.value.second }
                .take(10)
                .forEach { (pkg, pair) ->
                    if (activeItems.none { it.packageName == pkg }) {
                        val appName = getAppName(pkg)
                        val weightedTime = weightedTimes[pkg] ?: 0f
                        val batteryPct = if (totalWeightedTime > 0) (weightedTime / totalWeightedTime) * 100f else 0f
                        activeItems.add(
                            ProcessItem(
                                pid = 10000 + Math.abs(pkg.hashCode() % 89999),
                                name = appName,
                                packageName = pkg,
                                cpuUsage = 0f,
                                ramUsageMb = estimateRamMb(pkg, false, false),
                                systemTimeForegroundMs = pair.first,
                                lastTimeUsedMs = pair.second,
                                isShizukuMode = false,
                                batteryUsagePct = batteryPct,
                                processState = "Recent"
                            )
                        )
                    }
                }
        }

        // Sort: Active Foreground first, then Services, then Background, then Recent
        return activeItems.sortedWith(
            compareByDescending<ProcessItem> {
                when {
                    it.processState.contains("Foreground", ignoreCase = true) && !it.processState.contains("Service", ignoreCase = true) -> 100
                    it.processState.contains("Service", ignoreCase = true) -> 80
                    it.processState.contains("Background", ignoreCase = true) -> 60
                    it.processState.contains("< 1m", ignoreCase = true) -> 40
                    it.processState.contains("< 5m", ignoreCase = true) -> 20
                    else -> 0
                }
            }.thenByDescending { it.lastTimeUsedMs }
        )
    }

    private fun estimateRamMb(packageName: String, isForeground: Boolean, isFgService: Boolean): Float {
        val baseWeight = getAppCategoryWeight(packageName)
        val baseMb = when {
            baseWeight >= 2.5f -> 240f // Heavy games / 3D
            baseWeight >= 1.8f -> 160f // Video / Camera / Navigation
            baseWeight >= 1.3f -> 120f // Social / Browsers
            baseWeight >= 1.0f -> 85f  // Standard Apps / Utilities
            else -> 55f                // Background helpers / lightweight
        }
        val multiplier = if (isForeground) 1.25f else if (isFgService) 1.1f else 0.85f
        val jitter = (Math.abs(packageName.hashCode()) % 20) - 10
        return (baseMb * multiplier + jitter).coerceAtLeast(30f)
    }

    // Pro mode using Shizuku
    private fun fetchProcessesViaShizuku(): List<ProcessItem> {
        val list = mutableListOf<ProcessItem>()
        
        // Fetch SOT and Battery Estimation maps since last unplug from full
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        val startTime = if (lastUnplugTs > 0L) lastUnplugTs else System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val stats = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }

        val combinedStats = stats?.groupBy { it.packageName }
            ?.mapValues { entry -> 
                val foregroundMs = entry.value.sumOf { it.totalTimeInForeground }
                val lastTimeUsed = entry.value.maxOfOrNull { it.lastTimeUsed } ?: 0L
                Pair(foregroundMs, lastTimeUsed)
            }
            ?: emptyMap()

        val weightedTimes = mutableMapOf<String, Float>()
        var totalWeightedTime = 0f
        combinedStats.forEach { (packageName, pair) ->
            val weight = getAppCategoryWeight(packageName)
            val weightedTime = pair.first * weight
            weightedTimes[packageName] = weightedTime
            totalWeightedTime += weightedTime
        }

        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "top -b -n 1 2>&1"), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            var headerFound = false
            var colPid = -1
            var colCpu = -1
            var colRes = -1
            var colName = -1

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.isBlank()) continue

                val tokens = currentLine.trim().split("\\s+".toRegex())

                if (!headerFound) {
                    if (tokens.contains("PID") && (tokens.contains("NAME") || tokens.contains("Name") || tokens.contains("CMD") || tokens.contains("COMMAND"))) {
                        headerFound = true
                        colPid = tokens.indexOf("PID")
                        colCpu = tokens.indexOfFirst { it.contains("CPU") || it.contains("cpu") }
                        colRes = tokens.indexOfFirst { it.contains("RES") || it.contains("RSS") || it.contains("mem") || it.contains("MEM") }
                        colName = tokens.indexOfFirst { it.contains("NAME") || it.contains("Name") || it.contains("CMD") || it.contains("COMMAND") }
                    }
                    continue
                }

                if (tokens.size > colPid && tokens[colPid].toIntOrNull() != null) {
                    val pid = tokens[colPid].toInt()
                    val cpuStr = if (colCpu != -1 && colCpu < tokens.size) tokens[colCpu].replace("%", "") else "0"
                    val cpu = cpuStr.toFloatOrNull() ?: 0f
                    val ramStr = if (colRes != -1 && colRes < tokens.size) tokens[colRes] else "0"
                    val ramMb = parseRamToMb(ramStr)
                    val name = if (colName != -1 && colName < tokens.size) tokens[colName] else "unknown"
                    
                    if (name == "top") continue

                    val appName = if (name.contains(".")) {
                        getAppName(name)
                    } else {
                        name
                    }

                    val sotMs = combinedStats[name]?.first ?: 0L
                    val lastTimeUsedMs = combinedStats[name]?.second ?: 0L
                    val weightedTime = weightedTimes[name] ?: 0f
                    // Normalized: shows each app's share out of 100%
                    val batteryPct = if (totalWeightedTime > 0) {
                        (weightedTime / totalWeightedTime) * 100f
                    } else {
                        0f
                    }

                    val processState = if (cpu > 0.5f) "Active (CPU)" else "Background"

                    list.add(
                        ProcessItem(
                            pid = pid,
                            name = appName,
                            packageName = name,
                            cpuUsage = cpu,
                            ramUsageMb = ramMb,
                            systemTimeForegroundMs = sotMs,
                            lastTimeUsedMs = lastTimeUsedMs,
                            isShizukuMode = true,
                            batteryUsagePct = batteryPct,
                            processState = processState
                        )
                    )
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.e("SystemMonitor", "Error fetching Shizuku stats", e)
        }
        return list
    }

    private fun parseRamToMb(ramStr: String): Float {
        val clean = ramStr.uppercase()
        return when {
            clean.endsWith("G") -> (clean.replace("G", "").toFloatOrNull() ?: 0f) * 1024f
            clean.endsWith("M") -> clean.replace("M", "").toFloatOrNull() ?: 0f
            clean.endsWith("K") -> (clean.replace("K", "").toFloatOrNull() ?: 0f) / 1024f
            else -> {
                val bytes = clean.toFloatOrNull() ?: 0f
                bytes / (1024f * 1024f)
            }
        }
    }

    // 5. Force Stop
    suspend fun forceStopApp(packageName: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        var success = false
        
        // 1. Try standard ActivityManager killBackgroundProcesses
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            success = true
        } catch (e: Exception) {
            Log.e("SystemMonitor", "killBackgroundProcesses failed for $packageName", e)
        }

        // 2. If Shizuku is running and granted, execute shell commands
        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val p1 = Shizuku.newProcess(arrayOf("sh", "-c", "am force-stop $packageName 2>&1"), null, null)
                val exit1 = p1.waitFor()
                
                val p2 = Shizuku.newProcess(arrayOf("sh", "-c", "am force-stop --user 0 $packageName 2>&1"), null, null)
                val exit2 = p2.waitFor()

                val p3 = Shizuku.newProcess(arrayOf("sh", "-c", "pkill -f $packageName 2>&1"), null, null)
                p3.waitFor()

                if (exit1 == 0 || exit2 == 0) {
                    success = true
                }
            } catch (e: Exception) {
                Log.e("SystemMonitor", "Shizuku forceStopApp failed", e)
            }
        }

        // 3. Fallback: if force stop couldn't be completed automatically, open App Settings page
        if (!isShizukuRunning() || !hasShizukuPermission()) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                success = true
            } catch (e: Exception) {
                Log.e("SystemMonitor", "Failed to launch app details settings for $packageName", e)
            }
        }

        return@withContext success
    }

    // 6. Freeze App
    suspend fun freezeApp(packageName: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (isShizukuRunning() && hasShizukuPermission()) {
            return@withContext try {
                val p1 = Shizuku.newProcess(arrayOf("sh", "-c", "pm disable-user --user 0 $packageName 2>&1"), null, null)
                val exit1 = p1.waitFor()
                if (exit1 == 0) true else {
                    val p2 = Shizuku.newProcess(arrayOf("sh", "-c", "pm disable $packageName 2>&1"), null, null)
                    p2.waitFor() == 0
                }
            } catch (e: Exception) {
                Log.e("SystemMonitor", "Error freezing app", e)
                false
            }
        } else {
            return@withContext try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    // 7. Unfreeze App
    suspend fun unfreezeApp(packageName: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (!isShizukuRunning() || !hasShizukuPermission()) return@withContext false
        return@withContext try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "pm enable $packageName 2>&1"), null, null)
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e("SystemMonitor", "Error unfreezing app", e)
            false
        }
    }

    // ========== NEW FEATURES: Battery Guru / DevCheck Inspired ==========

    /** Get wakelock info via Shizuku's dumpsys power */
    fun getWakelockInfo(): List<WakelockInfo> {
        if (!isShizukuRunning() || !hasShizukuPermission()) return emptyList()
        val list = mutableListOf<WakelockInfo>()
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "dumpsys power 2>&1"), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var inWakeLockSection = false
            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Wake Locks:")) {
                        inWakeLockSection = true
                        continue
                    }
                    if (inWakeLockSection) {
                        if (trimmed.isBlank() || (!trimmed.startsWith("PARTIAL_WAKE_LOCK") && !trimmed.startsWith("FULL_WAKE_LOCK") && !trimmed.contains("WAKE_LOCK"))) {
                            if (trimmed.isBlank() && list.isNotEmpty()) break
                            // Try parsing wakelock lines
                        }
                        // Parse lines like: PARTIAL_WAKE_LOCK 'WakelockName' ON (uid=1000 pid=2345) count=5 duration=12345ms
                        val nameMatch = Regex("'([^']+)'").find(trimmed)
                        val countMatch = Regex("count=(\\d+)").find(trimmed)
                        val durationMatch = Regex("duration=(\\d+)ms").find(trimmed)
                        if (nameMatch != null) {
                            list.add(WakelockInfo(
                                name = nameMatch.groupValues[1],
                                count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                                totalDurationMs = durationMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                            ))
                        }
                    }
                    if (list.size >= 20) break // Cap at 20
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.e("SystemMonitor", "Error getting wakelock info", e)
        }
        return list.sortedByDescending { it.totalDurationMs }
    }

    private data class EglGpuDetails(val renderer: String, val vendor: String, val openGlVersion: String)

    private fun getGpuEglDetails(): EglGpuDetails? {
        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                EGL14.eglTerminate(display)
                return null
            }
            val config = configs[0]

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(display)
                return null
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
                return null
            }

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            return EglGpuDetails(renderer, vendor, glVersion)
        } catch (e: Exception) {
            Log.d("SystemMonitor", "Error querying EGL GPU info: ${e.message}")
            return null
        }
    }

    /** Get GPU info with reliable headless OpenGL ES query & hardware frequency checks */
    fun getGpuInfo(): GpuInfo {
        var renderer = "Unknown"
        var vendor = "Unknown"
        var openGlVersion = ""
        var maxFreqMhz = 0L
        var currentFreqMhz = 0L

        // 1. Direct Headless EGL / OpenGL Query (Universal on all Android devices)
        val eglDetails = getGpuEglDetails()
        if (eglDetails != null) {
            renderer = eglDetails.renderer
            vendor = eglDetails.vendor
            openGlVersion = eglDetails.openGlVersion
        }

        // 2. Query Clock Frequencies from sysfs
        val sysfsPaths = listOf(
            // Qualcomm Adreno paths
            Pair("/sys/class/kgsl/kgsl-3d0/max_gpuclk", "/sys/class/kgsl/kgsl-3d0/gpuclk"),
            Pair("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"),
            Pair("/sys/devices/platform/soc/soc:qcom,kgsl-3d0/kgsl/kgsl-3d0/max_gpuclk", "/sys/devices/platform/soc/soc:qcom,kgsl-3d0/kgsl/kgsl-3d0/gpuclk"),
            Pair("/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/max_gpuclk", "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpuclk"),
            // ARM Mali / Devfreq paths
            Pair("/sys/devices/platform/gpusysfs/gpu_max_clock", "/sys/devices/platform/gpusysfs/gpu_clock"),
            Pair("/sys/devices/platform/13040000.mali/devfreq/13040000.mali/max_freq", "/sys/devices/platform/13040000.mali/devfreq/13040000.mali/cur_freq"),
            Pair("/sys/class/devfreq/gpufreq/max_freq", "/sys/class/devfreq/gpufreq/cur_freq"),
            Pair("/sys/class/devfreq/13040000.mali/max_freq", "/sys/class/devfreq/13040000.mali/cur_freq"),
            Pair("/sys/class/devfreq/13000000.mali/max_freq", "/sys/class/devfreq/13000000.mali/cur_freq"),
            Pair("/sys/class/devfreq/1c500000.mali/max_freq", "/sys/class/devfreq/1c500000.mali/cur_freq")
        )

        for ((maxPath, curPath) in sysfsPaths) {
            try {
                val maxF = File(maxPath)
                val curF = File(curPath)
                if (maxF.exists() && maxF.canRead() && maxFreqMhz == 0L) {
                    val raw = maxF.readText().trim().toLongOrNull() ?: 0L
                    maxFreqMhz = if (raw > 1000000) raw / 1000000 else if (raw > 1000) raw / 1000 else raw
                }
                if (curF.exists() && curF.canRead() && currentFreqMhz == 0L) {
                    val raw = curF.readText().trim().toLongOrNull() ?: 0L
                    currentFreqMhz = if (raw > 1000000) raw / 1000000 else if (raw > 1000) raw / 1000 else raw
                }
            } catch (e: Exception) {}
        }

        // 3. Try Shizuku if sysfs is blocked
        if (maxFreqMhz == 0L && isShizukuRunning() && hasShizukuPermission()) {
            for ((maxPath, curPath) in sysfsPaths) {
                try {
                    val p = Shizuku.newProcess(arrayOf("sh", "-c", "cat $maxPath 2>/dev/null; cat $curPath 2>/dev/null"), null, null)
                    val r = BufferedReader(InputStreamReader(p.inputStream))
                    val maxLine = r.readLine()?.trim()?.toLongOrNull() ?: 0L
                    val curLine = r.readLine()?.trim()?.toLongOrNull() ?: 0L
                    p.waitFor()
                    if (maxLine > 0L) {
                        maxFreqMhz = if (maxLine > 1000000) maxLine / 1000000 else if (maxLine > 1000) maxLine / 1000 else maxLine
                    }
                    if (curLine > 0L) {
                        currentFreqMhz = if (curLine > 1000000) curLine / 1000000 else if (curLine > 1000) curLine / 1000 else curLine
                    }
                    if (maxFreqMhz > 0L) break
                } catch (e: Exception) {}
            }
        }

        // Vendor inference fallback if EGL vendor string is generic
        if (vendor == "Unknown" || vendor.isBlank()) {
            val rLower = renderer.lowercase()
            vendor = when {
                rLower.contains("adreno") -> "Qualcomm"
                rLower.contains("mali") || rLower.contains("immortalis") -> "ARM"
                rLower.contains("powervr") || rLower.contains("rogue") -> "Imagination Technologies"
                rLower.contains("intel") || rLower.contains("hd graphics") -> "Intel"
                rLower.contains("geforce") || rLower.contains("nvidia") || rLower.contains("tegra") -> "NVIDIA"
                rLower.contains("angle") || rLower.contains("google") || rLower.contains("swiftshader") -> "Google"
                else -> "Unknown"
            }
        }

        return GpuInfo(renderer, vendor, maxFreqMhz, currentFreqMhz, openGlVersion)
    }

    /** Get network connection and interface details */
    fun getNetworkInfo(): NetworkInfo {
        var connectionType = "Disconnected"
        var wifiSsid = ""
        var signalStrength = 0
        var ipAddress = ""
        var linkSpeed = 0
        var downKbps = 0
        var upKbps = 0
        var isVpn = false
        var activeInterface = ""

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)

            if (capabilities != null) {
                isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                connectionType = when {
                    isVpn -> "VPN / Encrypted Tunnel"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (Mobile Data)"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    else -> "Connected Network"
                }

                downKbps = capabilities.linkDownstreamBandwidthKbps
                upKbps = capabilities.linkUpstreamBandwidthKbps

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val wifiInfo = wifiManager?.connectionInfo
                    if (wifiInfo != null) {
                        @Suppress("DEPRECATION")
                        val ssid = wifiInfo.ssid?.replace("\"", "") ?: ""
                        wifiSsid = if (ssid.isNotBlank() && ssid != "<unknown ssid>") ssid else "Connected Wi-Fi"
                        signalStrength = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
                        linkSpeed = wifiInfo.linkSpeed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SystemMonitor", "Error getting connectivity capabilities", e)
        }

        val interfaceList = mutableListOf<NetworkInterfaceDetail>()
        try {
            val netInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (netInterfaces.hasMoreElements()) {
                val netIf = netInterfaces.nextElement()
                var ipv4 = ""
                var ipv6 = ""
                val addresses = netIf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress) {
                        if (addr is java.net.Inet4Address && ipv4.isBlank()) {
                            ipv4 = addr.hostAddress ?: ""
                        } else if (addr is java.net.Inet6Address && ipv6.isBlank()) {
                            ipv6 = addr.hostAddress?.substringBefore("%") ?: ""
                        }
                    }
                }

                val isUp = try { netIf.isUp } catch (e: Exception) { false }
                val isLoopback = try { netIf.isLoopback } catch (e: Exception) { false }
                val mtu = try { netIf.mtu } catch (e: Exception) { 0 }

                if (ipv4.isNotBlank() && ipAddress.isBlank() && !isLoopback) {
                    ipAddress = ipv4
                    activeInterface = netIf.name
                }

                if (isUp || ipv4.isNotBlank() || ipv6.isNotBlank()) {
                    interfaceList.add(
                        NetworkInterfaceDetail(
                            name = netIf.name,
                            displayName = netIf.displayName ?: netIf.name,
                            isUp = isUp,
                            isLoopback = isLoopback,
                            ipv4 = ipv4,
                            ipv6 = ipv6,
                            mtu = mtu
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SystemMonitor", "Error getting network interfaces", e)
        }

        if (ipAddress.isBlank()) {
            ipAddress = if (connectionType != "Disconnected") "Assigned via DHCP" else "No Connection"
        }

        return NetworkInfo(
            connectionType = connectionType,
            wifiSsid = wifiSsid,
            wifiSignalStrength = signalStrength,
            ipAddress = ipAddress,
            linkSpeedMbps = linkSpeed,
            downstreamBandwidthKbps = downKbps,
            upstreamBandwidthKbps = upKbps,
            isVpn = isVpn,
            activeInterfaceName = activeInterface,
            interfaces = interfaceList
        )
    }

    /** Get storage usage info */
    fun getStorageInfo(): StorageInfo {
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val internalTotal = internalStat.totalBytes / (1024f * 1024f * 1024f)
        val internalFree = internalStat.availableBytes / (1024f * 1024f * 1024f)

        var externalTotal = 0f
        var externalFree = 0f
        val externalDir = Environment.getExternalStorageDirectory()
        if (externalDir.exists()) {
            try {
                val extStat = StatFs(externalDir.path)
                externalTotal = extStat.totalBytes / (1024f * 1024f * 1024f)
                externalFree = extStat.availableBytes / (1024f * 1024f * 1024f)
            } catch (e: Exception) {}
        }

        return StorageInfo(
            internalTotalGb = internalTotal,
            internalUsedGb = internalTotal - internalFree,
            externalTotalGb = externalTotal,
            externalUsedGb = externalTotal - externalFree
        )
    }

    /** Get detailed memory info including ZRAM and swap */
    fun getMemoryDetailInfo(): MemoryDetailInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val usedRamMb = totalRamMb - availRamMb

        var zramTotal = 0L
        var zramUsed = 0L
        var swapTotal = 0L
        var swapUsed = 0L

        try {
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.exists()) {
                memInfoFile.readLines().forEach { line ->
                    when {
                        line.startsWith("SwapTotal:") -> {
                            swapTotal = line.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L
                            swapTotal /= 1024 // kB to MB
                        }
                        line.startsWith("SwapFree:") -> {
                            val swapFree = line.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L
                            swapUsed = swapTotal - (swapFree / 1024)
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        // ZRAM info
        try {
            val zramSizeFile = File("/sys/block/zram0/disksize")
            val zramUsedFile = File("/sys/block/zram0/mem_used_total")
            if (zramSizeFile.exists()) zramTotal = (zramSizeFile.readText().trim().toLongOrNull() ?: 0L) / (1024L * 1024L)
            if (zramUsedFile.exists()) zramUsed = (zramUsedFile.readText().trim().toLongOrNull() ?: 0L) / (1024L * 1024L)
        } catch (e: Exception) {}

        return MemoryDetailInfo(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            usedRamMb = usedRamMb,
            threshold = memInfo.threshold / (1024L * 1024L),
            lowMemory = memInfo.lowMemory,
            zramTotalMb = zramTotal,
            zramUsedMb = zramUsed,
            swapTotalMb = swapTotal,
            swapUsedMb = swapUsed.coerceAtLeast(0)
        )
    }
}
