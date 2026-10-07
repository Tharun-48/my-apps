package com.example.prostats.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * Step descriptor for dynamic one-by-one setup flow.
 */
data class SetupStep(
    val title: String,
    val badge: String,
    val isOptional: Boolean,
    val icon: ImageVector,
    val whatItDoes: String,
    val whyItMatters: String,
    val isGranted: Boolean,
    val actionLabel: String,
    val onAction: () -> Unit
)

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
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var hasBatteryOptimizations by remember { mutableStateOf(false) }
    var hasUsageAccess by remember { mutableStateOf(false) }
    var isShizukuRunning by remember { mutableStateOf(false) }
    var hasShizukuPermission by remember { mutableStateOf(false) }
    var hasCheckedAutostart by remember { mutableStateOf(prefs.getBoolean("has_checked_autostart", false)) }

    var currentStepIndex by remember { mutableIntStateOf(0) }

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

    // Define the dynamic 4-step sequence
    val steps = listOf(
        SetupStep(
            title = "Battery Optimization",
            badge = "REQUIRED",
            isOptional = false,
            icon = Icons.Default.Settings,
            whatItDoes = "Exempts ProStats from Android background killing and aggressive sleep limits.",
            whyItMatters = "Ensures continuous Screen-on Time (SOT) and battery drain logging even when the screen is turned off.",
            isGranted = hasBatteryOptimizations,
            actionLabel = if (hasBatteryOptimizations) "PERMISSION GRANTED" else "ALLOW UNRESTRICTED BATTERY",
            onAction = {
                if (!hasBatteryOptimizations) {
                    systemMonitor.launchBatterySettings()
                }
            }
        ),
        SetupStep(
            title = "Usage Access",
            badge = "REQUIRED",
            isOptional = false,
            icon = Icons.Default.Info,
            whatItDoes = "Allows ProStats to detect foreground applications and background app task categories.",
            whyItMatters = "Required to calculate per-app battery drain, active app standby buckets, and app-specific SOT.",
            isGranted = hasUsageAccess,
            actionLabel = if (hasUsageAccess) "PERMISSION GRANTED" else "ENABLE USAGE ACCESS",
            onAction = {
                if (!hasUsageAccess) {
                    systemMonitor.launchUsageAccessSettings()
                }
            }
        ),
        SetupStep(
            title = "Autostart & Background Launch",
            badge = "RECOMMENDED",
            isOptional = true,
            icon = Icons.Default.PlayArrow,
            whatItDoes = "Allows the background tracking service to automatically restart when your phone powers on.",
            whyItMatters = "Without autostart, aggressive OEM managers (Xiaomi HyperOS, Samsung, Oppo, Vivo) halt tracking after reboot.",
            isGranted = hasCheckedAutostart,
            actionLabel = if (hasCheckedAutostart) "AUTOSTART CHECKED" else "CONFIGURE AUTOSTART",
            onAction = {
                hasCheckedAutostart = true
                prefs.edit().putBoolean("has_checked_autostart", true).apply()
                launchAutostartSettings(context)
            }
        ),
        SetupStep(
            title = "Shizuku Wireless ADB",
            badge = "OPTIONAL • PRO MODE",
            isOptional = true,
            icon = Icons.Default.CheckCircle,
            whatItDoes = "Elevated system diagnostics via wireless ADB without requiring root access.",
            whyItMatters = "Unlocks PC-grade process inspection, true measured PSS RAM stats, and per-process memory controls.",
            isGranted = hasShizukuPermission,
            actionLabel = if (hasShizukuPermission) "AUTHORIZED" else if (isShizukuRunning) "REQUEST SHIZUKU PERMISSION" else "OPEN SHIZUKU / INSTALL",
            onAction = {
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
    )

    val currentStep = steps[currentStepIndex.coerceIn(0, steps.size - 1)]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Subtle ambient radial background glow in dark mode
        if (colors.isDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.accentGreen.copy(alpha = 0.10f), Color.Transparent),
                            radius = 900f
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
            Spacer(modifier = Modifier.height(12.dp))

            // Step Progress Indicator Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    val isPast = index < currentStepIndex || step.isGranted
                    val isCurrent = index == currentStepIndex
                    val barColor = when {
                        step.isGranted -> colors.accentGreen
                        isCurrent -> colors.accentBlue
                        else -> colors.borderColorSubtle
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(barColor, RoundedCornerShape(2.dp))
                            .clickable { currentStepIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step Badge Pill
            Box(
                modifier = Modifier
                    .background(colors.elevatedSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "STEP ${currentStepIndex + 1} OF ${steps.size}: ${currentStep.badge}",
                    color = if (currentStep.isOptional) colors.accentPurple else colors.accentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Setup ProStats",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Configure app permissions step-by-step for accurate background telemetry.",
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Focused Step Card (dynamically shows one step at a time)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentStepIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width / 2 } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width / 2 } + fadeOut()
                            )
                        }
                    },
                    label = "SetupStepAnimation"
                ) { targetIndex ->
                    val step = steps[targetIndex.coerceIn(0, steps.size - 1)]
                    StepCard(
                        step = step,
                        onGrantClick = step.onAction
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Action Button for Current Step
                Button(
                    onClick = {
                        if (currentStep.isGranted) {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else if (isReadyToStart) {
                                onStartMonitoring()
                            }
                        } else {
                            currentStep.onAction()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentStep.isGranted) colors.accentGreen else colors.accentBlue,
                        contentColor = if (currentStep.isGranted) Color.Black else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (currentStep.isGranted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentStepIndex < steps.size - 1) "NEXT STEP →" else "START MONITORING",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            )
                        } else {
                            Text(
                                text = currentStep.actionLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Secondary Navigation Controls (Back / Skip / Start Monitoring)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        TextButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "← PREVIOUS",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (isReadyToStart && currentStepIndex < steps.size - 1) {
                        TextButton(
                            onClick = onStartMonitoring,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "ENTER DASHBOARD →",
                                color = colors.accentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (currentStep.isOptional && currentStepIndex < steps.size - 1) {
                        TextButton(
                            onClick = { currentStepIndex++ },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "SKIP FOR NOW →",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun StepCard(
    step: SetupStep,
    onGrantClick: () -> Unit
) {
    val colors = ProStatsColors.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (step.isGranted) colors.accentGreen.copy(alpha = 0.4f) else colors.borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onGrantClick)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Icon Circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = if (step.isGranted) colors.accentGreen.copy(alpha = 0.15f) else colors.accentBlue.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (step.isGranted) colors.accentGreen.copy(alpha = 0.4f) else colors.accentBlue.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step.isGranted) Icons.Default.Check else step.icon,
                    contentDescription = null,
                    tint = if (step.isGranted) colors.accentGreen else colors.accentBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = step.title,
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.borderColorSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // 1. What it does
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "WHAT IT DOES",
                    color = colors.accentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = step.whatItDoes,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Why it matters
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "WHY IT MATTERS",
                    color = colors.accentOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = step.whyItMatters,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Status Chip
            Box(
                modifier = Modifier
                    .background(
                        color = if (step.isGranted) colors.accentGreen.copy(alpha = 0.15f) else colors.elevatedSurface,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (step.isGranted) colors.accentGreen.copy(alpha = 0.4f) else colors.borderColorSubtle,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (step.isGranted) "CONFIGURED & ACTIVE" else if (step.isOptional) "OPTIONAL SETUP" else "SETUP REQUIRED",
                    color = if (step.isGranted) colors.accentGreen else if (step.isOptional) colors.accentPurple else colors.accentOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Robust helper to launch OEM Autostart managers (Xiaomi, Samsung, Oppo, Vivo, Transsion, Huawei)
 * with graceful fallback to Application Details Settings.
 */
fun launchAutostartSettings(context: Context) {
    val intents = listOf(
        // Xiaomi / HyperOS / MIUI
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        // Oppo / ColorOS / Realme
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        // Vivo / FuntouchOS / OriginOS
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        // Huawei EMUI / HarmonyOS
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        // Samsung One UI Device Care
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        // Transsion (Infinix / Tecno / itel)
        Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoRunActivity")),
        // Fallback: App Details Settings
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try next vendor intent
        }
    }
}
