package com.example.prostats.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.prostats.data.BatteryHealthData
import com.example.prostats.data.BatteryHealthEstimator
import com.example.prostats.data.BatteryInfo
import com.example.prostats.data.BatteryTracker
import com.example.prostats.data.SystemMonitor
import com.example.prostats.data.UpdateChecker
import com.example.prostats.data.UpdateInfo
import com.example.prostats.theme.ProStatsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    systemMonitor: SystemMonitor,
    onNavigateToProcesses: () -> Unit,
    onNavigateToSotDetail: () -> Unit,
    onNavigateToBatteryTempDetail: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProStatsColors.current
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = colors.navBarColor,
                contentColor = colors.textPrimary,
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(0) }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp, fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accentGreen,
                        selectedTextColor = colors.accentGreen,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                        indicatorColor = colors.accentGreen.copy(alpha = 0.16f)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(1) }
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = "System Info") },
                    label = { Text("System Info", fontSize = 11.sp, fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accentGreen,
                        selectedTextColor = colors.accentGreen,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                        indicatorColor = colors.accentGreen.copy(alpha = 0.16f)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        coroutineScope.launch { pagerState.scrollToPage(2) }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accentGreen,
                        selectedTextColor = colors.accentGreen,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                        indicatorColor = colors.accentGreen.copy(alpha = 0.16f)
                    )
                )
            }
        },
        containerColor = colors.background,
        modifier = modifier
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            key = { page -> page },
            beyondViewportPageCount = 0,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> DashboardContent(
                    systemMonitor = systemMonitor,
                    onNavigateToProcesses = onNavigateToProcesses,
                    onNavigateToSotDetail = onNavigateToSotDetail,
                    onNavigateToBatteryTempDetail = onNavigateToBatteryTempDetail,
                    onNavigateToSettings = {
                        coroutineScope.launch { pagerState.scrollToPage(2) }
                    }
                )
                1 -> SystemInfoScreen()
                2 -> com.example.prostats.ui.settings.SettingsScreen(
                    systemMonitor = systemMonitor,
                    onNavigateBack = {
                        coroutineScope.launch { pagerState.scrollToPage(0) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    systemMonitor: SystemMonitor,
    onNavigateToProcesses: () -> Unit,
    onNavigateToSotDetail: () -> Unit,
    onNavigateToBatteryTempDetail: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProStatsColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var sotMs by remember { mutableStateOf(0L) }
    var batteryTemp by remember { mutableStateOf(0f) }
    var cpuTemp by remember { mutableStateOf(0f) }
    var ramUsedGb by remember { mutableStateOf(0f) }
    var ramTotalGb by remember { mutableStateOf(0f) }
    var cpuUsage by remember { mutableStateOf(0f) }
    var batteryInfo by remember { mutableStateOf(BatteryInfo(0, "Good", 0f, "Li-ion", 0, "Idle")) }
    var thermalStatus by remember { mutableStateOf("Normal") }
    var coreFreqs by remember { mutableStateOf<List<Long>>(emptyList()) }
    var healthData by remember { mutableStateOf<BatteryHealthData?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // Automatic GitHub release update check on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                updateInfo = UpdateChecker.checkForUpdates(context, notifyUserIfAvailable = true)
            } catch (e: Exception) {
                // Ignore network errors
            }
        }
    }

    // Live update loop for CPU, RAM, Battery, and Health data
    LaunchedEffect(Unit) {
        var loopCount = 0
        while (true) {
            val cpu = withContext(Dispatchers.IO) { systemMonitor.getSystemCpuUsage() }
            val ramInfo = withContext(Dispatchers.IO) { systemMonitor.getRamInfo() }
            val bat = withContext(Dispatchers.IO) { systemMonitor.getBatteryInfo() }
            val thermal = withContext(Dispatchers.IO) { systemMonitor.getThermalStatus() }
            val freqs = withContext(Dispatchers.IO) { systemMonitor.getCpuCoreFrequencies() }
            val cTemp = withContext(Dispatchers.IO) { systemMonitor.getCpuTemperature() }
            val bTemp = withContext(Dispatchers.IO) { systemMonitor.getBatteryTemperature() }
            val sot = withContext(Dispatchers.IO) { systemMonitor.getScreenOnTimeSinceLastChargeMs() }
            val health = if (loopCount % 6 == 0) {
                withContext(Dispatchers.IO) { BatteryHealthEstimator.getHealthData(context) }
            } else null

            cpuUsage = cpu
            ramUsedGb = ramInfo.usedGb
            ramTotalGb = ramInfo.totalGb
            batteryInfo = bat
            thermalStatus = thermal
            coreFreqs = freqs
            cpuTemp = cTemp
            batteryTemp = bTemp
            sotMs = sot
            if (health != null) healthData = health
            loopCount++

            delay(1200)
        }
    }

    // Refresh static stats on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sotMs = systemMonitor.getScreenOnTimeSinceLastChargeMs()
                batteryTemp = systemMonitor.getBatteryTemperature()
                cpuTemp = systemMonitor.getCpuTemperature()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colors.accentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ProStats",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = colors.textPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(colors.accentGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v2.3",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentGreen
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp)
                            .background(colors.elevatedSurface, CircleShape)
                            .border(1.dp, colors.borderColorSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                // GitHub In-App Update Banner (when new version available)
                updateInfo?.let { update ->
                    if (update.isAvailable) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.accentGreen, RoundedCornerShape(18.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(colors.accentGreen.copy(alpha = 0.16f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = colors.accentGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "New Update: v${update.remoteVersion}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Tap to update to latest build",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Install", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Dynamic Hero Multi-Arc Radial Telemetry Hub
                val ramRatio = if (ramTotalGb > 0) (ramUsedGb / ramTotalGb).coerceIn(0f, 1f) else 0f
                RadialTelemetryHeroCard(
                    cpuUsage = cpuUsage,
                    ramUsageRatio = ramRatio,
                    ramUsedGb = ramUsedGb,
                    ramTotalGb = ramTotalGb,
                    batteryMa = batteryInfo.currentMa,
                    batteryWatts = batteryInfo.watts,
                    batteryStatus = batteryInfo.status,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Metric Tiles: Screen Time & Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val minutes = (sotMs / 1000 / 60)
                    val sotHours = minutes / 60
                    val sotMins = minutes % 60

                    HeroMetricTile(
                        category = "SCREEN TIME",
                        value = "${sotHours}h ${sotMins}m",
                        subValue = "Since Last Charge",
                        accentColor = colors.accentPurple,
                        onClick = onNavigateToSotDetail,
                        modifier = Modifier.weight(1f)
                    )

                    val tempColor = when {
                        batteryTemp >= 45f -> Color(0xFFEF4444)
                        batteryTemp >= 38f -> colors.accentOrange
                        else -> colors.accentGreen
                    }

                    HeroMetricTile(
                        category = "TEMPERATURE",
                        value = "${String.format("%.1f", batteryTemp)}°C",
                        subValue = "Thermal: $thermalStatus",
                        accentColor = tempColor,
                        onClick = onNavigateToBatteryTempDetail,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Battery Health & Live Energy Flow Card
                healthData?.let { hd ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Battery Health & Power",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                val cycleSource = if (hd.cycleSourceIsSystem) "System" else "Calc"
                                Box(
                                    modifier = Modifier
                                        .background(colors.elevatedSurface, RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$cycleSource Cycles: ${hd.chargeCycles}",
                                        fontSize = 10.sp,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val healthColor = when {
                                    hd.healthScore >= 80 -> colors.accentGreen
                                    hd.healthScore >= 50 -> colors.accentYellow
                                    else -> Color(0xFFEF4444)
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${hd.healthScore}",
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = healthColor,
                                            lineHeight = 36.sp
                                        )
                                        Text(
                                            text = "%",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = healthColor.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            hd.healthScore >= 85 -> "Optimal Condition"
                                            hd.healthScore >= 75 -> "Good Health"
                                            hd.healthScore >= 60 -> "Fair Condition"
                                            else -> "Needs Attention"
                                        },
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${hd.currentCapacityMah} mAh",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Design: ${hd.designCapacityMah} mAh",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                    if (hd.avgDailySotMs > 0) {
                                        val avgSotMins = hd.avgDailySotMs / 1000 / 60
                                        val avgSotH = avgSotMins / 60
                                        val avgSotM = avgSotMins % 60
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Avg SOT: ${avgSotH}h ${avgSotM}m",
                                            fontSize = 10.sp,
                                            color = colors.accentPurple,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Dynamic Live Power Banner
                            Spacer(modifier = Modifier.height(14.dp))
                            if (batteryInfo.status == "Charging" && hd.chargeSpeedMa > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.accentGreen.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .border(1.dp, colors.accentGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚡ Charging: ${hd.chargeSpeedMa} mA (${String.format("%.1f", hd.chargeSpeedWatts)}W)",
                                            fontSize = 11.sp,
                                            color = colors.accentGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (hd.estimatedTimeToFull > 0) {
                                            val minsToFull = hd.estimatedTimeToFull / 1000 / 60
                                            val hrsToFull = minsToFull / 60
                                            val remMins = minsToFull % 60
                                            Text(
                                                text = "~${hrsToFull}h ${remMins}m to full",
                                                fontSize = 10.sp,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            } else if (hd.estimatedBatteryLife > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.elevatedSurface, RoundedCornerShape(12.dp))
                                        .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val minsLeft = hd.estimatedBatteryLife / 1000 / 60
                                        val hrsLeft = minsLeft / 60
                                        val remMins = minsLeft % 60
                                        Text(
                                            text = "Est. Runtime: ${hrsLeft}h ${remMins}m",
                                            fontSize = 11.sp,
                                            color = colors.accentOrange,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (hd.dischargeRatePctPerHour > 0) {
                                            Text(
                                                text = "${String.format("%.1f", hd.dischargeRatePctPerHour)}%/hr drain",
                                                fontSize = 10.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // CPU Cluster Frequency Grid
                if (coreFreqs.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CPU CLUSTER FREQUENCIES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "${coreFreqs.size} Cores Active",
                                    fontSize = 11.sp,
                                    color = colors.accentBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            coreFreqs.chunked(4).forEachIndexed { rowIndex, rowCores ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowCores.forEachIndexed { colIndex, freq ->
                                        val coreId = rowIndex * 4 + colIndex
                                        val maxExpectedFreq = 3000f
                                        val freqRatio = (freq / maxExpectedFreq).coerceIn(0.1f, 1f)

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(colors.elevatedSurface, RoundedCornerShape(12.dp))
                                                .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 6.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "C$coreId",
                                                    fontSize = 9.sp,
                                                    color = colors.textTertiary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (freq > 0) "$freq" else "—",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary
                                                )
                                                Text(
                                                    text = "MHz",
                                                    fontSize = 8.sp,
                                                    color = colors.textSecondary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .background(colors.borderColorSubtle, RoundedCornerShape(2.dp))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(freqRatio)
                                                            .height(3.dp)
                                                            .background(colors.accentBlue, RoundedCornerShape(2.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // Process Manager Action Button
                Button(
                    onClick = onNavigateToProcesses,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isDark) Color(0xFFF3F4F6) else Color(0xFF0F172A),
                        contentColor = if (colors.isDark) Color(0xFF0C0D10) else Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "MANAGE RUNNING PROCESSES",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * High-impact, GPU-accelerated Radial Multi-Arc Hero Hub.
 * Draws CPU, RAM, and Battery Telemetry on smooth canvas arcs with zero recomposition overhead.
 */
@Composable
fun RadialTelemetryHeroCard(
    cpuUsage: Float,
    ramUsageRatio: Float,
    ramUsedGb: Float,
    ramTotalGb: Float,
    batteryMa: Int,
    batteryWatts: Float,
    batteryStatus: String,
    modifier: Modifier = Modifier
) {
    val colors = ProStatsColors.current

    // Smooth lightweight 300ms transitions
    val animatedCpu by animateFloatAsState(
        targetValue = (cpuUsage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "radialCpu"
    )
    val animatedRam by animateFloatAsState(
        targetValue = ramUsageRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "radialRam"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = modifier
            .border(1.dp, colors.borderColor, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Overview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-Arc Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
            ) {
                val cpuColor = colors.accentGreen
                val ramColor = colors.accentOrange
                val trackColor = colors.elevatedSurface

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthOuter = 10.dp.toPx()
                    val strokeWidthInner = 8.dp.toPx()

                    // Outer Ring: CPU (Start at -90deg / Top)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthOuter, cap = StrokeCap.Round),
                        size = Size(size.width - strokeWidthOuter, size.height - strokeWidthOuter),
                        topLeft = Offset(strokeWidthOuter / 2, strokeWidthOuter / 2)
                    )
                    drawArc(
                        color = cpuColor,
                        startAngle = -90f,
                        sweepAngle = animatedCpu * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthOuter, cap = StrokeCap.Round),
                        size = Size(size.width - strokeWidthOuter, size.height - strokeWidthOuter),
                        topLeft = Offset(strokeWidthOuter / 2, strokeWidthOuter / 2)
                    )

                    // Inner Ring: RAM
                    val innerPadding = 18.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthInner, cap = StrokeCap.Round),
                        size = Size(size.width - innerPadding * 2, size.height - innerPadding * 2),
                        topLeft = Offset(innerPadding, innerPadding)
                    )
                    drawArc(
                        color = ramColor,
                        startAngle = -90f,
                        sweepAngle = animatedRam * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthInner, cap = StrokeCap.Round),
                        size = Size(size.width - innerPadding * 2, size.height - innerPadding * 2),
                        topLeft = Offset(innerPadding, innerPadding)
                    )
                }

                // Center Digital Readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${cpuUsage.toInt()}%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = "CPU LOAD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Legend Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TelemetryLegendPill(
                    dotColor = colors.accentGreen,
                    title = "CPU",
                    value = "${cpuUsage.toInt()}%"
                )
                TelemetryLegendPill(
                    dotColor = colors.accentOrange,
                    title = "RAM",
                    value = "${String.format("%.1f", ramUsedGb)}/${String.format("%.1f", ramTotalGb)}G"
                )
                val currentPwr = if (batteryMa != 0) {
                    if (batteryMa > 0) "+${batteryMa}mA" else "${batteryMa}mA"
                } else "Idle"
                TelemetryLegendPill(
                    dotColor = if (batteryMa > 0) colors.accentGreen else colors.accentPurple,
                    title = "PWR",
                    value = currentPwr
                )
            }
        }
    }
}

@Composable
private fun TelemetryLegendPill(
    dotColor: Color,
    title: String,
    value: String
) {
    val colors = ProStatsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.elevatedSurface, RoundedCornerShape(10.dp))
            .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$title: ",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textPrimary
        )
    }
}

@Composable
fun HeroMetricTile(
    category: String,
    value: String,
    subValue: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProStatsColors.current
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 300),
        label = "heroAccentColor"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = modifier.border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(animatedAccent, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    letterSpacing = 0.8.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = animatedAccent,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subValue,
                fontSize = 11.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
