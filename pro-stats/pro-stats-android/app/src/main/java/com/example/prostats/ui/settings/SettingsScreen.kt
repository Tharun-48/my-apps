package com.example.prostats.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prostats.data.AppLogger
import com.example.prostats.data.BatteryTracker
import com.example.prostats.data.SystemMonitor
import com.example.prostats.service.OverlayService
import com.example.prostats.theme.ProStatsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    systemMonitor: SystemMonitor,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ProStatsColors.current

    var isShizukuRunning by remember { mutableStateOf(systemMonitor.isShizukuRunning()) }
    var hasShizukuPerm by remember { mutableStateOf(systemMonitor.hasShizukuPermission()) }
    var hasUsageAccess by remember { mutableStateOf(systemMonitor.hasUsageStatsPermission()) }
    var canDrawOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var overlayTemp by remember { mutableStateOf(OverlayService.isTempEnabled(context)) }
    var overlayMa by remember { mutableStateOf(OverlayService.isMaEnabled(context)) }
    var overlayHz by remember { mutableStateOf(OverlayService.isHzEnabled(context)) }
    var overlayCpu by remember { mutableStateOf(OverlayService.isCpuEnabled(context)) }
    var overlayRam by remember { mutableStateOf(OverlayService.isRamEnabled(context)) }

    var chargeAlarmEnabled by remember { mutableStateOf(BatteryTracker.isChargeAlarmEnabled(context)) }
    var chargeAlarmLevel by remember { mutableIntStateOf(BatteryTracker.getChargeAlarmLevel(context)) }
    var tempAlarmEnabled by remember { mutableStateOf(BatteryTracker.isTempAlarmEnabled(context)) }
    var tempAlarmLimit by remember { mutableIntStateOf(BatteryTracker.getTempAlarmLimit(context)) }
    var lowBatteryAlarmEnabled by remember { mutableStateOf(BatteryTracker.isLowBatteryAlarmEnabled(context)) }
    var lowBatteryAlarmLevel by remember { mutableIntStateOf(BatteryTracker.getLowBatteryAlarmLevel(context)) }

    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var currentTheme by remember { mutableStateOf(prefs.getString("app_theme", "Material You") ?: "Material You") }

    var latestVersion by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }

    // Auto-refresh Usage/Overlay status every 3s
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val usageAccess = systemMonitor.hasUsageStatsPermission()
                hasUsageAccess = usageAccess
            }
            canDrawOverlay = Settings.canDrawOverlays(context)
            delay(3000)
        }
    }

    // Check for updates
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.github.com/repos/Tharun-48/my-apps/contents/pro-stats/releases")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (connection.responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    val jsonArray = org.json.JSONArray(response)
                    var maxVersionStr = com.example.prostats.BuildConfig.VERSION_NAME
                    var maxVersionNum = com.example.prostats.BuildConfig.VERSION_NAME.toFloatOrNull() ?: 2.3f
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.getString("name")
                        if (name.endsWith(".apk") && name.contains("-v")) {
                            val vStr = name.substringAfter("-v").substringBefore(".apk")
                            val vNum = vStr.toFloatOrNull()
                            if (vNum != null && vNum > maxVersionNum) {
                                maxVersionNum = vNum
                                maxVersionStr = vStr
                            }
                        }
                    }
                    if (maxVersionNum > (com.example.prostats.BuildConfig.VERSION_NAME.toFloatOrNull() ?: 2.3f)) {
                        latestVersion = maxVersionStr
                        updateAvailable = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateCheck", "Failed to check for updates", e)
            }
        }
    }

    DisposableEffect(Unit) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            isShizukuRunning = true
            hasShizukuPerm = systemMonitor.hasShizukuPermission()
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            isShizukuRunning = false
            hasShizukuPerm = false
        }
        val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 0) {
                hasShizukuPerm = (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
    }

    fun updateOverlayService() {
        val intent = Intent(context, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TEMP, overlayTemp)
            putExtra(OverlayService.EXTRA_MA, overlayMa)
            putExtra(OverlayService.EXTRA_HZ, overlayHz)
            putExtra(OverlayService.EXTRA_CPU, overlayCpu)
            putExtra(OverlayService.EXTRA_RAM, overlayRam)
        }
        if (overlayTemp || overlayMa || overlayHz || overlayCpu || overlayRam) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Overlays",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                            .background(colors.elevatedSurface, CircleShape)
                            .border(1.dp, colors.borderColorSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Shizuku Service Integration Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colors.accentPurple.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, colors.accentPurple.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = colors.accentPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Shizuku Wireless ADB", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        val statusText = when {
                            isShizukuRunning && hasShizukuPerm -> "CONNECTED"
                            isShizukuRunning -> "NO PERMISSION"
                            else -> "NOT RUNNING"
                        }
                        val statusColor = when {
                            isShizukuRunning && hasShizukuPerm -> colors.accentGreen
                            isShizukuRunning -> colors.accentYellow
                            else -> colors.accentOrange
                        }

                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isShizukuRunning && hasShizukuPerm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentGreen.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                .border(1.dp, colors.accentGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("Pro Mode Active", color = colors.accentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Real-time CPU & RAM per process\n• Force stop & freeze background apps\n• Battery stats via dumpsys\n• Wakelock analysis via dumpsys power",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else if (isShizukuRunning && !hasShizukuPerm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentYellow.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .border(1.dp, colors.accentYellow.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("Shizuku Running — Permission Needed", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Shizuku service is running. Tap below to grant permission to ProStats.", color = colors.textPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { systemMonitor.requestShizukuPermission() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentYellow, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Shizuku Permission", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentOrange.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .border(1.dp, colors.accentOrange.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("Shizuku Service Not Running", color = colors.accentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Open Shizuku app to start the service via Wireless Debugging or ADB to unlock Pro Mode task management.", color = colors.textPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val pm = context.packageManager
                                            val launchIntent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                ?: pm.getLaunchIntentForPackage("moe.shizuku.manager")
                                                ?: pm.getLaunchIntentForPackage("rikka.shizuku.manager")
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                android.widget.Toast.makeText(context, "Shizuku app not found on device.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange, contentColor = Color.Black),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open App", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                                            context.startActivity(webIntent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface, contentColor = colors.textPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Download App", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Usage Access status
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.borderColorSubtle)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!hasUsageAccess) systemMonitor.launchUsageAccessSettings()
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage Stats Access", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = if (hasUsageAccess) "Granted — enables SOT & foreground tracking"
                                else "Not granted — tap to enable for stats",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        val accessColor = if (hasUsageAccess) colors.accentGreen else colors.accentOrange
                        Box(
                            modifier = Modifier
                                .background(accessColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .border(1.dp, accessColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasUsageAccess) "GRANTED" else "GRANT",
                                color = accessColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // App Theme Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.accentBlue.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, colors.accentBlue.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("App Visual Theme", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val themes = listOf(
                        Triple("Material You", "Dynamic colors from system wallpaper", colors.accentGreen),
                        Triple("Dark", "Sleek slate dark theme with high contrast", Color(0xFF86EFAC)),
                        Triple("Pure Black (AMOLED)", "Deep OLED black for power savings", Color.White),
                        Triple("Light", "Clean bright background with sharp typography", colors.accentBlue)
                    )

                    themes.forEachIndexed { index, (theme, desc, dotColor) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    currentTheme = theme
                                    prefs.edit().putString("app_theme", theme).apply()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(dotColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(theme, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(desc, color = colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    currentTheme = theme
                                    prefs.edit().putString("app_theme", theme).apply()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accentGreen, unselectedColor = colors.textTertiary)
                            )
                        }
                        if (index < themes.size - 1) {
                            HorizontalDivider(color = colors.borderColorSubtle)
                        }
                    }
                }
            }

            // Battery Health Protection Alarms Card (Battery Guru Feature)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.accentOrange.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, colors.accentOrange.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.accentOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Battery Protection Alarms", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Set smart charging and temperature alert thresholds to extend Li-ion battery health.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Charge Limit Alarm
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Charge Limit Stop Alarm", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Notify when charging reaches target level",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = chargeAlarmEnabled,
                            onCheckedChange = {
                                chargeAlarmEnabled = it
                                BatteryTracker.setChargeAlarmEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentGreen,
                                checkedTrackColor = colors.accentGreen.copy(alpha = 0.3f),
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.elevatedSurface
                            )
                        )
                    }

                    if (chargeAlarmEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(80, 85, 90, 100).forEach { level ->
                                val selected = chargeAlarmLevel == level
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selected) colors.accentGreen.copy(alpha = 0.2f) else colors.elevatedSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) colors.accentGreen else colors.borderColorSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            chargeAlarmLevel = level
                                            BatteryTracker.setChargeAlarmLevel(context, level)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$level%",
                                        color = if (selected) colors.accentGreen else colors.textPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.borderColorSubtle)
                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. High Temperature Alarm
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Temperature Warning", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Alert when battery exceeds thermal threshold",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = tempAlarmEnabled,
                            onCheckedChange = {
                                tempAlarmEnabled = it
                                BatteryTracker.setTempAlarmEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentOrange,
                                checkedTrackColor = colors.accentOrange.copy(alpha = 0.3f),
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.elevatedSurface
                            )
                        )
                    }

                    if (tempAlarmEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(40, 42, 45).forEach { temp ->
                                val selected = tempAlarmLimit == temp
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selected) colors.accentOrange.copy(alpha = 0.2f) else colors.elevatedSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) colors.accentOrange else colors.borderColorSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            tempAlarmLimit = temp
                                            BatteryTracker.setTempAlarmLimit(context, temp)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$temp°C",
                                        color = if (selected) colors.accentOrange else colors.textPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.borderColorSubtle)
                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Low Battery Warning
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low Battery Warning", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Remind to plug in before deep discharge",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = lowBatteryAlarmEnabled,
                            onCheckedChange = {
                                lowBatteryAlarmEnabled = it
                                BatteryTracker.setLowBatteryAlarmEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentYellow,
                                checkedTrackColor = colors.accentYellow.copy(alpha = 0.3f),
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.elevatedSurface
                            )
                        )
                    }

                    if (lowBatteryAlarmEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 20).forEach { lvl ->
                                val selected = lowBatteryAlarmLevel == lvl
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selected) colors.accentYellow.copy(alpha = 0.2f) else colors.elevatedSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) colors.accentYellow else colors.borderColorSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            lowBatteryAlarmLevel = lvl
                                            BatteryTracker.setLowBatteryAlarmLevel(context, lvl)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$lvl%",
                                        color = if (selected) colors.accentYellow else colors.textPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Overlays Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.accentGreen.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, colors.accentGreen.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("System Floating HUD Overlays", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Display real-time diagnostic indicators over other applications.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!canDrawOverlay) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentOrange.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .border(1.dp, colors.accentOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Display Over Apps Permission Required", color = colors.accentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Grant overlay permission so ProStats can draw the HUD floating widget.", color = colors.textPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Grant Overlay Permission", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Toggles
                    OverlayToggleRow(
                        title = "Battery Temperature (°C)",
                        subtitle = "Live battery thermal readings",
                        checked = overlayTemp,
                        onCheckedChange = {
                            overlayTemp = it
                            OverlayService.setTempEnabled(context, it)
                            updateOverlayService()
                        }
                    )

                    HorizontalDivider(color = colors.borderColorSubtle, modifier = Modifier.padding(vertical = 6.dp))

                    OverlayToggleRow(
                        title = "Battery Current (mA)",
                        subtitle = "Realtime discharge & charge rate",
                        checked = overlayMa,
                        onCheckedChange = {
                            overlayMa = it
                            OverlayService.setMaEnabled(context, it)
                            updateOverlayService()
                        }
                    )

                    HorizontalDivider(color = colors.borderColorSubtle, modifier = Modifier.padding(vertical = 6.dp))

                    OverlayToggleRow(
                        title = "Refresh Rate (Hz)",
                        subtitle = "Screen display refresh rate",
                        checked = overlayHz,
                        onCheckedChange = {
                            overlayHz = it
                            OverlayService.setHzEnabled(context, it)
                            updateOverlayService()
                        }
                    )

                    HorizontalDivider(color = colors.borderColorSubtle, modifier = Modifier.padding(vertical = 6.dp))

                    OverlayToggleRow(
                        title = "CPU Usage (%)",
                        subtitle = "Realtime processor load",
                        checked = overlayCpu,
                        onCheckedChange = {
                            overlayCpu = it
                            OverlayService.setCpuEnabled(context, it)
                            updateOverlayService()
                        }
                    )

                    HorizontalDivider(color = colors.borderColorSubtle, modifier = Modifier.padding(vertical = 6.dp))

                    OverlayToggleRow(
                        title = "RAM Usage (%)",
                        subtitle = "System memory allocation load",
                        checked = overlayRam,
                        onCheckedChange = {
                            overlayRam = it
                            OverlayService.setRamEnabled(context, it)
                            updateOverlayService()
                        }
                    )
                }
            }

            // Error Logging & Storage Card
            var hasStoragePermission by remember { mutableStateOf(AppLogger.hasStoragePermission(context)) }
            var testLogStatus by remember { mutableStateOf<String?>(null) }
            val logDir = AppLogger.getLogDirectory(context)

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.accentBlue.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, colors.accentBlue.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = colors.accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Log & Error Diagnostics", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Crash dumps and diagnostic traces are logged outside the Android sandbox directory for easy inspection.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.elevatedSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Storage Location", color = colors.accentBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = logDir.absolutePath,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!hasStoragePermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentOrange.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .border(1.dp, colors.accentOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Storage Access Recommended", color = colors.accentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Allow storage access to write log files directly to internal storage.", color = colors.textPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { AppLogger.requestStoragePermission(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Grant Storage Access", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            val path = AppLogger.generateManualDiagnosticLog(context)
                            testLogStatus = "Manual diagnostic log saved to ${java.io.File(path).name}"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface, contentColor = colors.textPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate Diagnostic Log (Manual)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    if (testLogStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(testLogStatus!!, color = colors.accentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // About & Updates
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.accentYellow.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, colors.accentYellow.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.accentYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("About & Release Status", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Installed Version", color = colors.textSecondary, fontSize = 13.sp)
                        Box(
                            modifier = Modifier
                                .background(colors.elevatedSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("v${com.example.prostats.BuildConfig.VERSION_NAME}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (updateAvailable && latestVersion != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(1.dp, colors.accentGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("New Update Available: v$latestVersion!", color = colors.accentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tharun-48/my-apps/raw/main/pro-stats/releases/ProStats-v$latestVersion.apk"))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Download Update Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tharun-48/my-apps/tree/main/pro-stats/releases"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface, contentColor = colors.textPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Check Releases on GitHub", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun OverlayToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ProStatsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = colors.accentGreen,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.elevatedSurface
            )
        )
    }
}
