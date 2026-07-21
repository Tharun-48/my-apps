package com.example.prostats.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.prostats.data.HardwareMonitor
import com.example.prostats.data.SystemMonitor
import kotlinx.coroutines.*

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var systemMonitor: SystemMonitor
    private lateinit var hardwareMonitor: HardwareMonitor

    private var isTempOn = false
    private var isHzOn = false
    private var isCpuOn = false
    private var isRamOn = false

    private var txtTemp: TextView? = null
    private var txtHz: TextView? = null
    private var txtCpu: TextView? = null
    private var txtRam: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        systemMonitor = SystemMonitor(this)
        hardwareMonitor = HardwareMonitor(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            isTempOn = intent.getBooleanExtra(EXTRA_TEMP, isTempEnabled(this))
            isHzOn = intent.getBooleanExtra(EXTRA_HZ, isHzEnabled(this))
            isCpuOn = intent.getBooleanExtra(EXTRA_CPU, isCpuEnabled(this))
            isRamOn = intent.getBooleanExtra(EXTRA_RAM, isRamEnabled(this))
        } else {
            isTempOn = isTempEnabled(this)
            isHzOn = isHzEnabled(this)
            isCpuOn = isCpuEnabled(this)
            isRamOn = isRamEnabled(this)
        }

        if (!isTempOn && !isHzOn && !isCpuOn && !isRamOn) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Settings.canDrawOverlays(this)) {
            showOrUpdateOverlay()
        }

        return START_STICKY
    }

    private fun showOrUpdateOverlay() {
        if (overlayView == null) {
            createOverlayView()
        }
        updateViewVisibility()
        startMetricsLoop()
    }

    private fun createOverlayView() {
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(12), px(8), px(12), px(8))
            background = createCapsuleDrawable()
            elevation = px(6).toFloat()
        }

        txtTemp = createMetricTextView("#FB923C")
        txtHz = createMetricTextView("#60A5FA")
        txtCpu = createMetricTextView("#4ADE80")
        txtRam = createMetricTextView("#A78BFA")

        overlayView?.addView(txtTemp)
        overlayView?.addView(txtHz)
        overlayView?.addView(txtCpu)
        overlayView?.addView(txtRam)

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px(20)
            y = px(80)
        }

        setupDragTouchListener()

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDragTouchListener() {
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params?.x = initialX + (event.rawX - initialTouchX).toInt()
                        params?.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateViewVisibility() {
        txtTemp?.visibility = if (isTempOn) View.VISIBLE else View.GONE
        txtHz?.visibility = if (isHzOn) View.VISIBLE else View.GONE
        txtCpu?.visibility = if (isCpuOn) View.VISIBLE else View.GONE
        txtRam?.visibility = if (isRamOn) View.VISIBLE else View.GONE
    }

    private fun startMetricsLoop() {
        serviceScope.coroutineContext.cancelChildren()
        serviceScope.launch {
            while (isActive) {
                if (isTempOn) {
                    val batTemp = systemMonitor.getBatteryTemperature()
                    txtTemp?.text = "${String.format("%.1f", batTemp)}°C"
                }
                if (isHzOn) {
                    val displayInfo = hardwareMonitor.getDisplayInfo()
                    txtHz?.text = "${displayInfo.refreshRate.toInt()}Hz"
                }
                if (isCpuOn) {
                    val cpu = systemMonitor.getSystemCpuUsage()
                    txtCpu?.text = "CPU ${cpu.toInt()}%"
                }
                if (isRamOn) {
                    val ram = systemMonitor.getRamInfo()
                    val pct = if (ram.totalGb > 0) ((ram.usedGb / ram.totalGb) * 100).toInt() else 0
                    txtRam?.text = "RAM $pct%"
                }
                delay(1000)
            }
        }
    }

    private fun createMetricTextView(hexColor: String): TextView {
        return TextView(this).apply {
            setTextColor(Color.parseColor(hexColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(px(4), 0, px(4), 0)
        }
    }

    private fun createCapsuleDrawable(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = px(20).toFloat()
            setColor(Color.parseColor("#EE18181B")) // sleek dark translucent
            setStroke(px(1), Color.parseColor("#33FFFFFF"))
        }
    }

    private fun px(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "prostats_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ProStats Floating Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ProStats System Overlay Active")
            .setContentText("Displaying live floating HUD metrics.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val EXTRA_TEMP = "extra_temp"
        const val EXTRA_HZ = "extra_hz"
        const val EXTRA_CPU = "extra_cpu"
        const val EXTRA_RAM = "extra_ram"

        private const val PREFS_NAME = "overlay_prefs"

        fun isTempEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("overlay_temp", false)

        fun setTempEnabled(context: Context, enabled: Boolean) =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("overlay_temp", enabled).apply()

        fun isHzEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("overlay_hz", false)

        fun setHzEnabled(context: Context, enabled: Boolean) =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("overlay_hz", enabled).apply()

        fun isCpuEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("overlay_cpu", false)

        fun setCpuEnabled(context: Context, enabled: Boolean) =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("overlay_cpu", enabled).apply()

        fun isRamEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("overlay_ram", false)

        fun setRamEnabled(context: Context, enabled: Boolean) =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("overlay_ram", enabled).apply()
    }
}
