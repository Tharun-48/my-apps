package com.example.prostats.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

class BatteryTrackerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BatteryTrackerReceiver", "Received intent action: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                scheduleTracker(context)
                Log.d("BatteryTrackerReceiver", "Boot completed — tracker scheduled")
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                // Check if battery is at or above the configured reset level when unplugged
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryIntent = context.registerReceiver(null, filter)
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                val targetResetLevel = BatteryTracker.getTargetResetBatteryLevel(context)
                if (pct >= targetResetLevel) {
                    BatteryTracker.updateLastUnplugFromFullTimestamp(context)
                    Log.d("BatteryTrackerReceiver", "Charger unplugged at $pct% (>= $targetResetLevel%) — SOT baseline reset")
                } else {
                    Log.d("BatteryTrackerReceiver", "Charger unplugged at $pct% (< $targetResetLevel%) — no SOT reset")
                }
                // Always record a data point on unplug
                try {
                    BatteryTracker.recordDataPoint(context)
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error recording battery point on unplug", e)
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                // Charger connected — log it and record a data point
                Log.d("BatteryTrackerReceiver", "Charger connected — logging event")
                try {
                    BatteryTracker.recordDataPoint(context)
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error recording battery point on connect", e)
                }
            }

            ALARM_ACTION -> {
                // Alarm-triggered periodic recording (every 30 minutes)
                try {
                    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryIntent = context.registerReceiver(null, filter)
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                    
                    val targetResetLevel = BatteryTracker.getTargetResetBatteryLevel(context)
                    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    
                    // Auto-reset cycle multiple times while device stays at or above target level on charger
                    if (isCharging && pct >= targetResetLevel) {
                        BatteryTracker.updateLastUnplugFromFullTimestamp(context)
                        Log.d("BatteryTrackerReceiver", "Alarm: Battery at $pct% (>= $targetResetLevel%) while charging — auto-resetting SOT baseline")
                    }

                    BatteryTracker.recordDataPoint(context)
                    BatteryHealthEstimator.trackCycleData(context)
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error during alarm-triggered recording", e)
                }
            }

            else -> {
                Log.d("BatteryTrackerReceiver", "Unknown action: $action — ignoring")
            }
        }
    }

    companion object {
        const val ALARM_ACTION = "com.example.prostats.BATTERY_TRACKER_ALARM"

        fun scheduleTracker(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BatteryTrackerReceiver::class.java).apply {
                action = ALARM_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 30 minutes interval
            val interval = 30 * 60 * 1000L
            val triggerAt = System.currentTimeMillis() + interval

            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    interval,
                    pendingIntent
                )
                Log.d("BatteryTrackerReceiver", "Battery tracker alarm scheduled (30-min interval)")
            } catch (e: Exception) {
                Log.e("BatteryTrackerReceiver", "Failed to schedule battery tracker alarm", e)
            }
        }
    }
}
