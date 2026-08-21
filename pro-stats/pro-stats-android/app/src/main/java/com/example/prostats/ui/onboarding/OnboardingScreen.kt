package com.example.prostats.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.prostats.data.SystemMonitor
import com.example.prostats.theme.ProStatsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    systemMonitor: SystemMonitor,
    onStartMonitoring: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = ProStatsColors.current

    var hasBatteryOptimizations by remember { mutableStateOf(false) }
    var hasUsageAccess by remember { mutableStateOf(false) }
    var isShizukuRunning by remember { mutableStateOf(false) }
    var hasShizukuPermission by remember { mutableStateOf(false) }

    // Recheck permissions whenever the app returns to the foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBatteryOptimizations = systemMonitor.isIgnoringBatteryOptimizations()
                hasUsageAccess = systemMonitor.hasUsageStatsPermission()
                isShizukuRunning = systemMonitor.isShizukuRunning()
                hasShizukuPermission = systemMonitor.hasShizukuPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isReadyToStart = hasBatteryOptimizations && hasUsageAccess

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Subtle ambient background glow (dark mode)
        if (colors.isDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.accentGreen.copy(alpha = 0.12f), Color.Transparent),
                            radius = 800f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Step Indicator Badge
            Box(
                modifier = Modifier
                    .background(colors.elevatedSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "STEP 2 OF 3: PERMISSIONS",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Configure ProStats Access",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "These permissions enable real-time process monitoring, Screen-on Time tracking, and battery health analytics.",
                color = colors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Permission Card 1: Battery stats
                PermissionCard(
                    title = "Battery Optimization Whitelist",
                    subtitle = "Required for continuous background logging",
                    description = "Prevents Android from killing the background tracker for Screen-On Time (SOT) and temperature logs.",
                    isGranted = hasBatteryOptimizations,
                    onClick = {
                        if (!hasBatteryOptimizations) {
                            systemMonitor.launchBatterySettings()
                        }
                    }
                )

                // Permission Card 2: Usage Stats
                PermissionCard(
                    title = "Usage Access Permission",
                    subtitle = "Required for per-app battery & process stats",
                    description = "Allows ProStats to monitor running processes, foreground app durations, and system load.",
                    isGranted = hasUsageAccess,
                    onClick = {
                        if (!hasUsageAccess) {
                            systemMonitor.launchUsageAccessSettings()
                        }
                    }
                )

                // Permission Card 3: Shizuku
                PermissionCard(
                    title = "Shizuku Wireless ADB Authorization",
                    subtitle = "Optional for Pro Mode features",
                    description = if (isShizukuRunning) {
                        "Tap to grant wireless ADB permissions for PC-grade task monitoring and freeze/force-stop."
                    } else {
                        "Shizuku service is not running. Tap to launch Shizuku manager or install."
                    },
                    isGranted = hasShizukuPermission,
                    isOptional = true,
                    onClick = {
                        if (isShizukuRunning) {
                            if (!hasShizukuPermission) {
                                systemMonitor.requestShizukuPermission()
                            }
                        } else {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                ?: context.packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
                                ?: context.packageManager.getLaunchIntentForPackage("rikka.shizuku.manager")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                systemMonitor.requestShizukuPermission()
                            }
                        }
                    }
                )
            }

            // Start Monitoring Button
            Button(
                onClick = onStartMonitoring,
                enabled = isReadyToStart,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReadyToStart) (if (colors.isDark) Color(0xFFF3F4F6) else Color(0xFF0F172A)) else colors.elevatedSurface,
                    contentColor = if (isReadyToStart) (if (colors.isDark) Color(0xFF0C0D10) else Color.White) else colors.textTertiary,
                    disabledContainerColor = colors.elevatedSurface,
                    disabledContentColor = colors.textTertiary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = if (isReadyToStart) "START MONITORING" else "GRANT REQUIRED PERMISSIONS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    subtitle: String,
    description: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ProStatsColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isGranted) colors.accentGreen.copy(alpha = 0.4f) else colors.borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isGranted) colors.accentGreen else colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // State Indicator
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(colors.accentGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val chipColor = if (isOptional) colors.accentPurple else colors.accentOrange
                Box(
                    modifier = Modifier
                        .background(chipColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .border(1.dp, chipColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isOptional) "OPTIONAL" else "GRANT",
                        color = chipColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
