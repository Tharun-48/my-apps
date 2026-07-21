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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    systemMonitor: SystemMonitor,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isShizukuRunning by remember { mutableStateOf(systemMonitor.isShizukuRunning()) }
    var hasShizukuPerm by remember { mutableStateOf(systemMonitor.hasShizukuPermission()) }
    var canDrawOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var overlayTemp by remember { mutableStateOf(OverlayService.isTempEnabled(context)) }
    var overlayHz by remember { mutableStateOf(OverlayService.isHzEnabled(context)) }
    var overlayCpu by remember { mutableStateOf(OverlayService.isCpuEnabled(context)) }
    var overlayRam by remember { mutableStateOf(OverlayService.isRamEnabled(context)) }

    LaunchedEffect(Unit) {
        isShizukuRunning = systemMonitor.isShizukuRunning()
        hasShizukuPerm = systemMonitor.hasShizukuPermission()
        canDrawOverlay = Settings.canDrawOverlays(context)
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
                title = { Text("Settings & Overlays", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0C))
            )
        },
        containerColor = Color(0xFF0A0A0C),
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0A0A0C))
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Shizuku Service Integration Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFA78BFA))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shizuku Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        val statusText = when {
                            isShizukuRunning && hasShizukuPerm -> "CONNECTED"
                            isShizukuRunning -> "NO PERMISSION"
                            else -> "NOT RUNNING"
                        }
                        val statusColor = when {
                            isShizukuRunning && hasShizukuPerm -> Color(0xFF4ADE80)
                            isShizukuRunning -> Color(0xFFFBBF24)
                            else -> Color(0xFFFB923C)
                        }

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Shizuku provides high-precision top CPU load metrics without root.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (isShizukuRunning) {
                                systemMonitor.requestShizukuPermission()
                            } else {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                                        context.startActivity(webIntent)
                                    }
                                } catch (e: Exception) {
                                    systemMonitor.requestShizukuPermission()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isShizukuRunning) "Request Shizuku Permission" else "Open / Get Shizuku App")
                    }
                }
            }

            // Floating Overlays Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF4ADE80))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Floating HUD Overlays", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Display live metrics overlay on top of any application on your screen.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!canDrawOverlay) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x22FB923C)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Display Over Apps Permission Required", color = Color(0xFFFB923C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Grant overlay permission so ProStats can draw the HUD floating widget.", color = Color.White, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB923C), contentColor = Color.Black),
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

                    Divider(color = Color(0x11FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

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

                    Divider(color = Color(0x11FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

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

                    Divider(color = Color(0x11FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF4ADE80),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF2C2C2E)
            )
        )
    }
}
