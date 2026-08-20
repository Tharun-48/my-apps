package com.example.prostats.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

data class HistoryPoint(
    val timestamp: Long,
    val batteryLevel: Int,
    val sotTodayMs: Long,
    val batteryTemp: Float = 0f
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("batteryLevel", batteryLevel)
            put("sotTodayMs", sotTodayMs)
            put("batteryTemp", batteryTemp.toDouble())
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): HistoryPoint {
            return HistoryPoint(
                obj.getLong("timestamp"),
                obj.getInt("batteryLevel"),
                obj.optLong("sotTodayMs", 0L),
                obj.optDouble("batteryTemp", 0.0).toFloat()
            )
        }
    }
}

object BatteryTracker {
    private const val FILE_NAME = "battery_history.json"
    private const val MAX_HISTORY_DAYS = 7
    private const val TAG = "BatteryTracker"

    // In-memory cache to reduce file I/O
    @Volatile
    private var cachedHistory: List<HistoryPoint>? = null
    @Volatile
    private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 5000L // 5 second cache

    @Synchronized
    fun recordDataPoint(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val level = getBatteryPctNow(context)
            val sotToday = getSotSinceLastCharge(context)

            val points = loadHistory(context).toMutableList()
            
            val temp = getBatteryTempNow(context)
            val targetResetLevel = getTargetResetBatteryLevel(context)

            // Auto-reset baseline when device is on charger at or above 90%
            if (isChargingOrFull(context) && level >= targetResetLevel) {
                updateLastUnplugFromFullTimestamp(context, now)
                Log.d(TAG, "Device charging at $level% (>= $targetResetLevel%) — SOT baseline auto-reset")
            }

            // Add new data point
            points.add(HistoryPoint(now, level, sotToday, temp))

            // Keep only last 7 days of history and sort
            val cutOff = now - (MAX_HISTORY_DAYS * 24 * 60 * 60 * 1000L)
            val filteredPoints = points.filter { it.timestamp >= cutOff && it.timestamp <= now }
            val sorted = filteredPoints.sortedBy { it.timestamp }
            saveHistory(context, sorted)
            cachedHistory = sorted
            cacheTimestamp = now
            Log.d(TAG, "Recorded point: Battery=$level%, SOT=${sotToday / 1000 / 60}m")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record data point", e)
        }
    }

    @Synchronized
    fun getRawHistory(context: Context): List<HistoryPoint> {
        val now = System.currentTimeMillis()
        // Return cached data if fresh
        cachedHistory?.let { cached ->
            if (now - cacheTimestamp < CACHE_TTL_MS) {
                return cached
            }
        }
        val history = loadHistory(context)
        cachedHistory = history
        cacheTimestamp = now
        return history
    }

    private fun loadHistory(context: Context): List<HistoryPoint> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val list = mutableListOf<HistoryPoint>()
            for (i in 0 until array.length()) {
                list.add(HistoryPoint.fromJsonObject(array.getJSONObject(i)))
            }
            list.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read history", e)
            emptyList()
        }
    }

    private const val PREFS_NAME = "battery_prefs"
    private const val KEY_LAST_FULL_CHARGE = "last_full_charge_ts"
    private const val KEY_LAST_UNPLUG_FROM_FULL = "last_unplug_from_full_ts"
    private const val KEY_BASELINE_INITIALIZED = "baseline_initialized"

    // Legacy — kept for history graph fallback only
    fun getLastFullChargeTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val defaultTs = now - (24 * 60 * 60 * 1000L)
        return prefs.getLong(KEY_LAST_FULL_CHARGE, defaultTs)
    }

    fun updateLastFullChargeTimestamp(context: Context, timestamp: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_FULL_CHARGE, timestamp).apply()
    }

    private const val KEY_RESET_BATTERY_LEVEL = "reset_battery_level_pct"

    /** Returns target battery percentage (e.g. 90%) to trigger SOT reset on charger unplug. */
    fun getTargetResetBatteryLevel(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RESET_BATTERY_LEVEL, 90)
    }

    fun setTargetResetBatteryLevel(context: Context, levelPct: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RESET_BATTERY_LEVEL, levelPct.coerceIn(50, 100)).apply()
    }

    /** Returns the timestamp when charger was last unplugged at target charge level.
     *  Initializes to current time only on the very first app launch (using a dedicated flag).
     *  Subsequent calls return the stored value without overwriting it. */
    fun getLastUnplugFromFullTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(KEY_BASELINE_INITIALIZED, false)
        if (!initialized) {
            val ts = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_UNPLUG_FROM_FULL, ts)
                .putBoolean(KEY_BASELINE_INITIALIZED, true)
                .apply()
            Log.d(TAG, "First launch: SOT baseline initialized to $ts")
            return ts
        }
        return prefs.getLong(KEY_LAST_UNPLUG_FROM_FULL, System.currentTimeMillis())
    }

    fun updateLastUnplugFromFullTimestamp(context: Context, timestamp: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_UNPLUG_FROM_FULL, timestamp).apply()
        // Invalidate cache so next read picks up the new baseline
        cachedHistory = null
        Log.d(TAG, "Unplug-from-full timestamp updated to $timestamp")
    }

    fun getHistory24h(context: Context): List<HistoryPoint> {
        val now = System.currentTimeMillis()
        val limit = now - 24 * 60 * 60 * 1000L
        return getRawHistory(context).filter { it.timestamp >= limit }
    }

    fun getHistorySinceLastCharge(context: Context): List<HistoryPoint> {
        val lastUnplugTs = getLastUnplugFromFullTimestamp(context)
        if (lastUnplugTs == 0L) return emptyList()
        return getRawHistory(context).filter { it.timestamp >= lastUnplugTs }
    }

    fun getHistory7d(context: Context): List<HistoryPoint> {
        val now = System.currentTimeMillis()
        val limit = now - 7 * 24 * 60 * 60 * 1000L
        return getRawHistory(context).filter { it.timestamp >= limit }
    }

    private fun saveHistory(context: Context, points: List<HistoryPoint>) {
        try {
            val array = JSONArray()
            points.forEach { array.put(it.toJsonObject()) }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save battery history", e)
        }
    }

    private fun getBatteryPctNow(context: Context): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 50
    }

    private fun getBatteryTempNow(context: Context): Float {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10.0f
    }

    private fun isChargingOrFull(context: Context): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getSotSinceLastCharge(context: Context): Long {
        return try {
            val systemMonitor = SystemMonitor(context)
            systemMonitor.getScreenOnTimeSinceLastChargeMs()
        } catch (e: Exception) {
            0L
        }
    }
}
