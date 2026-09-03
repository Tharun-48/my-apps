package com.example.prostats.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.prostats.MainActivity
import com.example.prostats.R
import kotlinx.coroutines.launch

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
                try {
                    BatteryTracker.onPowerDisconnected(context)
                    BatteryTracker.recordDataPoint(context)
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error on power disconnected", e)
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                try {
                    BatteryTracker.onPowerConnected(context)
                    BatteryTracker.recordDataPoint(context)
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error on power connected", e)
                }
            }

            ALARM_ACTION -> {
                try {
                    BatteryTracker.recordDataPoint(context)
                    BatteryHealthEstimator.trackCycleData(context)
                    checkBatteryProtectionAlarms(context)
                    
                    val pendingResult = goAsync()
                    // Periodically check GitHub for new releases
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            UpdateChecker.checkForUpdates(context, notifyUserIfAvailable = true)
                        } catch (e: Exception) {
                            Log.d("BatteryTrackerReceiver", "Update check failed: ${e.message}")
                        } finally {
                            try {
                                pendingResult.finish()
                            } catch (e: Exception) {
                                // Ignore finish errors if already finalized
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BatteryTrackerReceiver", "Error during alarm-triggered recording", e)
                }
            }

            else -> {
                Log.d("BatteryTrackerReceiver", "Unknown action: $action — ignoring")
            }
        }
    }

    private fun checkBatteryProtectionAlarms(context: Context) {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter) ?: return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = tempRaw / 10f

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel(nm)

            // 1. Charge Limit Alarm (e.g. 80% / 90% reached while on charger)
            if (isCharging && BatteryTracker.isChargeAlarmEnabled(context)) {
                val limit = BatteryTracker.getChargeAlarmLevel(context)
                if (pct >= limit) {
                    showNotification(
                        context,
                        nm,
                        ID_CHARGE_ALARM,
                        "Battery Charge Limit Reached ($pct%)",
                        "Unplug charger to protect battery health and prevent overcharging wear."
                    )
                }
            }

            // 2. High Battery Temperature Alarm (e.g. > 42°C)
            if (BatteryTracker.isTempAlarmEnabled(context)) {
                val tempLimit = BatteryTracker.getTempAlarmLimit(context)
                if (tempC >= tempLimit) {
                    showNotification(
                        context,
                        nm,
                        ID_TEMP_ALARM,
                        "High Battery Temperature Alert (${tempC.toInt()}°C)",
                        "Device battery is running hot. Allow the device to cool down."
                    )
                }
            }

            // 3. Low Battery Warning
            if (!isCharging && BatteryTracker.isLowBatteryAlarmEnabled(context)) {
                val lowLimit = BatteryTracker.getLowBatteryAlarmLevel(context)
                if (pct in 1..lowLimit) {
                    showNotification(
                        context,
                        nm,
                        ID_LOW_BATTERY_ALARM,
                        "Low Battery Alert ($pct%)",
                        "Battery level dropped to $pct%. Connect to charger soon."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("BatteryTrackerReceiver", "Error checking battery protection alarms", e)
        }
    }

    private fun createNotificationChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Protection & Health Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for charge limits, battery temperature warnings, and low battery"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, nm: NotificationManager, id: Int, title: String, message: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        nm.notify(id, builder.build())
    }

    companion object {
        const val ALARM_ACTION = "com.example.prostats.BATTERY_TRACKER_ALARM"
        private const val CHANNEL_ID = "prostats_battery_alarms"
        private const val ID_CHARGE_ALARM = 2001
        private const val ID_TEMP_ALARM = 2002
        private const val ID_LOW_BATTERY_ALARM = 2003

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

            // 15-30 minutes interval
            val interval = 15 * 60 * 1000L
            val triggerAt = System.currentTimeMillis() + interval

            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    interval,
                    pendingIntent
                )
                Log.d("BatteryTrackerReceiver", "Battery tracker alarm scheduled (15-min interval)")
            } catch (e: Exception) {
                Log.e("BatteryTrackerReceiver", "Failed to schedule battery tracker alarm", e)
            }
        }
    }
}
