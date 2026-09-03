package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

/**
 * Detailed charging session record (inspired by Battery Guru & AccuBattery).
 */
data class ChargingSession(
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val energyAddedMah: Int,
    val peakTempC: Float,
    val avgCurrentMa: Int,
    val chargerType: String = "AC"
) {
    val durationMs: Long get() = (endTime - startTime).coerceAtLeast(0L)
    val levelGained: Int get() = (endLevel - startLevel).coerceAtLeast(0)

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("startTime", startTime)
            put("endTime", endTime)
            put("startLevel", startLevel)
            put("endLevel", endLevel)
            put("energyAddedMah", energyAddedMah)
            put("peakTempC", peakTempC.toDouble())
            put("avgCurrentMa", avgCurrentMa)
            put("chargerType", chargerType)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ChargingSession {
            return ChargingSession(
                startTime = obj.getLong("startTime"),
                endTime = obj.getLong("endTime"),
                startLevel = obj.getInt("startLevel"),
                endLevel = obj.getInt("endLevel"),
                energyAddedMah = obj.optInt("energyAddedMah", 0),
                peakTempC = obj.optDouble("peakTempC", 0.0).toFloat(),
                avgCurrentMa = obj.optInt("avgCurrentMa", 0),
                chargerType = obj.optString("chargerType", "AC")
            )
        }
    }
}

/**
 * Deep sleep & screen-off analytics model (inspired by Battery Guru & BetterBatteryStats).
 */
data class DeepSleepStats(
    val deepSleepTimeMs: Long,
    val screenOffAwakeTimeMs: Long,
    val totalScreenOffTimeMs: Long,
    val deepSleepPct: Float,
    val screenOffDrainRatePctPerHour: Float
)

object BatteryTracker {
    private const val FILE_NAME = "battery_history.json"
    private const val CHARGING_SESSIONS_FILE = "charging_sessions.json"
    private const val MAX_HISTORY_DAYS = 7
    private const val MAX_SESSIONS = 25
    private const val TAG = "BatteryTracker"

    // In-memory cache to reduce file I/O
    @Volatile
    private var cachedHistory: List<HistoryPoint>? = null
    @Volatile
    private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 5000L

    // In-memory charging session tracking state
    @Volatile
    private var currentChargeStartTime: Long = 0L
    @Volatile
    private var currentChargeStartLevel: Int = -1
    @Volatile
    private var currentChargePeakTemp: Float = 0f
    @Volatile
    private var currentChargeCurrentSamples = mutableListOf<Int>()

