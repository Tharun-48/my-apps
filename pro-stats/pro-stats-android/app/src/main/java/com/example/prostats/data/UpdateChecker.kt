package com.example.prostats.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.prostats.BuildConfig
import com.example.prostats.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isAvailable: Boolean = false,
    val remoteVersion: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_REPO_API = "https://api.github.com/repos/Tharun-48/my-apps/contents/pro-stats/releases"
    private const val GITHUB_RAW_BASE = "https://github.com/Tharun-48/my-apps/raw/main/pro-stats/releases"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    private const val CHANNEL_ID = "prostats_updates"
    private const val NOTIFICATION_ID = 3001

    /**
     * Check GitHub repository for newer compiled APK releases.
     * Compares remote APK version (e.g. ProStats-v2.3.apk) with local BuildConfig.VERSION_NAME (e.g. 2.2).
     */
    suspend fun checkForUpdates(context: Context, notifyUserIfAvailable: Boolean = false): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersionStr = BuildConfig.VERSION_NAME
        val currentVersionNum = parseVersionNumber(currentVersionStr)

        var highestVersionStr = currentVersionStr
        var highestVersionNum = currentVersionNum
        var downloadUrl = ""

        try {
            val url = URL(GITHUB_REPO_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ProStats-Android-App")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonResponse = reader.readText()
                val array = JSONArray(jsonResponse)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fileName = obj.optString("name", "")
                    if (fileName.endsWith(".apk") && fileName.contains("-v", ignoreCase = true)) {
                        val verStr = fileName.substringAfter("-v").substringBefore(".apk").trim()
                        val verNum = parseVersionNumber(verStr)
                        if (verNum > highestVersionNum) {
                            highestVersionNum = verNum
                            highestVersionStr = verStr
                            downloadUrl = obj.optString("download_url", "$GITHUB_RAW_BASE/$fileName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GitHub API update check failed: ${e.message}")
        }

        val updateAvailable = highestVersionNum > currentVersionNum

        if (updateAvailable && downloadUrl.isBlank()) {
            downloadUrl = "$GITHUB_RAW_BASE/ProStats-v$highestVersionStr.apk"
        }

        val info = UpdateInfo(
            isAvailable = updateAvailable,
            remoteVersion = highestVersionStr,
            currentVersion = currentVersionStr,
            downloadUrl = downloadUrl,
            releaseNotes = "New ProStats v$highestVersionStr release is ready with latest performance and design improvements."
        )

        if (updateAvailable && notifyUserIfAvailable) {
            postUpdateNotification(context, info)
        }

        return@withContext info
    }

    private fun parseVersionNumber(versionStr: String): Float {
        return try {
            val clean = versionStr.replace(Regex("[^0-9.]"), "")
            val parts = clean.split(".")
            if (parts.size >= 2) {
                "${parts[0]}.${parts[1]}".toFloatOrNull() ?: 0f
            } else {
                clean.toFloatOrNull() ?: 0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    private fun postUpdateNotification(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_VERSION, "")

        // Avoid spamming the same notification repeatedly
        if (lastNotified == info.remoteVersion) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new ProStats releases on GitHub"
            }
            nm.createNotificationChannel(channel)
        }

        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New ProStats Update Available (v${info.remoteVersion})")
            .setContentText("A new release has been pushed to GitHub. Tap to download & install.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("ProStats v${info.remoteVersion} is now available on GitHub. Tap below to download the latest APK."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.stat_sys_download, "Download APK", pendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
        prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, info.remoteVersion).apply()
    }
}
