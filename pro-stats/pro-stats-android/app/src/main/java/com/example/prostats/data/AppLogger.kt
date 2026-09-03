package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.example.prostats.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_FOLDER_NAME = "ProStats/Logs"

    private var isInitialized = false

    /**
     * Initializes the AppLogger and attaches an uncaught exception crash handler.
     * NOTE: Does NOT write any files to storage automatically on startup.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logCrash(context, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash to file", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "AppLogger initialized. Logging is strictly manual.")
    }

    /**
     * Retrieves the target log directory on phone storage.
     */
    fun getLogDirectory(context: Context): File {
        val primaryDir = File(Environment.getExternalStorageDirectory(), LOG_FOLDER_NAME)
        if (primaryDir.exists() || primaryDir.mkdirs()) {
            return primaryDir
        }

        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_FOLDER_NAME)
        if (docsDir.exists() || docsDir.mkdirs()) {
            return docsDir
        }

        val externalAppDir = context.getExternalFilesDir("Logs")
        if (externalAppDir != null && (externalAppDir.exists() || externalAppDir.mkdirs())) {
            return externalAppDir
        }

        val internalDir = File(context.filesDir, "Logs")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Checks if storage permissions are granted.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val write = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    /**
     * Launches settings to grant storage permission.
     */
    fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Standard Logcat outputs — does NOT write to disk automatically.
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun logWarning(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    /**
     * Generates a comprehensive manual diagnostic log file ONLY when explicitly requested by the user.
     * Returns the generated file path.
     */
    fun generateManualDiagnosticLog(context: Context): String {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val systemMonitor = SystemMonitor(context)
        val hardwareMonitor = HardwareMonitor(context)

        val ramInfo = systemMonitor.getRamInfo()
        val batteryInfo = systemMonitor.getBatteryInfo()
        val cpuUsage = systemMonitor.getSystemCpuUsage()
        val freqs = systemMonitor.getCpuCoreFrequencies()
        val thermal = systemMonitor.getThermalStatus()
        val sotMs = systemMonitor.getScreenOnTimeSinceLastChargeMs()
        val healthData = BatteryHealthEstimator.getHealthData(context)

        val logBuilder = StringBuilder().apply {
            append("================ PROSTATS DIAGNOSTIC LOG ================\n")
            append("Generated: ").append(timeStamp).append("\n")
            append("App Version: v").append(BuildConfig.VERSION_NAME).append(" (Code: ").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (").append(Build.BOARD).append(")\n")
            append("Android OS: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Kernel: ").append(System.getProperty("os.version") ?: "Unknown").append("\n")
            append("--------------------------------------------------------\n")
            append("SYSTEM TELEMETRY SNAPSHOT:\n")
            append("• CPU Load: ").append(cpuUsage.toInt()).append("%\n")
            append("• CPU Frequencies (MHz): ").append(freqs.joinToString(", ")).append("\n")
            val availGb = (ramInfo.totalGb - ramInfo.usedGb).coerceAtLeast(0f)
            val bTemp = systemMonitor.getBatteryTemperature()
            append("• RAM: ").append(String.format(Locale.US, "%.2f", ramInfo.usedGb)).append(" / ").append(String.format(Locale.US, "%.2f", ramInfo.totalGb)).append(" GB (Avail: ").append(String.format(Locale.US, "%.2f", availGb)).append(" GB)\n")
            append("• Battery Level: ").append(batteryInfo.level).append("% (").append(batteryInfo.status).append(")\n")
            append("• Battery Power: ").append(batteryInfo.currentMa).append(" mA / ").append(String.format(Locale.US, "%.2f", batteryInfo.watts)).append(" W\n")
            append("• Battery Temp: ").append(bTemp).append(" °C (Thermal: ").append(thermal).append(")\n")
            append("• Battery Health Score: ").append(healthData.healthScore).append("% (Est. Capacity: ").append(healthData.currentCapacityMah).append(" mAh, Design: ").append(healthData.designCapacityMah).append(" mAh)\n")
            append("• Screen-On Time: ").append(sotMs / 1000 / 60).append(" mins\n")
            append("--------------------------------------------------------\n")
            append("PERMISSIONS STATUS:\n")
            append("• Usage Access: ").append(systemMonitor.hasUsageStatsPermission()).append("\n")
            append("• Battery Optimizations Ignored: ").append(systemMonitor.isIgnoringBatteryOptimizations()).append("\n")
            append("• Overlay Permission: ").append(android.provider.Settings.canDrawOverlays(context)).append("\n")
            append("• Shizuku Access: ").append(systemMonitor.hasShizukuPermission()).append("\n")
            append("========================================================\n")
        }

        val logDir = getLogDirectory(context)
        val logFile = File(logDir, "prostats_manual_log_$fileTimestamp.txt")
        appendToFile(logFile, logBuilder.toString())

        Log.i(TAG, "Manual diagnostic log written: ${logFile.absolutePath}")
        return logFile.absolutePath
    }

    /**
     * Records a crash dump when an uncaught exception occurs.
     */
    private fun logCrash(context: Context, thread: Thread, throwable: Throwable) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val crashReport = StringBuilder().apply {
            append("\n================ CRASH REPORT ================\n")
            append("Timestamp: ").append(timeStamp).append("\n")
            @Suppress("DEPRECATION")
            append("Thread: ").append(thread.name).append(" (ID: ").append(thread.id).append(")\n")
            append("App Version: ProStats v").append(BuildConfig.VERSION_NAME).append("\n")
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("Android OS: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Exception: ").append(throwable.javaClass.name).append(": ").append(throwable.message).append("\n")
            append("StackTrace:\n").append(stackTrace)
            append("===============================================\n\n")
        }.toString()

        val logDir = getLogDirectory(context)
        val crashFile = File(logDir, "prostats_crash_log.txt")
        appendToFile(crashFile, crashReport)
    }

    private fun appendToFile(file: File, content: String) {
        try {
            file.parentFile?.mkdirs()
            FileWriter(file, true).use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file: ${file.absolutePath}", e)
        }
    }
}
