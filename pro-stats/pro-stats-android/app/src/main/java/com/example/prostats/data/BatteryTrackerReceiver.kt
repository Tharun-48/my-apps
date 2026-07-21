package com.example.prostats.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BatteryTrackerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BatteryTrackerReceiver", "Received intent action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleTracker(context)
        } else {
            try {
                BatteryTracker.recordDataPoint(context)
            } catch (e: Exception) {
                Log.e("BatteryTrackerReceiver", "Error recording battery point", e)
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
