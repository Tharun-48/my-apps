package com.example.prostats.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prostats.data.SystemMonitor
import com.example.prostats.service.OverlayService
import com.example.prostats.theme.ProStatsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    systemMonitor: SystemMonitor,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProStatsColors.current
    val context = LocalContext.current
    var isShizukuRunning by remember { mutableStateOf(systemMonitor.isShizukuRunning()) }
    var hasShizukuPerm by remember { mutableStateOf(systemMonitor.hasShizukuPermission()) }
    var hasUsageAccess by remember { mutableStateOf(systemMonitor.hasUsageStatsPermission()) }
    var canDrawOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var overlayTemp by remember { mutableStateOf(OverlayService.isTempEnabled(context)) }
    var overlayHz by remember { mutableStateOf(OverlayService.isHzEnabled(context)) }
    var overlayCpu by remember { mutableStateOf(OverlayService.isCpuEnabled(context)) }
    var overlayRam by remember { mutableStateOf(OverlayService.isRamEnabled(context)) }

    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    var currentTheme by remember { mutableStateOf(prefs.getString("app_theme", "Material You") ?: "Material You") }

    // Auto-refresh Shizuku status every 3s
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val shizRunning = systemMonitor.isShizukuRunning()
                val shizPerm = systemMonitor.hasShizukuPermission()
                val usageAccess = systemMonitor.hasUsageStatsPermission()
                
                isShizukuRunning = shizRunning
                hasShizukuPerm = shizPerm
                hasUsageAccess = usageAccess
            }
            canDrawOverlay = Settings.canDrawOverlays(context)
            delay(3000)
        }
    }

    fun updateOverlayService() {
        val intent = Intent(context, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TEMP, overlayTemp)
            putExtra(OverlayService.EXTRA_HZ, overlayHz)
            putExtra(OverlayService.EXTRA_CPU, overlayCpu)
            putExtra(OverlayService.EXTRA_RAM, overlayRam)
        }
        if (overlayTemp || overlayHz || overlayCpu || overlayRam) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Overlays", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = colors.accentPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shizuku Status", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Shizuku benefits explanation & Action buttons
                    if (isShizukuRunning && hasShizukuPerm) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.accentGreen.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pro Mode Active", color = colors.accentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Real-time CPU & RAM per process via 'top'\n• Force stop & freeze apps via ADB\n• Enhanced battery stats via 'dumpsys'\n• Wakelock monitoring via 'dumpsys power'\n• More accurate battery drain estimation",
                                    color = colors.textPrimary, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    } else if (isShizukuRunning && !hasShizukuPerm) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.accentYellow.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Shizuku Running — Permission Needed", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Shizuku service is running. Tap below to grant permission to ProStats.", color = colors.textPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { systemMonitor.requestShizukuPermission() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentYellow, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Shizuku Permission", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.accentOrange.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Shizuku Service Not Running", color = colors.accentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Open Shizuku app to start the service via Wireless Debugging, ADB, or Root.", color = colors.textPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val pm = context.packageManager
                                            val launchIntent = pm.getLaunchIntentForPackage("moe.shizuku.manager")
                                                ?: pm.getLaunchIntentForPackage("rikka.shizuku.manager")
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                                                context.startActivity(webIntent)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open Shizuku App", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Usage Access status
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = colors.borderColor)
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
                            Text("Usage Access", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = if (hasUsageAccess) "Granted — improves SOT and battery accuracy" 
                                       else "Not granted — tap to enable for better accuracy",
                                color = colors.textSecondary, fontSize = 11.sp
                            )
                            if (isShizukuRunning && hasShizukuPerm && hasUsageAccess) {
                                Text(
                                    text = "✓ Combined with Shizuku for maximum accuracy",
                                    color = colors.accentGreen, fontSize = 11.sp
                                )
                            }
                        }
                        val accessColor = if (hasUsageAccess) colors.accentGreen else colors.accentOrange
                        Text(
                            text = if (hasUsageAccess) "GRANTED" else "GRANT",
                            color = accessColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(accessColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // App Theme Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.accentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Theme", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val themes = listOf("Material You", "Light", "Dark", "Pure Black (AMOLED)")
                    themes.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTheme = theme
                                    prefs.edit().putString("app_theme", theme).apply()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(theme, color = colors.textPrimary, fontSize = 14.sp)
                                Text(
                                    text = when (theme) {
                                        "Material You" -> "Dynamic colors from your wallpaper"
                                        "Light" -> "Bright backgrounds with dark text"
                                        "Dark" -> "Dark backgrounds with light text"
                                        "Pure Black (AMOLED)" -> "True black for OLED power savings"
                                        else -> ""
                                    },
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    currentTheme = theme
                                    prefs.edit().putString("app_theme", theme).apply()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accentBlue, unselectedColor = colors.textSecondary)
                            )
                        }
                        if (theme != themes.last()) {
                            Divider(color = colors.borderColor)
                        }
                    }
                }
            }

            // Floating Overlays Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = null, tint = colors.accentGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Floating HUD Overlays", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Display live metrics overlay on top of any application on your screen.",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!canDrawOverlay) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.accentOrange.copy(alpha = 0.13f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
                        Spacer(modifier = Modifier.height(16.dp))
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

                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 8.dp))

                    OverlayToggleRow(
                        title = "Refresh Rate (Hz)",
                        subtitle = "Screen display FPS / Hz rate",
                        checked = overlayHz,
                        onCheckedChange = {
                            overlayHz = it
                            OverlayService.setHzEnabled(context, it)
                            updateOverlayService()
                        }
                    )

                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 8.dp))

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

                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 8.dp))

                    OverlayToggleRow(
                        title = "RAM Usage (%)",
                        subtitle = "Memory load allocation",
                        checked = overlayRam,
                        onCheckedChange = {
                            overlayRam = it
                            OverlayService.setRamEnabled(context, it)
                            updateOverlayService()
                        }
                    )
                }
            }
            
            // About & Updates
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderColor.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = colors.accentYellow)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About & Updates", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", color = colors.textSecondary, fontSize = 14.sp)
                        Text("v2.0", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tharun-48/my-apps/tree/main/pro-stats/releases"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface, contentColor = colors.textPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Check for Updates on GitHub")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
