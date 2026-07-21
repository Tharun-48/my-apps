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
            Intent.ACTION_BOOT_COMPLETED -> scheduleTracker(context)
            Intent.ACTION_POWER_DISCONNECTED -> {
                // Check if battery was at full charge when unplugged
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryIntent = context.registerReceiver(null, filter)
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                if (pct >= 95) {
                    BatteryTracker.updateLastUnplugFromFullTimestamp(context)
                    Log.d("BatteryTrackerReceiver", "Charger unplugged from full charge ($pct%)")
                }
            }
            else -> {
                try {
                    BatteryTracker.recordDataPoint(context)
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error recording battery point", e)
                }
            }
        }
    }

    companion object {
        fun scheduleTracker(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BatteryTrackerReceiver::class.java)
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
                Log.d("BatteryTrackerReceiver", "Successfully scheduled battery tracker alarm.")
            } catch (e: Exception) {
                Log.e("BatteryTrackerReceiver", "Failed to schedule battery tracker alarm", e)
            }
        }
    }
}
