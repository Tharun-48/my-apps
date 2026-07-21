package com.example.prostats.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.prostats.data.SystemMonitor
import com.example.prostats.data.BatteryInfo
import kotlinx.coroutines.delay
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    systemMonitor: SystemMonitor,
    onNavigateToProcesses: () -> Unit,
    onNavigateToSotDetail: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF121214),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4ADE80),
                        selectedTextColor = Color(0xFF4ADE80),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0x224ADE80)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = "System Info") },
                    label = { Text("System Info", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4ADE80),
                        selectedTextColor = Color(0xFF4ADE80),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0x224ADE80)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4ADE80),
                        selectedTextColor = Color(0xFF4ADE80),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0x224ADE80)
                    )
                )
            }
        },
        containerColor = Color(0xFF0A0A0C),
        modifier = modifier
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> DashboardContent(
                    systemMonitor = systemMonitor,
                    onNavigateToProcesses = onNavigateToProcesses,
                    onNavigateToSotDetail = onNavigateToSotDetail,
                    onNavigateToSettings = {
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
                1 -> SystemInfoScreen()
                2 -> com.example.prostats.ui.settings.SettingsScreen(
                    systemMonitor = systemMonitor,
                    onNavigateBack = {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
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
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    // Live update loop for CPU, RAM, and Battery Currents/Temps
    LaunchedEffect(Unit) {
        while (true) {
            cpuUsage = systemMonitor.getSystemCpuUsage()
            val ramInfo = systemMonitor.getRamInfo()
            ramUsedGb = ramInfo.usedGb
            ramTotalGb = ramInfo.totalGb
            batteryInfo = systemMonitor.getBatteryInfo()
            thermalStatus = systemMonitor.getThermalStatus()
            coreFreqs = systemMonitor.getCpuCoreFrequencies()
            cpuTemp = systemMonitor.getCpuTemperature()
            batteryTemp = systemMonitor.getBatteryTemperature()
            sotMs = systemMonitor.getScreenOnTimeSinceLastChargeMs()
            delay(1500)
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
                title = { Text("ProStats - System Dashboard", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0C))
            )
        },
        containerColor = Color(0xFF0A0A0C),
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0A0A0C))
        ) {
            // Glows
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x1A4ADE80), Color.Transparent),
                            radius = 900f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Section 1: Grid row (SOT & Battery Temperature)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val minutes = (sotMs / 1000 / 60)
                    val sotHours = minutes / 60
                    val sotMins = minutes % 60
                    
                    DashboardCard(
                        title = "SCREEN-ON TIME",
                        value = "${sotHours}h ${sotMins}m",
                        subValue = "Since Last Charge",
                        color = Color(0xFFA78BFA),
                        onClick = onNavigateToSotDetail,
                        modifier = Modifier.weight(1f)
                    )

                    val tempColor = when {
                        batteryTemp >= 45f -> Color.Red
                        batteryTemp >= 38f -> Color(0xFFFB923C)
                        else -> Color(0xFF4ADE80)
                    }
                    DashboardCard(
                        title = "BATTERY TEMPERATURE",
                        value = "${String.format("%.1f", batteryTemp)}°C",
                        subValue = "Thermal: $thermalStatus",
                        color = tempColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Section 2: Battery Diagnostics Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "BATTERY DIAGNOSTICS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Health: ${batteryInfo.health}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Voltage: ${String.format("%.2f", batteryInfo.voltageV)} V", fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val currentText = if (batteryInfo.currentMa < 0) "${batteryInfo.currentMa} mA" else "+${batteryInfo.currentMa} mA"
                                val wattText = if (batteryInfo.currentMa < 0) "-${String.format("%.2f", batteryInfo.watts)} W" else "+${String.format("%.2f", batteryInfo.watts)} W"
                                val currentTextColor = if (batteryInfo.currentMa < 0) Color(0xFFFB923C) else Color(0xFF4ADE80)
                                Text(currentText, fontSize = 14.sp, color = currentTextColor, fontWeight = FontWeight.SemiBold)
                                Text("Power: $wattText", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Status: ${batteryInfo.status} (${batteryInfo.technology})", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "System Thermal State: $thermalStatus",
                            fontSize = 12.sp,
                            color = if (thermalStatus != "Normal") Color.Red else Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Section 3: CPU Cores Monitor Card (DevCheck-inspired)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "CPU CLUSTER FREQUENCIES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        coreFreqs.chunked(4).forEachIndexed { rowIndex, rowCores ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCores.forEachIndexed { colIndex, freq ->
                                    val coreId = rowIndex * 4 + colIndex
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Core $coreId", fontSize = 10.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "$freq MHz",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Section 4: System CPU load bar
                SystemLoadCard(
                    title = "SYSTEM CPU LOAD",
                    value = "${cpuUsage.toInt()}%",
                    progress = cpuUsage / 100f,
                    color = Color(0xFF4ADE80)
                )

                // Section 5: System RAM allocation
                val ramPercentage = if (ramTotalGb > 0) ramUsedGb / ramTotalGb else 0f
                SystemLoadCard(
                    title = "RAM ALLOCATION",
                    value = "${String.format("%.1f", ramUsedGb)} GB / ${String.format("%.1f", ramTotalGb)} GB",
                    progress = ramPercentage,
                    color = Color(0xFFFB923C)
                )

                // Section 6: Main Navigation Button
                Button(
                    onClick = onNavigateToProcesses,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(56.dp)
                ) {
                    Text(
                        text = "MANAGE RUNNING PROCESSES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCard(
    title: String,
    value: String,
    subValue: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = modifier.border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subValue, fontSize = 11.sp, color = Color.Gray)
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = modifier.border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subValue, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun SystemLoadCard(
    title: String,
    value: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress,
                color = color,
                trackColor = Color(0x22FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}
