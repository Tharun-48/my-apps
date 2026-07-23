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
    val sotTodayMs: Long
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("batteryLevel", batteryLevel)
            put("sotTodayMs", sotTodayMs)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): HistoryPoint {
            return HistoryPoint(
                obj.getLong("timestamp"),
                obj.getInt("batteryLevel"),
                obj.getLong("sotTodayMs")
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
            val sotToday = getSotNow(context)

            val points = loadHistory(context).toMutableList()
            
            // NOTE: charge-unplug tracking is handled by BatteryTrackerReceiver via ACTION_POWER_DISCONNECTED

            // Add new data point
            points.add(HistoryPoint(now, level, sotToday))

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

    /** Returns the timestamp when charger was last unplugged at >=90% charge. Returns 0L if never recorded. */
    fun getLastUnplugFromFullTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_UNPLUG_FROM_FULL, 0L)
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

    private fun getSotNow(context: Context): Long {
        return try {
            val systemMonitor = SystemMonitor(context)
            systemMonitor.getScreenOnTimeMs()
        } catch (e: Exception) {
            0L
        }
    }
}
