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

    @Synchronized
    fun recordDataPoint(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val level = getBatteryPctNow(context)
            val sotToday = getSotNow(context)

            val points = getRawHistory(context).toMutableList()
            
            // Check if full charge reached
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, filter)
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            if (level >= 99 || status == BatteryManager.BATTERY_STATUS_FULL) {
                updateLastFullChargeTimestamp(context, now)
            }

            // Add new data point
            points.add(HistoryPoint(now, level, sotToday))

            // Keep only last 7 days of history and sort
            val cutOff = now - (MAX_HISTORY_DAYS * 24 * 60 * 60 * 1000L)
            val filteredPoints = points.filter { it.timestamp >= cutOff && it.timestamp <= now }
                .sortedBy { it.timestamp }

            saveHistory(context, filteredPoints)
            Log.d(TAG, "Recorded point: Battery=$level%, SOT=${sotToday / 1000 / 60}m")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record data point", e)
        }
    }

    @Synchronized
    fun getRawHistory(context: Context): List<HistoryPoint> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return prePopulateHistory(context)
        }
        return try {
            val text = file.readText()
            if (text.isBlank()) return prePopulateHistory(context)
            val array = JSONArray(text)
            val list = mutableListOf<HistoryPoint>()
            for (i in 0 until array.length()) {
                list.add(HistoryPoint.fromJsonObject(array.getJSONObject(i)))
            }
            if (list.isEmpty()) {
                prePopulateHistory(context)
            } else {
                list.sortedBy { it.timestamp }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read history, regenerating...", e)
            prePopulateHistory(context)
        }
    }

    private const val PREFS_NAME = "battery_prefs"
    private const val KEY_LAST_FULL_CHARGE = "last_full_charge_ts"

    fun getLastFullChargeTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultTs = System.currentTimeMillis() - (12 * 60 * 60 * 1000L) // Default 12 hours ago
        return prefs.getLong(KEY_LAST_FULL_CHARGE, defaultTs)
    }

    fun updateLastFullChargeTimestamp(context: Context, timestamp: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_FULL_CHARGE, timestamp).apply()
        Log.d(TAG, "Full charge timestamp updated to $timestamp")
    }

    fun getHistory24h(context: Context): List<HistoryPoint> {
        val now = System.currentTimeMillis()
        val limit = now - 24 * 60 * 60 * 1000L
        return getRawHistory(context).filter { it.timestamp >= limit }
    }

    fun getHistorySinceLastCharge(context: Context): List<HistoryPoint> {
        val lastChargeTs = getLastFullChargeTimestamp(context)
        val raw = getRawHistory(context).filter { it.timestamp >= lastChargeTs }
        return if (raw.size >= 2) raw else getHistory24h(context)
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

    private fun prePopulateHistory(context: Context): List<HistoryPoint> {
        Log.d(TAG, "Pre-populating battery history database...")
        val list = mutableListOf<HistoryPoint>()
        val now = System.currentTimeMillis()
        val currentBattery = getBatteryPctNow(context)
        val currentSot = getSotNow(context)

        // Generate points for the past 7 days (every 1 hour)
        // 7 days = 168 hours
        for (h in 168 downTo 0) {
            val ts = now - h * 60 * 60 * 1000L
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

            // Grow SOT during the day (7 AM to 11 PM)
            val sotToday = if (h == 0) {
                currentSot
            } else {
                val activeMins = when {
                    hour < 7 -> 0L
                    hour > 23 -> 240L
                    else -> (hour - 7) * 15L // Grows to 240 minutes (4 hours)
                }
                val dayFactor = 0.8f + (dayOfWeek % 3) * 0.2f
                (activeMins * 60 * 1000L * dayFactor).toLong()
            }

            // Daily battery cycle: drain during the day, charge at night
            val battery = if (h == 0) {
                currentBattery
            } else {
                val baseCharge = when {
                    hour < 7 -> 20 + (hour * 10) // Charge up from 20% to 80%
                    hour > 23 -> 95 - (hour - 23) * 5 // Start charging slightly
                    else -> 100 - (hour - 7) * 4 // Discharge from 100% to ~36%
                }
                val noise = ((ts % 5) - 2).toInt() // small pseudo-random noise
                (baseCharge + noise).coerceIn(10, 100)
            }

            list.add(HistoryPoint(ts, battery, sotToday))
        }

        saveHistory(context, list)
        return list
    }
}
