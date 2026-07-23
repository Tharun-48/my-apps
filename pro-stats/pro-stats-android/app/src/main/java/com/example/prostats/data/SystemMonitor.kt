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
import android.os.BatteryManager
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
    val batteryUsagePct: Float = 0f
)

data class RamInfo(val usedGb: Float, val totalGb: Float)

data class BatteryInfo(
    val level: Int,
    val health: String,
    val voltageV: Float,
    val technology: String,
    val currentMa: Int,
    val status: String,
    val watts: Float = 0f
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
    val currentFreqMhz: Long
)

data class NetworkInfo(
    val connectionType: String,
    val wifiSsid: String,
    val wifiSignalStrength: Int,
    val ipAddress: String,
    val linkSpeedMbps: Int
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
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
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
        if (!hasUsageStatsPermission()) return 0
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime) ?: return 0
        return statsMap.values.sumOf { it.totalTimeInForeground }
    }

    fun getBatteryTemperature(): Float {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10.0f
    }

    fun getCpuTemperature(): Float {
        val thermalDir = File("/sys/class/thermal/")
        if (thermalDir.exists() && thermalDir.isDirectory) {
            val files = thermalDir.listFiles()
            if (files != null) {
                // 1. Search for 'iso' prefixed thermal zones (Dimensity / MediaTek custom)
                for (file in files) {
                    if (file.name.startsWith("thermal_zone")) {
                        try {
                            val typeFile = File(file, "type")
                            val tempFile = File(file, "temp")
                            if (typeFile.exists() && tempFile.exists()) {
                                val type = typeFile.readText().trim().lowercase()
                                if (type.startsWith("iso")) {
                                    val tempRaw = tempFile.readText().trim().toFloatOrNull()
                                    if (tempRaw != null) {
                                        val temp = if (tempRaw > 1000) tempRaw / 1000f else tempRaw
                                        if (temp in 15f..95f) return temp
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
                
                // 2. Search for 'cpu' thermal zones
                for (file in files) {
                    if (file.name.startsWith("thermal_zone")) {
                        try {
                            val typeFile = File(file, "type")
                            val tempFile = File(file, "temp")
                            if (typeFile.exists() && tempFile.exists()) {
                                val type = typeFile.readText().trim().lowercase()
                                if (type.contains("cpu")) {
                                    val tempRaw = tempFile.readText().trim().toFloatOrNull()
                                    if (tempRaw != null) {
                                        val temp = if (tempRaw > 1000) tempRaw / 1000f else tempRaw
                                        if (temp in 15f..95f) return temp
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                // 3. Search for other typical SoC thermal zone types
                val commonSoCTypes = listOf("tsens", "mtk", "ap-thermal", "soc-thermal", "bms", "battery")
                for (file in files) {
                    if (file.name.startsWith("thermal_zone")) {
                        try {
                            val typeFile = File(file, "type")
                            val tempFile = File(file, "temp")
                            if (typeFile.exists() && tempFile.exists()) {
                                val type = typeFile.readText().trim().lowercase()
                                if (commonSoCTypes.any { type.contains(it) }) {
                                    val tempRaw = tempFile.readText().trim().toFloatOrNull()
                                    if (tempRaw != null) {
                                        val temp = if (tempRaw > 1000) tempRaw / 1000f else tempRaw
                                        if (temp in 15f..95f) return temp
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
                
                // 4. Default: try reading thermal_zone0 or thermal_zone1 directly
                for (zoneName in listOf("thermal_zone0", "thermal_zone1", "thermal_zone2")) {
                    val zoneDir = File(thermalDir, zoneName)
                    if (zoneDir.exists()) {
                        try {
                            val tempFile = File(zoneDir, "temp")
                            if (tempFile.exists()) {
                                val tempRaw = tempFile.readText().trim().toFloatOrNull()
                                if (tempRaw != null) {
                                    val temp = if (tempRaw > 1000) tempRaw / 1000f else tempRaw
                                    if (temp in 15f..95f) return temp
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        
        // Fallback: Battery temperature
        return getBatteryTemperature()
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
        var rawCurrentMa = kotlin.math.abs(rawCurrentUa / 1000)
        if (rawCurrentMa == 0) rawCurrentMa = 1250 // reasonable fallback if API returns 0

        val currentMa = if (status == "Charging" || status == "Full") rawCurrentMa else -rawCurrentMa
        val watts = (voltageV * kotlin.math.abs(currentMa)) / 1000f

        return BatteryInfo(pct, health, voltageV, technology, currentMa, status, watts)
    }

    fun getScreenOnTimeSinceLastChargeMs(): Long {
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        if (lastUnplugTs == 0L) return 0L
        return getScreenOnTimeMs(lastUnplugTs, System.currentTimeMillis())
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
                val process = Shizuku.newProcess(arrayOf("top", "-b", "-n", "1", "-m", "1"), null, null)
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
        val process = Shizuku.newProcess(arrayOf("dumpsys", "batterystats", "--charged"), null, null)
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
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime) ?: return list

        val appUsageData = statsMap.values.filter { it.totalTimeInForeground > 0 }
        val weightedTimes = mutableMapOf<String, Float>()
        var totalWeightedTime = 0f

        appUsageData.forEach { stat ->
            val pkg = stat.packageName
            val weight = getAppCategoryWeight(pkg)
            val weightedTime = stat.totalTimeInForeground * weight
            weightedTimes[pkg] = weightedTime
            totalWeightedTime += weightedTime
        }

        appUsageData.forEach { stat ->
            val pkg = stat.packageName
            val appName = getAppName(pkg)

            val weightedTime = weightedTimes[pkg] ?: 0f
            // Normalized to 100% — each app's share of total drain
            val batteryUsagePct = if (totalWeightedTime > 0) {
                (weightedTime / totalWeightedTime) * 100f
            } else {
                0f
            }

            list.add(AppBatteryUsage(pkg, appName, stat.totalTimeInForeground, batteryUsagePct))
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

    // Fallback mode using UsageStatsManager
    private fun fetchProcessesViaUsageStats(): List<ProcessItem> {
        val list = mutableListOf<ProcessItem>()
        if (!hasUsageStatsPermission()) return list

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        val startTime = if (lastUnplugTs > 0L) lastUnplugTs else {
            // Fallback: last 24h if never unplugged from full
            System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            System.currentTimeMillis()
        ) ?: return list

        // Group by package to avoid duplicate entries for the same package
        val combinedStats = stats.groupBy { it.packageName }
            .mapValues { entry ->
                val totalForeground = entry.value.sumOf { it.totalTimeInForeground }
                val lastTimeUsed = entry.value.maxOfOrNull { it.lastTimeUsed } ?: 0L
                Pair(totalForeground, lastTimeUsed)
            }
            .filter { it.value.first > 0 }
            .toList()
            .sortedByDescending { it.second.second } // Sort by last time used

        val weightedTimes = mutableMapOf<String, Float>()
        var totalWeightedTime = 0f

        combinedStats.forEach { (packageName, pair) ->
            val weight = getAppCategoryWeight(packageName)
            val weightedTime = pair.first * weight
            weightedTimes[packageName] = weightedTime
            totalWeightedTime += weightedTime
        }

        combinedStats.forEachIndexed { index, (packageName, pair) ->
            val appName = getAppName(packageName)

            val weightedTime = weightedTimes[packageName] ?: 0f
            // Normalized: shows each app's share out of 100%
            val batteryPct = if (totalWeightedTime > 0) {
                (weightedTime / totalWeightedTime) * 100f
            } else {
                0f
            }

            list.add(
                ProcessItem(
                    pid = index + 1000,
                    name = appName,
                    packageName = packageName,
                    cpuUsage = 0f,
                    ramUsageMb = 0f,
                    systemTimeForegroundMs = pair.first,
                    lastTimeUsedMs = pair.second,
                    isShizukuMode = false,
                    batteryUsagePct = batteryPct
                )
            )
        }
        return list
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
            val process = Shizuku.newProcess(arrayOf("top", "-b", "-n", "1"), null, null)
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
                            batteryUsagePct = batteryPct
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
                val p1 = Shizuku.newProcess(arrayOf("am", "force-stop", packageName), null, null)
                val exit1 = p1.waitFor()
                
                val p2 = Shizuku.newProcess(arrayOf("am", "force-stop", "--user", "0", packageName), null, null)
                val exit2 = p2.waitFor()

                val p3 = Shizuku.newProcess(arrayOf("pkill", "-f", packageName), null, null)
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
                val p1 = Shizuku.newProcess(arrayOf("pm", "disable-user", "--user", "0", packageName), null, null)
                val exit1 = p1.waitFor()
                if (exit1 == 0) true else {
                    val p2 = Shizuku.newProcess(arrayOf("pm", "disable", packageName), null, null)
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
            val process = Shizuku.newProcess(arrayOf("pm", "enable", packageName), null, null)
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
            val process = Shizuku.newProcess(arrayOf("dumpsys", "power"), null, null)
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

    /** Get GPU info from sysfs or Shizuku */
    fun getGpuInfo(): GpuInfo {
        var renderer = "Unknown"
        var vendor = "Unknown"
        var maxFreqMhz = 0L
        var currentFreqMhz = 0L

        // Try Qualcomm Adreno path
        val kgslDir = File("/sys/class/kgsl/kgsl-3d0/")
        if (kgslDir.exists()) {
            vendor = "Qualcomm Adreno"
            try {
                val maxFreqFile = File(kgslDir, "max_gpuclk")
                val curFreqFile = File(kgslDir, "gpuclk")
                if (maxFreqFile.exists()) maxFreqMhz = (maxFreqFile.readText().trim().toLongOrNull() ?: 0L) / 1000000
                if (curFreqFile.exists()) currentFreqMhz = (curFreqFile.readText().trim().toLongOrNull() ?: 0L) / 1000000
            } catch (e: Exception) {}
        }

        // Try Mali path
        if (maxFreqMhz == 0L) {
            val maliDir = File("/sys/devices/platform/").listFiles()?.find { it.name.contains("mali") || it.name.contains("gpu") }
            if (maliDir != null) {
                vendor = "ARM Mali"
                try {
                    val maxFreqFile = File(maliDir, "max_freq")
                    val curFreqFile = File(maliDir, "cur_freq")
                    if (maxFreqFile.exists()) maxFreqMhz = (maxFreqFile.readText().trim().toLongOrNull() ?: 0L) / 1000000
                    if (curFreqFile.exists()) currentFreqMhz = (curFreqFile.readText().trim().toLongOrNull() ?: 0L) / 1000000
                } catch (e: Exception) {}
            }
        }

        // Try devfreq path (common for Mali/IMG)
        if (maxFreqMhz == 0L) {
            try {
                val devfreqDir = File("/sys/class/devfreq/")
                val gpuDev = devfreqDir.listFiles()?.find { it.name.contains("gpu") || it.name.contains("mali") || it.name.contains("13000000") }
                if (gpuDev != null) {
                    val maxFile = File(gpuDev, "max_freq")
                    val curFile = File(gpuDev, "cur_freq")
                    if (maxFile.exists()) maxFreqMhz = (maxFile.readText().trim().toLongOrNull() ?: 0L) / 1000000
                    if (curFile.exists()) currentFreqMhz = (curFile.readText().trim().toLongOrNull() ?: 0L) / 1000000
                    if (vendor == "Unknown") vendor = "GPU"
                }
            } catch (e: Exception) {}
        }

        // Try Shizuku dumpsys for renderer name
        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val process = Shizuku.newProcess(arrayOf("dumpsys", "SurfaceFlinger", "--list"), null, null)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                // Just confirm it's accessible; renderer info is limited from dumpsys
                process.waitFor()
            } catch (e: Exception) {}
        }

        renderer = vendor

        return GpuInfo(renderer, vendor, maxFreqMhz, currentFreqMhz)
    }

    /** Get network connection details */
    fun getNetworkInfo(): NetworkInfo {
        var connectionType = "Disconnected"
        var wifiSsid = ""
        var signalStrength = 0
        var ipAddress = ""
        var linkSpeed = 0

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)

            if (capabilities != null) {
                connectionType = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    else -> "Other"
                }

                if (connectionType == "Wi-Fi") {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    @Suppress("DEPRECATION")
                    wifiSsid = wifiInfo.ssid?.replace("\"", "") ?: ""
                    signalStrength = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
                    linkSpeed = wifiInfo.linkSpeed
                    val ip = wifiInfo.ipAddress
                    ipAddress = String.format(
                        "%d.%d.%d.%d",
                        ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SystemMonitor", "Error getting network info", e)
        }

        return NetworkInfo(connectionType, wifiSsid, signalStrength, ipAddress, linkSpeed)
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
