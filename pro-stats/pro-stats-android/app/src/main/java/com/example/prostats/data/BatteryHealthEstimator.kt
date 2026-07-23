package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

data class BatteryHealthData(
    val healthScore: Int,           // 0-100 estimated health score
    val chargeCycles: Int,          // Estimated charge cycle count
    val currentCapacityMah: Int,    // Estimated current capacity
    val designCapacityMah: Int,     // Design capacity (if available)
    val chargeSpeedMa: Int,         // Current charge speed (when charging)
    val chargeSpeedWatts: Float,    // Current charge speed in watts
    val estimatedTimeToFull: Long,  // ms until full (0 if not charging)
    val estimatedBatteryLife: Long, // ms of battery life remaining (0 if charging)
    val dischargeRatePctPerHour: Float, // Average % drain per hour
    val avgDailySotMs: Long         // Average daily screen-on time from history
)

/**
 * Battery health estimation engine inspired by Battery Guru.
 * Tracks charge cycles, estimates capacity, and provides health scoring.
 */
object BatteryHealthEstimator {

    private const val PREFS_NAME = "battery_health_prefs"
    private const val KEY_CHARGE_CYCLES = "charge_cycles"
    private const val KEY_CUMULATIVE_DISCHARGE = "cumulative_discharge"
    private const val KEY_DESIGN_CAPACITY = "design_capacity"
    private const val KEY_LAST_LEVEL = "last_level"
    private const val KEY_LAST_CHARGING = "last_charging"
    private const val TAG = "BatteryHealthEstimator"

    /**
     * Track battery level changes to count discharge cycles.
     * Call this periodically (e.g., from BatteryTrackerReceiver or dashboard refresh).
     */
    fun trackCycleData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLevel = getCurrentBatteryLevel(context)
        val lastLevel = prefs.getInt(KEY_LAST_LEVEL, currentLevel)
        val isCharging = isCurrentlyCharging(context)
        val wasCharging = prefs.getBoolean(KEY_LAST_CHARGING, false)

        val editor = prefs.edit()

        if (!isCharging && lastLevel > currentLevel) {
            // Accumulate discharge
            val discharged = lastLevel - currentLevel
            val cumulative = prefs.getInt(KEY_CUMULATIVE_DISCHARGE, 0) + discharged
            
            // 1 full cycle = 100% discharge
            val cycles = prefs.getInt(KEY_CHARGE_CYCLES, 0) + cumulative / 100
            val remainder = cumulative % 100

            editor.putInt(KEY_CHARGE_CYCLES, cycles)
            editor.putInt(KEY_CUMULATIVE_DISCHARGE, remainder)
        }

        editor.putInt(KEY_LAST_LEVEL, currentLevel)
        editor.putBoolean(KEY_LAST_CHARGING, isCharging)
        editor.apply()
    }

    /**
     * Get comprehensive battery health data.
     */
    fun getHealthData(context: Context): BatteryHealthData {
        trackCycleData(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val chargeCycles = prefs.getInt(KEY_CHARGE_CYCLES, 0)

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = if (voltageMv > 1000) voltageMv / 1000f else voltageMv.toFloat()

        // Current in mA
        val rawCurrentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = kotlin.math.abs(rawCurrentUa / 1000)

        // Design capacity
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
        val chargeSpeedMa = if (isCharging) currentMa else 0
        val chargeSpeedWatts = if (isCharging) (voltageV * currentMa) / 1000f else 0f

        // Time estimates
        val estimatedTimeToFull = if (isCharging && currentMa > 0 && pct < 100) {
            val remainingPct = 100 - pct
            val remainingMah = (designCapacity * remainingPct) / 100f
            ((remainingMah / currentMa) * 3600 * 1000).toLong() // ms
        } else 0L

        // Discharge rate and battery life estimation
        val dischargeRate = calculateDischargeRate(context)
        val estimatedBatteryLife = if (!isCharging && dischargeRate > 0f && pct > 0) {
            ((pct / dischargeRate) * 3600 * 1000).toLong() // ms
        } else 0L

        // Average daily SOT
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
            avgDailySotMs = avgDailySot
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

        // Try BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER at full charge
        // For now, use a reasonable default
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentLevel = getCurrentBatteryLevel(context)

        val design = if (chargeCounterUah > 0 && currentLevel > 50) {
            // Estimate: current charge / current_level% * 100%
            ((chargeCounterUah / 1000f) / (currentLevel / 100f)).toInt()
        } else {
            4500 // Common default for modern phones
        }

        prefs.edit().putInt(KEY_DESIGN_CAPACITY, design).apply()
        return design
    }

    private fun calculateHealthScore(cycles: Int, currentCapacity: Int, designCapacity: Int): Int {
        // Capacity-based factor (0-100)
        val capacityRatio = if (designCapacity > 0) {
            (currentCapacity.toFloat() / designCapacity).coerceIn(0f, 1.1f)
        } else 1f

        // Cycle-based degradation (typical Li-ion: 80% health at 500 cycles)
        val cycleFactor = (1f - (cycles / 1500f)).coerceIn(0.3f, 1f)

        // Blend both factors
        val score = ((capacityRatio * 0.7f + cycleFactor * 0.3f) * 100f).toInt()
        return score.coerceIn(0, 100)
    }

    /** Calculate average discharge rate in %/hour from recent history */
    private fun calculateDischargeRate(context: Context): Float {
        val lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
        if (lastUnplugTs == 0L) return 0f

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

    /** Calculate average daily SOT from 7-day history */
    private fun calculateAvgDailySot(context: Context): Long {
        val history = BatteryTracker.getHistory7d(context)
        if (history.isEmpty()) return 0L

        // Group by day and find the max SOT per day
        val dailySot = history.groupBy { point ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = point.timestamp
            "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.mapValues { entry ->
            entry.value.maxOfOrNull { it.sotTodayMs } ?: 0L
        }

        val validDays = dailySot.values.filter { it > 0 }
        return if (validDays.isNotEmpty()) {
            validDays.sum() / validDays.size
        } else 0L
    }
}
