package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class BatteryHealthData(
    val healthScore: Int,           // 0-100 estimated health score
    val chargeCycles: Int,          // Charge cycle count (system or estimated)
    val currentCapacityMah: Int,    // Estimated current capacity
    val designCapacityMah: Int,     // Design capacity (if available)
    val chargeSpeedMa: Int,         // Current charge speed (when charging)
    val chargeSpeedWatts: Float,    // Current charge speed in watts
    val estimatedTimeToFull: Long,  // ms until full (0 if not charging)
    val estimatedBatteryLife: Long, // ms of battery life remaining (0 if charging)
    val dischargeRatePctPerHour: Float, // Average % drain per hour
    val avgDailySotMs: Long,        // Average daily screen-on time from history
    val cycleSourceIsSystem: Boolean = false // true if cycle count came from system API/sysfs/dumpsys
)

/**
 * Comprehensive battery health estimation engine.
 * Reads system cycle count from Android APIs, sysfs nodes, and Shizuku dumpsys,
 * falling back to cumulative charge-counter estimation.
 */
object BatteryHealthEstimator {
    init {
        System.loadLibrary("core_rs")
    }

    private const val PREFS_NAME = "battery_health_prefs"
    private const val KEY_CHARGE_CYCLES = "charge_cycles"
    private const val KEY_CUMULATIVE_DISCHARGE = "cumulative_discharge"
    private const val KEY_DESIGN_CAPACITY = "design_capacity"
    private const val KEY_LAST_LEVEL = "last_level"
    private const val KEY_LAST_CHARGING = "last_charging"
    private const val KEY_LAST_SYSTEM_CYCLES = "last_system_cycles"
    private const val TAG = "BatteryHealthEstimator"

    /**
     * Track battery level changes to count discharge cycles.
     * Called only from the 30-minute alarm in BatteryTrackerReceiver — not on every UI refresh.
     */
    fun trackCycleData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // If system API provides cycle count, no need to self-track
        if (getSystemCycleCount(context) >= 0) return

        val currentLevel = getCurrentBatteryLevel(context)
        val lastLevel = prefs.getInt(KEY_LAST_LEVEL, currentLevel)
        val isCharging = isCurrentlyCharging(context)

        val editor = prefs.edit()

        if (!isCharging && lastLevel > currentLevel) {
            val discharged = lastLevel - currentLevel
            val cumulative = prefs.getInt(KEY_CUMULATIVE_DISCHARGE, 0) + discharged

            // 1 full cycle = 100% cumulative discharge
            val newCycles = prefs.getInt(KEY_CHARGE_CYCLES, 0) + cumulative / 100
            val remainder = cumulative % 100

            editor.putInt(KEY_CHARGE_CYCLES, newCycles)
            editor.putInt(KEY_CUMULATIVE_DISCHARGE, remainder)
        }