    @Synchronized
    fun recordDataPoint(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val level = getBatteryPctNow(context)
            val sotToday = getSotSinceLastCharge(context)
            val temp = getBatteryTempNow(context)
            val isCharging = isChargingOrFull(context)

            // Update in-progress charge metrics
            if (isCharging) {
                if (currentChargeStartTime == 0L) {
                    currentChargeStartTime = now
                    currentChargeStartLevel = level
                    currentChargePeakTemp = temp
                    currentChargeCurrentSamples.clear()
                } else {
                    if (temp > currentChargePeakTemp) currentChargePeakTemp = temp
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val curUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    val curMa = kotlin.math.abs(curUa / 1000)
                    if (curMa > 0) currentChargeCurrentSamples.add(curMa)
                }
            } else if (currentChargeStartTime > 0L) {
                // Charger just disconnected -> finalize session
                finalizeChargingSession(context, now, level)
            }

            val points = loadHistory(context).toMutableList()
            val targetResetLevel = getTargetResetBatteryLevel(context)

            // Auto-reset baseline when device is on charger at or above reset threshold (e.g. 90%)
            if (isCharging && level >= targetResetLevel) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record data point", e)
        }
    }

    @Synchronized
    fun onPowerConnected(context: Context) {
        val now = System.currentTimeMillis()
        val level = getBatteryPctNow(context)
        val temp = getBatteryTempNow(context)
        currentChargeStartTime = now
        currentChargeStartLevel = level
        currentChargePeakTemp = temp
        currentChargeCurrentSamples.clear()
        Log.d(TAG, "Power connected at $level%, temp $temp°C")
    }

    @Synchronized
    fun onPowerDisconnected(context: Context) {
        val now = System.currentTimeMillis()
        val level = getBatteryPctNow(context)
        finalizeChargingSession(context, now, level)
        
        val targetResetLevel = getTargetResetBatteryLevel(context)
        if (level >= targetResetLevel) {
            updateLastUnplugFromFullTimestamp(context, now)
            Log.d(TAG, "Unplugged at $level% >= $targetResetLevel% — Reset baseline")
        }
    }

    private fun finalizeChargingSession(context: Context, endTime: Long, endLevel: Int) {
        if (currentChargeStartTime == 0L || currentChargeStartLevel == -1) return
        val duration = endTime - currentChargeStartTime
        // Only log sessions longer than 2 minutes and with at least 1% gained
        if (duration >= 2 * 60 * 1000L && endLevel >= currentChargeStartLevel) {
            val avgCurrent = if (currentChargeCurrentSamples.isNotEmpty()) {
                currentChargeCurrentSamples.average().toInt()
            } else {
                0
            }
            val gainedPct = endLevel - currentChargeStartLevel
            val prefs = context.getSharedPreferences("battery_health_prefs", Context.MODE_PRIVATE)
            val designCap = prefs.getInt("design_capacity", 4500)
            val energyMah = (designCap * gainedPct) / 100

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val chargerType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "AC Charger"
            }

            val session = ChargingSession(
                startTime = currentChargeStartTime,
                endTime = endTime,
                startLevel = currentChargeStartLevel,
                endLevel = endLevel,
                energyAddedMah = energyMah,
                peakTempC = currentChargePeakTemp,
                avgCurrentMa = avgCurrent,
                chargerType = chargerType
            )
            saveChargingSession(context, session)
            Log.d(TAG, "Recorded charging session: +$gainedPct% ($energyMah mAh) in ${duration / 60000}m")
        }
        currentChargeStartTime = 0L
        currentChargeStartLevel = -1
        currentChargeCurrentSamples.clear()
    }

    @Synchronized
    fun getChargingSessions(context: Context): List<ChargingSession> {
        val file = File(context.filesDir, CHARGING_SESSIONS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val list = mutableListOf<ChargingSession>()
            for (i in 0 until array.length()) {
                list.add(ChargingSession.fromJsonObject(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.endTime }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading charging sessions", e)
            emptyList()
        }
    }

    private fun saveChargingSession(context: Context, session: ChargingSession) {
        try {
            val existing = getChargingSessions(context).toMutableList()
            existing.add(0, session)
            val trimmed = existing.take(MAX_SESSIONS)
            val array = JSONArray()
            trimmed.forEach { array.put(it.toJsonObject()) }
            val file = File(context.filesDir, CHARGING_SESSIONS_FILE)
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save charging session", e)
        }
    }

    /**
     * Calculates kernel deep sleep vs screen-off awake time and screen-off drain rate.
     */
    fun getDeepSleepStats(context: Context): DeepSleepStats {
        val lastUnplug = getLastUnplugFromFullTimestamp(context)
        val elapsedTotal = (System.currentTimeMillis() - lastUnplug).coerceAtLeast(1L)
        val sotMs = getSotSinceLastCharge(context)
        val totalScreenOffMs = (elapsedTotal - sotMs).coerceAtLeast(0L)

        // Kernel deep sleep = total elapsedRealtime - total uptimeMillis
        val uptimeSinceBoot = SystemClock.uptimeMillis()
        val realSinceBoot = SystemClock.elapsedRealtime()
        val deepSleepTotalBoot = (realSinceBoot - uptimeSinceBoot).coerceAtLeast(0L)

        // Proportionate deep sleep within the current charge cycle
        val deepSleepInCycle = if (realSinceBoot > 0) {
            ((deepSleepTotalBoot.toDouble() / realSinceBoot.toDouble()) * totalScreenOffMs).toLong()
        } else {
            (totalScreenOffMs * 0.85).toLong()
        }.coerceIn(0L, totalScreenOffMs)

        val awakeInCycle = (totalScreenOffMs - deepSleepInCycle).coerceAtLeast(0L)
        val deepSleepPct = if (totalScreenOffMs > 0) {
            (deepSleepInCycle.toFloat() / totalScreenOffMs.toFloat()) * 100f
        } else {
            0f
        }.coerceIn(0f, 100f)

        // Screen-off drain rate (%/hour)
        val drainRate = try {
            val history = getHistorySinceLastCharge(context)
            if (history.size >= 2) {
                val first = history.first()
                val last = history.last()
                val totalDrain = (first.batteryLevel - last.batteryLevel).coerceAtLeast(0)
                val hours = totalScreenOffMs / (1000f * 60f * 60f)
                val elapsed = (last.timestamp - first.timestamp).coerceAtLeast(1L)
                val screenOffRatio = (totalScreenOffMs.toFloat() / elapsed.toFloat()).coerceIn(0f, 1f)
                if (hours > 0.1f) (totalDrain * screenOffRatio) / hours else 0.5f
            } else {
                0.5f
            }
        } catch (e: Exception) {
            0.5f
        }

        return DeepSleepStats(
            deepSleepTimeMs = deepSleepInCycle,
            screenOffAwakeTimeMs = awakeInCycle,
            totalScreenOffTimeMs = totalScreenOffMs,
            deepSleepPct = deepSleepPct,
            screenOffDrainRatePctPerHour = drainRate
        )
    }

    @Synchronized
    fun getRawHistory(context: Context): List<HistoryPoint> {
        val now = System.currentTimeMillis()
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
        if (!file.exists()) return emptyList()
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
    private const val KEY_RESET_BATTERY_LEVEL = "reset_battery_level_pct"

    // Alarm preferences (Battery Guru feature)
    const val KEY_CHARGE_ALARM_ENABLED = "charge_alarm_enabled"
    const val KEY_CHARGE_ALARM_LEVEL = "charge_alarm_level" // e.g. 80, 85, 90, 100
    const val KEY_TEMP_ALARM_ENABLED = "temp_alarm_enabled"
    const val KEY_TEMP_ALARM_LIMIT = "temp_alarm_limit" // e.g. 40, 42, 45
    const val KEY_LOW_BATTERY_ALARM_ENABLED = "low_battery_alarm_enabled"
    const val KEY_LOW_BATTERY_ALARM_LEVEL = "low_battery_alarm_level" // e.g. 15, 20

    fun isChargeAlarmEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CHARGE_ALARM_ENABLED, false)

    fun setChargeAlarmEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_CHARGE_ALARM_ENABLED, enabled).apply()

    fun getChargeAlarmLevel(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_CHARGE_ALARM_LEVEL, 80)

    fun setChargeAlarmLevel(context: Context, level: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_CHARGE_ALARM_LEVEL, level.coerceIn(50, 100)).apply()

    fun isTempAlarmEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TEMP_ALARM_ENABLED, false)

    fun setTempAlarmEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_TEMP_ALARM_ENABLED, enabled).apply()

    fun getTempAlarmLimit(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_TEMP_ALARM_LIMIT, 42)

    fun setTempAlarmLimit(context: Context, limitC: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_TEMP_ALARM_LIMIT, limitC.coerceIn(35, 60)).apply()

    fun isLowBatteryAlarmEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_LOW_BATTERY_ALARM_ENABLED, false)

    fun setLowBatteryAlarmEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOW_BATTERY_ALARM_ENABLED, enabled).apply()

    fun getLowBatteryAlarmLevel(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LOW_BATTERY_ALARM_LEVEL, 20)

    fun setLowBatteryAlarmLevel(context: Context, level: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_LOW_BATTERY_ALARM_LEVEL, level.coerceIn(5, 30)).apply()

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

    fun getTargetResetBatteryLevel(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RESET_BATTERY_LEVEL, 90)
    }

    fun setTargetResetBatteryLevel(context: Context, levelPct: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RESET_BATTERY_LEVEL, levelPct.coerceIn(50, 100)).apply()
    }

    fun getLastUnplugFromFullTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(KEY_BASELINE_INITIALIZED, false)
        if (!initialized) {
            val ts = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_UNPLUG_FROM_FULL, ts)
                .putBoolean(KEY_BASELINE_INITIALIZED, true)
                .apply()
            return ts
        }
        return prefs.getLong(KEY_LAST_UNPLUG_FROM_FULL, System.currentTimeMillis())
    }

    fun updateLastUnplugFromFullTimestamp(context: Context, timestamp: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_UNPLUG_FROM_FULL, timestamp).apply()
        cachedHistory = null
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
