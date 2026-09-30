package com.example.prostats.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prostats.data.*
import com.example.prostats.theme.ProStatsColors
import com.example.prostats.ui.main.AppIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SotDetailScreen(
    systemMonitor: SystemMonitor,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ProStatsColors.current

    // Baseline timestamp (initialized to app install/first run, or last charge >= 90%)
    var lastUnplugTs by remember { mutableLongStateOf(BatteryTracker.getLastUnplugFromFullTimestamp(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Interactive timeline draggable range state (startMs, endMs)
    var selectedRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    // Periodic refresh loop every 15s for live metrics
    LaunchedEffect(Unit) {
        while (true) {
            lastUnplugTs = BatteryTracker.getLastUnplugFromFullTimestamp(context)
            refreshTick++
            kotlinx.coroutines.delay(15000)
        }
    }

    val startDateFormatted = remember(lastUnplugTs) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(lastUnplugTs))
    }

    // Sort state for app list
    var appSort by remember { mutableStateOf("Time") } // Time | Battery | Name

    // Time range toggle state (Since Charge or 24h)
    var timeRange by remember { mutableStateOf("Since Charge") }

    val startTime = remember(lastUnplugTs, timeRange, refreshTick) {
        val now = System.currentTimeMillis()
        when (timeRange) {
            "Since Charge" -> lastUnplugTs
            else -> now - 24 * 60 * 60 * 1000L
        }
    }

    // History points
    val points = remember(startTime, refreshTick, timeRange) {
        when (timeRange) {
            "Since Charge" -> BatteryTracker.getHistorySinceLastCharge(context)
            else -> BatteryTracker.getHistory24h(context)
        }
    }

    // Active Screen On Time for the selected range or entire window
    val activeWindowSotMs by produceState(initialValue = 0L, key1 = startTime, key2 = refreshTick, key3 = selectedRange) {
        val now = System.currentTimeMillis()
        val qStart = selectedRange?.first ?: startTime
        val qEnd = selectedRange?.second ?: now
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            systemMonitor.getScreenOnTimeMs(qStart, qEnd)
        }
    }

    val totalSotMs by produceState(initialValue = 0L, key1 = startTime, key2 = refreshTick) {
        val now = System.currentTimeMillis()
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            systemMonitor.getScreenOnTimeMs(startTime, now)
        }
    }

    // Dynamic hasData evaluation
    val hasData = remember(points, totalSotMs) {
        points.isNotEmpty() || totalSotMs > 0L
    }

    // App usage list (filtered dynamically by selected draggable timeframe)
    val rawAppList by produceState(
        initialValue = emptyList<com.example.prostats.data.AppBatteryUsage>(),
        startTime,
        refreshTick,
        hasData,
        selectedRange
    ) {
        if (!hasData) {
            value = emptyList()
        } else {
            val now = System.currentTimeMillis()
            val qStart = selectedRange?.first ?: startTime
            val qEnd = selectedRange?.second ?: now
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                systemMonitor.getAppBatteryUsageList(qStart, qEnd)
            }
        }
    }

    val appUsageList = remember(rawAppList, appSort) {
        when (appSort) {
            "Battery" -> rawAppList.sortedByDescending { it.batteryUsagePct }
            "Name" -> rawAppList.sortedBy { it.appName }
            else -> rawAppList.sortedByDescending { it.foregroundTimeMs }
        }
    }

    val totalSotFormatted = remember(totalSotMs, hasData) {
        if (!hasData) "—"
        else {
            val mins = totalSotMs / 1000 / 60
            val hrs = mins / 60
            val remMins = mins % 60
            if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
        }
    }

    val activeSotBreakdown = remember(activeWindowSotMs) {
        val totalMins = activeWindowSotMs / 1000 / 60
        val hrs = totalMins / 60
        val mins = totalMins % 60
        Pair(hrs, mins)
    }

    // Screen Off Time
    val screenOffMs by produceState(initialValue = 0L, key1 = startTime, key2 = refreshTick, key3 = hasData) {
        if (!hasData) {
            value = 0L
        } else {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                systemMonitor.getScreenOffTimeMs(startTime, System.currentTimeMillis())
            }
        }
    }

    val screenOffTimeFormatted = remember(screenOffMs, hasData) {
        if (!hasData) "—"
        else {
            val mins = screenOffMs / 1000 / 60
            val hrs = mins / 60
            val remMins = mins % 60
            if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
        }
    }

    // Average daily SOT
    val avgDailySot = remember(lastUnplugTs) {
        val health = BatteryHealthEstimator.getHealthData(context)
        health.avgDailySotMs
    }

    val avgDailySotFormatted = remember(avgDailySot) {
        if (avgDailySot <= 0) "—"
        else {
            val mins = avgDailySot / 1000 / 60
            val hrs = mins / 60
            val remMins = mins % 60
            if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
        }
    }

    // Wakelocks (via Shizuku when available)
    val wakelocks by produceState(initialValue = emptyList<com.example.prostats.data.WakelockInfo>(), key1 = lastUnplugTs, key2 = refreshTick) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            systemMonitor.getWakelockInfo()
        }
    }

    // Deep Sleep & Screen-Off idle drain analytics
    val deepSleepStats = remember(lastUnplugTs, refreshTick) {
        BatteryTracker.getDeepSleepStats(context)
    }

    // Charging session history
    val chargingSessions = remember(refreshTick) {
        BatteryTracker.getChargingSessions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Screen-on Time & Battery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            if (!hasData) {
                // No-data placeholder
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("⚡", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Data Captured Yet",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Charge your device to 90% or above and disconnect charger to establish the baseline and track real-time SOT drain.",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // Graph card
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.borderColor, RoundedCornerShape(22.dp))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                // Header: Active SOT & Legend
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Active",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "ⓘ",
                                                fontSize = 11.sp,
                                                color = colors.textTertiary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "${activeSotBreakdown.first}",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "hrs",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${activeSotBreakdown.second}",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "mins",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }

                                    // Right: Legend & Time range toggle
                                    Column(horizontalAlignment = Alignment.End) {
                                        // Segmented toggle
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .background(colors.elevatedSurface, RoundedCornerShape(10.dp))
                                                .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(10.dp))
                                                .padding(2.dp)
                                        ) {
                                            listOf("Since Charge", "24h").forEach { range ->
                                                val active = timeRange == range
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (active) Color(0xFF0091EA).copy(alpha = 0.22f) else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                            width = if (active) 1.dp else 0.dp,
                                                            color = if (active) Color(0xFF0091EA).copy(alpha = 0.5f) else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        .clickable {
                                                            timeRange = range
                                                            selectedRange = null
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = range,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (active) Color(0xFF0091EA) else colors.textSecondary
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        // Legend items
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFF0091EA), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Battery usage", fontSize = 11.sp, color = colors.textSecondary)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFF10B981), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Charging", fontSize = 11.sp, color = colors.textSecondary)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                BatteryGraph(
                                    points = points,
                                    selectedRange = selectedRange,
                                    onRangeSelected = { selectedRange = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )

                                if (selectedRange != null) {
                                    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                    val startStr = sdf.format(Date(selectedRange!!.first))
                                    val endStr = sdf.format(Date(selectedRange!!.second))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Showing drain from $startStr to $endStr",
                                            color = Color(0xFF0091EA),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        TextButton(
                                            onClick = { selectedRange = null },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Reset Filter",
                                                color = colors.accentOrange,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SOT & Screen Off Symmetrical Tile Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val sotSubtitle = when (timeRange) {
                                "24h" -> "Last 24 hours active"
                                else -> "Since disconnected"
                            }
                            val screenOffSubtitle = when (timeRange) {
                                "24h" -> "Last 24 hours idle"
                                else -> "Background standby"
                            }

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, colors.borderColor, RoundedCornerShape(20.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).background(colors.accentPurple, CircleShape))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("SCREEN ON", fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(totalSotFormatted, fontSize = 22.sp, color = colors.accentPurple, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(sotSubtitle, fontSize = 10.sp, color = colors.textTertiary)
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, colors.borderColor, RoundedCornerShape(20.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).background(colors.accentOrange, CircleShape))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("SCREEN OFF", fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(screenOffTimeFormatted, fontSize = 22.sp, color = colors.accentOrange, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(screenOffSubtitle, fontSize = 10.sp, color = colors.textTertiary)
                                }
                            }
                        }
                    }

                    // Average Daily SOT card
                    if (avgDailySot > 0) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.borderColor, RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("AVG DAILY SCREEN-ON TIME", fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Historical 7-day rolling average", fontSize = 11.sp, color = colors.textTertiary)
                                    }
                                    Text(avgDailySotFormatted, fontSize = 22.sp, color = colors.accentBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Wakelock section (via Shizuku)
                    if (wakelocks.isNotEmpty()) {
                        item {
                            Text(
                                text = "TOP SYSTEM WAKELOCKS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                                letterSpacing = 1.sp
                            )
                        }
                        items(wakelocks.take(10), key = { it.name }) { wl ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = wl.name,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Wake Count: ${wl.count}",
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val durationMins = wl.totalDurationMs / 1000 / 60
                                        val durationText = if (durationMins > 60) "${durationMins / 60}h ${durationMins % 60}m" else "${durationMins}m"
                                        Text(
                                            text = durationText,
                                            color = colors.accentOrange,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section: Deep Sleep & Screen-Off Idle Drain Analytics (Battery Guru Feature)
                    item {
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
                                                .size(10.dp)
                                                .background(colors.accentBlue, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "DEEP SLEEP & IDLE DRAIN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textSecondary,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(colors.accentBlue.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%.1f%% Deep Sleep", deepSleepStats.deepSleepPct),
                                            color = colors.accentBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val dsMins = deepSleepStats.deepSleepTimeMs / 1000 / 60
                                    val dsHrs = dsMins / 60
                                    val dsRem = dsMins % 60
                                    val dsFormatted = if (dsHrs > 0) "${dsHrs}h ${dsRem}m" else "${dsRem}m"

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(colors.elevatedSurface, RoundedCornerShape(14.dp))
                                            .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(14.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            Text("Deep Sleep", color = colors.textSecondary, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(dsFormatted, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(colors.elevatedSurface, RoundedCornerShape(14.dp))
                                            .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(14.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            Text("Screen-Off Drain", color = colors.textSecondary, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                String.format(Locale.US, "%.2f%% / hr", deepSleepStats.screenOffDrainRatePctPerHour),
                                                color = colors.accentGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section: Recent Charging Sessions (Battery Guru & AccuBattery Feature)
                    if (chargingSessions.isNotEmpty()) {
                        item {
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
                                                    .size(10.dp)
                                                    .background(colors.accentGreen, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "RECENT CHARGING SESSIONS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textSecondary,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                        Text(
                                            text = "${chargingSessions.size} recorded",
                                            fontSize = 11.sp,
                                            color = colors.textTertiary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    chargingSessions.take(4).forEachIndexed { idx, session ->
                                        if (idx > 0) {
                                            HorizontalDivider(color = colors.borderColorSubtle, modifier = Modifier.padding(vertical = 10.dp))
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${session.startLevel}% → ${session.endLevel}%",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = colors.textPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "(+${session.levelGained}%)",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = colors.accentGreen
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${session.chargerType} • ${session.durationMs / 60000} mins",
                                                    color = colors.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                if (session.energyAddedMah > 0) {
                                                    Text(
                                                        text = "+${session.energyAddedMah} mAh",
                                                        color = colors.accentGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                if (session.peakTempC > 0f) {
                                                    Text(
                                                        text = "Peak ${String.format(Locale.US, "%.1f°C", session.peakTempC)}",
                                                        color = colors.textTertiary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // App list header + sort tabs
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedRange != null) {
                                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    val startStr = sdf.format(Date(selectedRange!!.first))
                                    val endStr = sdf.format(Date(selectedRange!!.second))
                                    "APP DRAIN ($startStr - $endStr)"
                                } else {
                                    "APP BATTERY DRAIN"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedRange != null) Color(0xFF0091EA) else colors.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (selectedRange != null) {
                                    "Selected timeframe"
                                } else if (timeRange == "24h") {
                                    "Last 24 hours"
                                } else {
                                    "Since charge"
                                },
                                fontSize = 11.sp,
                                color = if (selectedRange != null) Color(0xFF0091EA) else colors.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Time" to "Active Time", "Battery" to "Drain %", "Name" to "App Name").forEach { (key, label) ->
                                val active = appSort == key
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (active) Color(0xFF0091EA).copy(alpha = 0.18f) else colors.elevatedSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (active) Color(0xFF0091EA).copy(alpha = 0.5f) else colors.borderColorSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { appSort = key }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) Color(0xFF0091EA) else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (appUsageList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedRange != null) "No app activity recorded in selected timeframe" else "No application usage captured in this interval",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(appUsageList, key = { it.packageName }) { app ->
                            AppSotRow(app = app)
                        }
                    }
                }
            }
        }
    }
}

private enum class DragTarget {
    NONE, START_HANDLE, END_HANDLE, MIDDLE_REGION
}

@Composable
fun BatteryGraph(
    points: List<HistoryPoint>,
    selectedRange: Pair<Long, Long>?,
    onRangeSelected: (Pair<Long, Long>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProStatsColors.current
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No history captured yet", color = colors.textSecondary, fontSize = 12.sp)
        }
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.timestamp } }
    val minTime = sortedPoints.first().timestamp
    val maxTime = sortedPoints.last().timestamp
    val timeSpan = (maxTime - minTime).coerceAtLeast(60000L)

    val labelFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val labelColor = if (colors.isDark) android.graphics.Color.GRAY else android.graphics.Color.DKGRAY
    val gridColor = if (colors.isDark) Color(0x12FFFFFF) else Color(0x10000000)

    var activeDragTarget by remember { mutableStateOf(DragTarget.NONE) }
    var dragInitialRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var dragStartX by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .pointerInput(sortedPoints, selectedRange) {
                detectTapGestures(
                    onDoubleTap = {
                        // Double tap reverts to normal state
                        onRangeSelected(null)
                    },
                    onTap = { offset ->
                        val width = size.width
                        val xRatio = (offset.x / width).coerceIn(0f, 1f)
                        val tappedTs = minTime + (xRatio * timeSpan).toLong()
                        // Center a 2-hour slice around tap position
                        val halfSpan = (timeSpan / 4L).coerceIn(30 * 60 * 1000L, 3 * 60 * 60 * 1000L)
                        val s = (tappedTs - halfSpan).coerceAtLeast(minTime)
                        val e = (tappedTs + halfSpan).coerceAtMost(maxTime)
                        onRangeSelected(Pair(s, if (e > s) e else s + 60000L))
                    }
                )
            }
            .pointerInput(sortedPoints, selectedRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        dragStartX = offset.x

                        if (selectedRange != null) {
                            dragInitialRange = selectedRange
                            val (sTs, eTs) = selectedRange
                            val sX = ((sTs - minTime).toFloat() / timeSpan) * width
                            val eX = ((eTs - minTime).toFloat() / timeSpan) * width
                            val thresholdPx = 36.dp.toPx()

                            activeDragTarget = when {
                                kotlin.math.abs(offset.x - sX) <= thresholdPx -> DragTarget.START_HANDLE
                                kotlin.math.abs(offset.x - eX) <= thresholdPx -> DragTarget.END_HANDLE
                                offset.x in (sX..eX) -> DragTarget.MIDDLE_REGION
                                else -> {
                                    // Tap/drag outside: create new range
                                    val tappedTs = minTime + ((offset.x / width).coerceIn(0f, 1f) * timeSpan).toLong()
                                    val defaultSpan = (timeSpan / 3L).coerceIn(60 * 60 * 1000L, 3 * 60 * 60 * 1000L)
                                    val newStart = tappedTs.coerceAtLeast(minTime)
                                    val newEnd = (newStart + defaultSpan).coerceAtMost(maxTime)
                                    val newRange = Pair(newStart, newEnd)
                                    onRangeSelected(newRange)
                                    dragInitialRange = newRange
                                    DragTarget.END_HANDLE
                                }
                            }
                        } else {
                            // Initial drag when range is null: create a slice
                            val tappedTs = minTime + ((offset.x / width).coerceIn(0f, 1f) * timeSpan).toLong()
                            val defaultSpan = (timeSpan / 3L).coerceIn(60 * 60 * 1000L, 3 * 60 * 60 * 1000L)
                            val newStart = (tappedTs - defaultSpan / 2).coerceAtLeast(minTime)
                            val newEnd = (newStart + defaultSpan).coerceAtMost(maxTime)
                            val newRange = Pair(newStart, newEnd)
                            onRangeSelected(newRange)
                            dragInitialRange = newRange
                            activeDragTarget = DragTarget.END_HANDLE
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val width = size.width
                        val initial = dragInitialRange ?: return@detectDragGestures
                        val deltaX = change.position.x - dragStartX
                        val deltaTs = ((deltaX / width) * timeSpan).toLong()

                        when (activeDragTarget) {
                            DragTarget.START_HANDLE -> {
                                val newStart = (initial.first + deltaTs).coerceIn(minTime, initial.second - 15 * 60 * 1000L)
                                onRangeSelected(Pair(newStart, initial.second))
                            }
                            DragTarget.END_HANDLE -> {
                                val newEnd = (initial.second + deltaTs).coerceIn(initial.first + 15 * 60 * 1000L, maxTime)
                                onRangeSelected(Pair(initial.first, newEnd))
                            }
                            DragTarget.MIDDLE_REGION -> {
                                val windowDuration = initial.second - initial.first
                                val newStart = (initial.first + deltaTs).coerceIn(minTime, maxTime - windowDuration)
                                val newEnd = newStart + windowDuration
                                onRangeSelected(Pair(newStart, newEnd))
                            }
                            DragTarget.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        activeDragTarget = DragTarget.NONE
                        dragInitialRange = null
                    },
                    onDragCancel = {
                        activeDragTarget = DragTarget.NONE
                        dragInitialRange = null
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height - 24.dp.toPx()

        val paint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        // 1. Draw horizontal grid lines & percentage labels (0%, 25%, 50%, 75%, 100%)
        for (pct in listOf(0, 25, 50, 75, 100)) {
            val y = height * (1f - pct / 100f)
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$pct%", width - 4.dp.toPx(), y - 4.dp.toPx(), paint)
        }

        val coords = sortedPoints.map { pt ->
            val xRatio = (pt.timestamp - minTime).toFloat() / timeSpan
            val yRatio = (pt.batteryLevel / 100f).coerceIn(0f, 1f)
            Offset(xRatio * width, height * (1f - yRatio))
        }

        if (coords.isNotEmpty()) {
            val isFiltered = selectedRange != null
            val (sTs, eTs) = selectedRange ?: Pair(minTime, maxTime)
            val startX = ((sTs - minTime).toFloat() / timeSpan).coerceIn(0f, 1f) * width
            val endX = ((eTs - minTime).toFloat() / timeSpan).coerceIn(0f, 1f) * width

            // Draw full base curve (dimmed if filtered, vibrant if normal)
            val baseLinePath = Path().apply {
                moveTo(coords[0].x, coords[0].y)
                for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
            }

            if (!isFiltered) {
                val fillPath = Path().apply {
                    moveTo(coords[0].x, coords[0].y)
                    for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
                    lineTo(coords.last().x, height)
                    lineTo(coords.first().x, height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0091EA).copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                drawPath(
                    path = baseLinePath,
                    brush = Brush.horizontalGradient(colors = listOf(Color(0xFF0091EA), Color(0xFF10B981))),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                // Dimmed background curve
                drawPath(
                    path = baseLinePath,
                    color = if (colors.isDark) Color(0x35FFFFFF) else Color(0x25000000),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Highlighted slice between startX and endX
                val sliceCoords = coords.filter { it.x in (startX..endX) }.toMutableList()

                // Interpolate start and end edge points on the curve
                fun getYAtX(targetX: Float): Float {
                    val p1 = coords.lastOrNull { it.x <= targetX } ?: coords.first()
                    val p2 = coords.firstOrNull { it.x >= targetX } ?: coords.last()
                    if (p1.x == p2.x) return p1.y
                    val fraction = (targetX - p1.x) / (p2.x - p1.x)
                    return p1.y + fraction * (p2.y - p1.y)
                }

                val startY = getYAtX(startX)
                val endY = getYAtX(endX)

                val fullSliceCoords = mutableListOf<Offset>()
                fullSliceCoords.add(Offset(startX, startY))
                sliceCoords.forEach { pt ->
                    if (pt.x > startX && pt.x < endX) fullSliceCoords.add(pt)
                }
                fullSliceCoords.add(Offset(endX, endY))

                // Highlighted blue shaded area
                val sliceFillPath = Path().apply {
                    moveTo(fullSliceCoords.first().x, fullSliceCoords.first().y)
                    for (i in 1 until fullSliceCoords.size) lineTo(fullSliceCoords[i].x, fullSliceCoords[i].y)
                    lineTo(fullSliceCoords.last().x, height)
                    lineTo(fullSliceCoords.first().x, height)
                    close()
                }
                drawPath(
                    path = sliceFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0091EA).copy(alpha = 0.32f), Color(0xFF0091EA).copy(alpha = 0.06f))
                    )
                )

                // Vertical boundary lines
                drawLine(
                    color = Color(0xFF0091EA).copy(alpha = 0.55f),
                    start = Offset(startX, 0f),
                    end = Offset(startX, height),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF0091EA).copy(alpha = 0.55f),
                    start = Offset(endX, 0f),
                    end = Offset(endX, height),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Highlighted blue curve segment
                val sliceLinePath = Path().apply {
                    moveTo(fullSliceCoords.first().x, fullSliceCoords.first().y)
                    for (i in 1 until fullSliceCoords.size) lineTo(fullSliceCoords[i].x, fullSliceCoords[i].y)
                }
                drawPath(
                    path = sliceLinePath,
                    color = Color(0xFF0091EA),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draggable start handle (Outer blue ring, dark center)
                drawCircle(
                    color = Color(0xFF0091EA),
                    radius = 7.5.dp.toPx(),
                    center = Offset(startX, startY)
                )
                drawCircle(
                    color = if (colors.isDark) Color(0xFF181820) else Color.White,
                    radius = 3.2.dp.toPx(),
                    center = Offset(startX, startY)
                )

                // Draggable end handle (Outer blue ring, dark center)
                drawCircle(
                    color = Color(0xFF0091EA),
                    radius = 7.5.dp.toPx(),
                    center = Offset(endX, endY)
                )
                drawCircle(
                    color = if (colors.isDark) Color(0xFF181820) else Color.White,
                    radius = 3.2.dp.toPx(),
                    center = Offset(endX, endY)
                )

                // Floating Blue Pill Badge at Top (HH:mm-HH:mm)
                val sText = labelFormat.format(Date(sTs))
                val eText = labelFormat.format(Date(eTs))
                val pillText = "$sText-$eText"

                val pillPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.dp.toPx()
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val pillBgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0091EA")
                    style = android.graphics.Paint.Style.FILL
                }

                val pillWidth = 96.dp.toPx()
                val pillHeight = 26.dp.toPx()
                val pillCenterX = ((startX + endX) / 2f).coerceIn(pillWidth / 2f + 4.dp.toPx(), width - pillWidth / 2f - 4.dp.toPx())
                val pillCenterY = 14.dp.toPx()
                val pillRect = android.graphics.RectF(
                    pillCenterX - pillWidth / 2f,
                    pillCenterY - pillHeight / 2f,
                    pillCenterX + pillWidth / 2f,
                    pillCenterY + pillHeight / 2f
                )
                drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, 8.dp.toPx(), 8.dp.toPx(), pillBgPaint)
                drawContext.canvas.nativeCanvas.drawText(pillText, pillCenterX, pillCenterY + 4.dp.toPx(), pillPaint)
            }
        }

        // Bottom X-axis hour ticks
        val xLabelPaint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val step = timeSpan / 4
        for (i in 0..4) {
            val targetTime = minTime + i * step
            val x = (i / 4f) * width
            val dateStr = if (i == 4) "Now" else SimpleDateFormat("H", Locale.getDefault()).format(Date(targetTime))
            drawContext.canvas.nativeCanvas.drawText(
                dateStr,
                x.coerceIn(16.dp.toPx(), width - 16.dp.toPx()),
                height + 16.dp.toPx(),
                xLabelPaint
            )
        }
    }
}

@Composable
fun AppSotRow(app: AppBatteryUsage) {
    val colors = ProStatsColors.current
    val durationFormatted = remember(app.foregroundTimeMs) {
        val mins = app.foregroundTimeMs / 1000 / 60
        val hrs = mins / 60
        val remMins = mins % 60
        if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderColor, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.appName != app.packageName) {
                    Text(
                        text = app.packageName,
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (app.batteryUsagePct / 100f).coerceIn(0f, 1f) },
                    color = colors.accentGreen,
                    trackColor = colors.elevatedSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", app.batteryUsagePct),
                    color = colors.accentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = durationFormatted,
                    color = colors.textPrimary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