        editor.putInt(KEY_LAST_LEVEL, currentLevel)
        editor.putBoolean(KEY_LAST_CHARGING, isCharging)
        editor.apply()
    }

    /**
     * Attempt to read system-provided cycle count from all available hardware and OS sources:
     * 1. Android 14+ (API 34+) BatteryManager (BATTERY_PROPERTY_CYCLE_COUNT = 7)
     * 2. Intent.ACTION_BATTERY_CHANGED proprietary OEM extras
     * 3. Linux kernel sysfs power supply nodes across Qualcomm, MTK, Exynos, Google Tensor
     * 4. Shizuku privileged dumpsys battery & sysfs execution
     */
    fun getSystemCycleCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Android 14+ (API 34+) BatteryManager
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val cycles = bm.getIntProperty(7) // BATTERY_PROPERTY_CYCLE_COUNT = 7
                if (cycles > 0) {
                    prefs.edit().putInt(KEY_LAST_SYSTEM_CYCLES, cycles).apply()
                    return cycles
                }
            } catch (e: Exception) {
                Log.d(TAG, "BatteryManager cycle count property unavailable: ${e.message}")
            }
        }

        // 2. Battery Changed Intent OEM Extras (Samsung, Xiaomi, Pixel, OnePlus, Motorola)
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            if (intent != null) {
                val candidateKeys = listOf(
                    "android.os.extra.CYCLE_COUNT",
                    "battery_cycle",
                    "cycle_count",
                    "cycle",
                    "charge_cycle",
                    "total_cycle",
                    "battery_cycle_count",
                    "charge_counter_cycle",
                    "mCycleCount"
                )
                for (key in candidateKeys) {
                    val c = intent.getIntExtra(key, -1)
                    if (c > 0) {
                        prefs.edit().putInt(KEY_LAST_SYSTEM_CYCLES, c).apply()
                        return c
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Intent cycle extra check failed: ${e.message}")
        }

        // 3. Sysfs power_supply nodes (World-readable on various kernel builds)
        val sysfsPaths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle",
            "/sys/class/power_supply/battery/cycle",
            "/sys/class/power_supply/battery/total_cycle",
            "/sys/class/power_supply/battery/charge_cycle",
            "/sys/class/power_supply/battery/cycle_count_raw",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/bms/battery_cycle",
            "/sys/class/power_supply/maxfg/cycle_count",
            "/sys/class/power_supply/qcom-battery/cycle_count",
            "/sys/class/power_supply/battery/device/cycle_count",
            "/sys/class/power_supply/device/cycle_count",
            "/sys/class/power_supply/battery/fg_cycle",
            "/sys/class/power_supply/fg_cycle",
            "/sys/devices/platform/battery/power_supply/battery/cycle_count",
            "/sys/devices/soc/soc:qcom,pmic/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/soh"
        )
        for (path in sysfsPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText().trim()
                    val c = text.toIntOrNull()
                    if (c != null && c > 0) {
                        prefs.edit().putInt(KEY_LAST_SYSTEM_CYCLES, c).apply()
                        return c
                    }
                }
            } catch (e: Exception) {}
        }

        // 4. Shizuku / Privileged shell execution if accessible
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // Try dumpsys battery
                val process = Shizuku.newProcess(arrayOf("sh", "-c", "dumpsys battery 2>&1"), null, null)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (l.contains("Cycle count", ignoreCase = true) || 
                        l.contains("mCycleCount", ignoreCase = true) ||
                        l.contains("cycle_count", ignoreCase = true) ||
                        l.contains("battery_cycle", ignoreCase = true)) {
                        val num = l.substringAfter(":").trim().toIntOrNull()
                        if (num != null && num > 0) {
                            process.destroy()
                            prefs.edit().putInt(KEY_LAST_SYSTEM_CYCLES, num).apply()
                            return num
                        }
                    }
                }
                process.waitFor()

                // Try reading kernel sysfs files via Shizuku shell
                for (path in sysfsPaths) {
                    val shProcess = Shizuku.newProcess(arrayOf("sh", "-c", "cat $path 2>/dev/null"), null, null)
                    val shReader = BufferedReader(InputStreamReader(shProcess.inputStream))
                    val out = shReader.readLine()?.trim()?.toIntOrNull()
                    shProcess.waitFor()
                    if (out != null && out > 0) {
                        prefs.edit().putInt(KEY_LAST_SYSTEM_CYCLES, out).apply()
                        return out
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku cycle query error: ${e.message}")
        }

        // Fallback: Check if we previously found a valid system cycle count
        val cachedSystemCycles = prefs.getInt(KEY_LAST_SYSTEM_CYCLES, -1)
        if (cachedSystemCycles > 0) {
            return cachedSystemCycles
        }

        return -1
    }

    /**
     * Get comprehensive battery health data.
     * Does NOT call trackCycleData() — that is handled by the 30-min alarm scheduler.
     */
    fun getHealthData(context: Context): BatteryHealthData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Try system cycle count first (Android API 34+, sysfs, or Shizuku dumpsys)
        val systemCycles = getSystemCycleCount(context)
        val cycleSourceIsSystem = systemCycles >= 0
        val chargeCycles = if (cycleSourceIsSystem) {
            systemCycles
        } else {
            prefs.getInt(KEY_CHARGE_CYCLES, 0)
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = if (voltageMv > 1000) voltageMv / 1000f else voltageMv.toFloat()

        // Current in mA (absolute value)
        val rawCurrentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = kotlin.math.abs(rawCurrentUa / 1000)

        // Capacity from charge counter
        val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val designCapacity = getDesignCapacity(context, prefs)
        val currentCapacity = if (chargeCounterUah > 0 && pct > 0) {
            ((chargeCounterUah / 1000f) / (pct / 100f)).toInt()
        } else {
            designCapacity
        }

        // Health score based on cycles and capacity degradation
        val healthScore = calculateHealthScore(chargeCycles, currentCapacity, designCapacity)

        // Charge speed
        val chargeSpeedMa = if (isCharging && currentMa > 0) currentMa else 0
        val chargeSpeedWatts = if (isCharging && currentMa > 0) (voltageV * currentMa) / 1000f else 0f

        // Time estimates
        val estimatedTimeToFull = if (isCharging && currentMa > 0 && pct < 100) {
            val remainingPct = 100 - pct
            val remainingMah = (designCapacity * remainingPct) / 100f
            ((remainingMah / currentMa) * 3600 * 1000).toLong()
        } else 0L

        // Discharge rate and battery life estimation
        val dischargeRate = calculateDischargeRate(context)
        val estimatedBatteryLife = if (!isCharging && dischargeRate > 0f && pct > 0) {
            ((pct / dischargeRate) * 3600 * 1000).toLong()
        } else 0L

        val avgDailySot = calculateAvgDailySot(context)

        return BatteryHealthData(
            healthScore = healthScore,
            chargeCycles = chargeCycles,
            currentCapacityMah = currentCapacity,
            designCapacityMah = designCapacity,
            chargeSpeedMa = chargeSpeedMa,
            chargeSpeedWatts = chargeSpeedWatts,
            estimatedTimeToFull = estimatedTimeToFull,
            estimatedBatteryLife = estimatedBatteryLife,
            dischargeRatePctPerHour = dischargeRate,
            avgDailySotMs = avgDailySot,
            cycleSourceIsSystem = cycleSourceIsSystem
        )
    }

    private fun getCurrentBatteryLevel(context: Context): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun isCurrentlyCharging(context: Context): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        return statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getDesignCapacity(context: Context, prefs: android.content.SharedPreferences): Int {
        val cached = prefs.getInt(KEY_DESIGN_CAPACITY, 0)
        if (cached > 0) return cached

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentLevel = getCurrentBatteryLevel(context)

        // Try PowerProfile for design capacity
        var design = 0
        try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass).getConstructor(Context::class.java).newInstance(context)
            val cap = Class.forName(powerProfileClass).getMethod("getBatteryCapacity").invoke(mPowerProfile) as? Double
            if (cap != null && cap > 100) design = cap.toInt()
        } catch (e: Exception) {}

        if (design == 0 && chargeCounterUah > 0 && currentLevel > 50) {
            design = ((chargeCounterUah / 1000f) / (currentLevel / 100f)).toInt()
        }

        if (design == 0) design = 4500 // reasonable default for modern phones

        prefs.edit().putInt(KEY_DESIGN_CAPACITY, design).apply()
        return design
    }

    private fun calculateHealthScore(cycles: Int, currentCapacity: Int, designCapacity: Int): Int {
        return calculateHealthScoreNative(cycles, currentCapacity, designCapacity)
    }

    @JvmStatic
    external fun calculateHealthScoreNative(cycles: Int, currentCapacity: Int, designCapacity: Int): Int

    private fun calculateDischargeRate(context: Context): Float {
        val points = BatteryTracker.getHistorySinceLastCharge(context)
        if (points.size < 2) return 0f

        val sorted = points.sortedBy { it.timestamp }
        val first = sorted.first()
        val last = sorted.last()

        val elapsed = last.timestamp - first.timestamp
        if (elapsed <= 0) return 0f

        val drain = first.batteryLevel - last.batteryLevel
        if (drain <= 0) return 0f

        val elapsedHours = elapsed / (1000f * 60f * 60f)
        return drain / elapsedHours
    }

    private fun calculateAvgDailySot(context: Context): Long {
        val history = BatteryTracker.getHistory7d(context)
        if (history.isEmpty()) return 0L

        val dailySot = history.groupBy { point ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = point.timestamp
            "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.mapValues { entry ->
            entry.value.maxOfOrNull { it.sotTodayMs } ?: 0L
        }

        val validDays = dailySot.values.filter { it > 0 }
        return if (validDays.isNotEmpty()) validDays.sum() / validDays.size else 0L
    }
}
