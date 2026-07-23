package com.example.prostats.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        // Ambient background glow (only on dark themes)
        if (colors.isDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.accentGreen.copy(alpha = 0.15f), Color.Transparent),
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
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "STEP 2/3: CONFIGURE PRO STATS ACCESS",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "These permissions enable live process tracking, battery optimization analyses, and background statistics.",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permission Card 1: Battery stats
                PermissionCard(
                    title = "BATTERY USAGE STATS (Required)",
                    description = "Access per-app power consumption data and Screen-On Time (SOT) history.",
                    isGranted = hasBatteryOptimizations,
                    onClick = {
                        if (!hasBatteryOptimizations) {
                            systemMonitor.launchBatterySettings()
                        }
                    }
                )

                // Permission Card 2: Usage Stats
                PermissionCard(
                    title = "USAGE ACCESS (Required)",
                    description = "Allow Pro Stats to monitor running processes and real-time CPU/RAM usage statistics.",
                    isGranted = hasUsageAccess,
                    onClick = {
                        if (!hasUsageAccess) {
                            systemMonitor.launchUsageAccessSettings()
                        }
                    }
                )

                // Permission Card 3: Shizuku
                PermissionCard(
                    title = "SHIZUKU AUTHORIZATION (Optional)",
                    description = if (isShizukuRunning) {
                        "Tap to grant wireless ADB permissions for PC-grade task monitoring."
                    } else {
                        "Shizuku service is not running. Tap to open Shizuku app or install it."
                    },
                    isGranted = hasShizukuPermission,
                    isOptional = true,
                    onClick = {
                        if (isShizukuRunning) {
                            if (!hasShizukuPermission) {
                                systemMonitor.requestShizukuPermission()
                            }
                        } else {
                            // Launch Shizuku app
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                // Redirect to play store or site
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
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReadyToStart) colors.accentGreen else colors.cardSurface,
                    contentColor = Color.Black,
                    disabledContainerColor = colors.cardSurface,
                    disabledContentColor = colors.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (isReadyToStart) "START MONITORING" else "GRANT REQUIRED PERMISSIONS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ProStatsColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isGranted) colors.accentGreen else colors.borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isGranted) colors.accentGreen else colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            // State Indicator
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(colors.accentGreen, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    text = if (isOptional) "OPTIONAL" else "GRANT",
                    color = if (isOptional) colors.accentPurple else colors.accentOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (isOptional) colors.accentPurple else colors.accentOrange,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
