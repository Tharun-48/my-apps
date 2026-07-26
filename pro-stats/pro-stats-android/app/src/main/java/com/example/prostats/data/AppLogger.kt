package com.example.prostats.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
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
     * Initializes the AppLogger and attaches a global uncaught exception handler.
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

        logInfo(context, TAG, "AppLogger initialized successfully. Log directory target: ${getLogDirectory(context).absolutePath}")
    }

    /**
     * Retrieves the target log directory on phone storage, ensuring it is outside the Android system folder.
     */
    fun getLogDirectory(context: Context): File {
        // Priority 1: Direct root storage /sdcard/ProStats/Logs
        val primaryDir = File(Environment.getExternalStorageDirectory(), LOG_FOLDER_NAME)
        if (primaryDir.exists() || primaryDir.mkdirs()) {
            return primaryDir
        }

        // Priority 2: /sdcard/Documents/ProStats/Logs
        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_FOLDER_NAME)
        if (docsDir.exists() || docsDir.mkdirs()) {
            return docsDir
        }

        // Priority 3: External App Files directory as fallback
        val externalAppDir = context.getExternalFilesDir("Logs")
        if (externalAppDir != null && (externalAppDir.exists() || externalAppDir.mkdirs())) {
            return externalAppDir
        }

        // Priority 4: Internal private directory as emergency fallback
        val internalDir = File(context.filesDir, "Logs")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Checks if all files management access or storage permissions are granted.
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
     * Launches settings activity to grant storage permission if required.
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
     * Logs an error message and optional exception to log files on storage.
     */
    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeLogEntry(context, "ERROR", tag, message, throwable)
    }

    /**
     * Logs an informational message to log files.
     */
    fun logInfo(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        writeLogEntry(context, "INFO", tag, message, null)
    }

    /**
     * Logs a warning message to log files.
     */
    fun logWarning(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        writeLogEntry(context, "WARN", tag, message, throwable)
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
            append("App Version: ProStats v2.1\n")
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("Android OS: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Exception: ").append(throwable.javaClass.name).append(": ").append(throwable.message).append("\n")
            append("StackTrace:\n").append(stackTrace)
            append("===============================================\n\n")
        }.toString()

        val logDir = getLogDirectory(context)
        val crashFile = File(logDir, "prostats_crash_log.txt")
        appendToFile(crashFile, crashReport)

        // Also append to the daily log file
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val dailyFile = File(logDir, "prostats_log_$dateStr.txt")
        appendToFile(dailyFile, crashReport)
    }

    private fun writeLogEntry(context: Context, level: String, tag: String, message: String, throwable: Throwable?) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        val logBuilder = StringBuilder()
        logBuilder.append("[").append(timeStamp).append("] ")
            .append("[").append(level).append("] ")
            .append("[").append(tag).append("]: ")
            .append(message).append("\n")

        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            logBuilder.append(sw.toString()).append("\n")
        }

        val entry = logBuilder.toString()
        val logDir = getLogDirectory(context)

        // Append to main error log if ERROR level
        if (level == "ERROR" || level == "CRASH") {
            val errorFile = File(logDir, "prostats_error_log.txt")
            appendToFile(errorFile, entry)
        }

        // Append to daily combined log file
        val dailyFile = File(logDir, "prostats_log_$dateStr.txt")
        appendToFile(dailyFile, entry)
    }

    private fun appendToFile(file: File, content: String) {
        try {
            file.parentFile?.mkdirs()
            FileWriter(file, true).use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error appending to file: ${file.absolutePath}", e)
        }
    }
}
