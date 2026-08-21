package com.example.prostats.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    // Time range toggle state
    var timeRange by remember { mutableStateOf("Since Charge") } // "Since Charge", "24h", or "7d"

    val startTime = remember(lastUnplugTs, timeRange, refreshTick) {
        val now = System.currentTimeMillis()
        when (timeRange) {
            "Since Charge" -> lastUnplugTs
            "7d" -> now - 7 * 24 * 60 * 60 * 1000L
            else -> now - 24 * 60 * 60 * 1000L
        }
    }

    // History points
    val points = remember(startTime, refreshTick, timeRange) {
        when (timeRange) {
            "Since Charge" -> BatteryTracker.getHistorySinceLastCharge(context)
            "7d" -> BatteryTracker.getHistory7d(context)
            else -> BatteryTracker.getHistory24h(context)
        }
    }

    // Screen On Time
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

    // App usage list
    val rawAppList by produceState(initialValue = emptyList<com.example.prostats.data.AppBatteryUsage>(), key1 = startTime, key2 = refreshTick, key3 = hasData) {
        if (!hasData) {
            value = emptyList()
        } else {
            val now = System.currentTimeMillis()
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                systemMonitor.getAppBatteryUsageList(startTime, now)
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "BATTERY LEVEL TREND",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary,
                                        letterSpacing = 1.sp
                                    )
                                    // Time range segmented pill
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .background(colors.elevatedSurface, RoundedCornerShape(12.dp))
                                            .border(1.dp, colors.borderColorSubtle, RoundedCornerShape(12.dp))
                                            .padding(3.dp)
                                    ) {
                                        listOf("Since Charge", "24h", "7d").forEach { range ->
                                            val active = timeRange == range
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (active) colors.accentPurple.copy(alpha = 0.2f) else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        width = if (active) 1.dp else 0.dp,
                                                        color = if (active) colors.accentPurple.copy(alpha = 0.5f) else Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    .clickable { timeRange = range },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = range,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (active) colors.accentPurple else colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Baseline: $startDateFormatted", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        Text("Auto-resets on unplug at ≥${BatteryTracker.getTargetResetBatteryLevel(context)}%", fontSize = 10.sp, color = colors.textTertiary)
                                    }
                                    TextButton(
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            BatteryTracker.updateLastUnplugFromFullTimestamp(context, now)
                                            lastUnplugTs = now
                                        }
                                    ) {
                                        Text("Reset Cycle", fontSize = 11.sp, color = colors.accentPurple, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                BatteryGraph(
                                    points = points,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                )
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
                                "7d" -> "Last 7 days active"
                                "24h" -> "Last 24 hours active"
                                else -> "Since disconnected"
                            }
                            val screenOffSubtitle = when (timeRange) {
                                "7d" -> "Last 7 days idle"
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

                    // App list header + sort tabs
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "APP BATTERY DRAIN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = when (timeRange) {
                                    "7d" -> "Last 7 days"
                                    "24h" -> "Last 24 hours"
                                    else -> "Since charge"
                                },
                                fontSize = 11.sp,
                                color = colors.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Time" to "Active Time", "Battery" to "Drain %", "Name" to "App Name").forEach { (key, label) ->
                                val active = appSort == key
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (active) colors.accentGreen.copy(alpha = 0.16f) else colors.elevatedSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (active) colors.accentGreen.copy(alpha = 0.4f) else colors.borderColorSubtle,
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
                                        color = if (active) colors.accentGreen else colors.textSecondary
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
                                Text("No application usage captured in this interval", color = colors.textSecondary, fontSize = 13.sp)
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

@Composable
fun BatteryGraph(
    points: List<HistoryPoint>,
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
    val timeSpan = (maxTime - minTime).coerceAtLeast(1L)

    val labelFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val labelColor = if (colors.isDark) android.graphics.Color.GRAY else android.graphics.Color.DKGRAY
    val gridColor = if (colors.isDark) Color(0x12FFFFFF) else Color(0x10000000)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height - 22.dp.toPx()

        val paint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        // Draw horizontal grid lines & labels
        for (pct in listOf(25, 50, 75, 100)) {
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
                    colors = listOf(colors.accentPurple.copy(alpha = 0.22f), Color.Transparent)
                )
            )

            val linePath = Path().apply {
                moveTo(coords[0].x, coords[0].y)
                for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
            }
            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(colors = listOf(colors.accentPurple, colors.accentGreen)),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val xLabelPaint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val step = timeSpan / 4
        val isMultiDay = timeSpan > 24 * 60 * 60 * 1000L
        val dynamicFormat = if (isMultiDay) SimpleDateFormat("dd/MM", Locale.getDefault()) else labelFormat
        for (i in 0..4) {
            val targetTime = minTime + i * step
            val x = (i / 4f) * width
            val dateStr = dynamicFormat.format(Date(targetTime))
            drawContext.canvas.nativeCanvas.drawText(
                dateStr,
                x.coerceIn(24.dp.toPx(), width - 24.dp.toPx()),
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
