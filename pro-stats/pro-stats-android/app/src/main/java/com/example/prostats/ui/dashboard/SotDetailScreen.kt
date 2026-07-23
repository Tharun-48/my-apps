package com.example.prostats.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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

    // Always since last unplug from full charge (>= 90%)
    val lastUnplugTs = remember { BatteryTracker.getLastUnplugFromFullTimestamp(context) }
    val hasData = lastUnplugTs > 0L

    // Sort state for app list
    var appSort by remember { mutableStateOf("Time") } // Time | Battery | Name

    // History points since unplug
    val points = remember(lastUnplugTs) {
        BatteryTracker.getHistorySinceLastCharge(context)
    }

    // App usage list
    val rawAppList = remember(lastUnplugTs) {
        if (!hasData) emptyList()
        else {
            val now = System.currentTimeMillis()
            systemMonitor.getAppBatteryUsageList(lastUnplugTs, now)
        }
    }

    val appUsageList = remember(rawAppList, appSort) {
        when (appSort) {
            "Battery" -> rawAppList.sortedByDescending { it.batteryUsagePct }
            "Name" -> rawAppList.sortedBy { it.appName }
            else -> rawAppList.sortedByDescending { it.foregroundTimeMs }
        }
    }

    // Screen On Time
    val totalSotMs = remember(lastUnplugTs) {
        if (!hasData) 0L
        else {
            val now = System.currentTimeMillis()
            systemMonitor.getScreenOnTimeMs(lastUnplugTs, now)
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
    val screenOffMs = remember(lastUnplugTs) {
        if (!hasData) 0L
        else systemMonitor.getScreenOffTimeSinceLastChargeMs()
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
    val avgDailySot = remember {
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
    val wakelocks = remember { systemMonitor.getWakelockInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen-on Time & Battery", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
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
                            "No data yet",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Charge your device to 90% or above, then unplug the charger to start tracking Screen-on Time and battery drain.",
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
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Graph card
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderColor, RoundedCornerShape(20.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "BATTERY CHARGE HISTORY — SINCE UNPLUGGED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                BatteryGraph(
                                    points = points,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Stats summary — SCREEN ON TIME + SCREEN OFF DRAIN + AVG DAILY SOT
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier.weight(1f).border(1.dp, colors.borderColor, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("SCREEN ON TIME", fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(totalSotFormatted, fontSize = 18.sp, color = colors.accentPurple, fontWeight = FontWeight.Bold)
                                    Text("Since unplugged", fontSize = 10.sp, color = colors.textSecondary.copy(alpha = 0.6f))
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier.weight(1f).border(1.dp, colors.borderColor, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("SCREEN OFF TIME", fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(screenOffTimeFormatted, fontSize = 18.sp, color = colors.accentOrange, fontWeight = FontWeight.Bold)
                                    Text("Time in background", fontSize = 10.sp, color = colors.textSecondary.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }

                    // Average Daily SOT card
                    if (avgDailySot > 0) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderColor, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("AVG DAILY SCREEN-ON TIME", fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Based on last 7 days", fontSize = 10.sp, color = colors.textSecondary.copy(alpha = 0.6f))
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
                                text = "TOP WAKELOCKS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                                letterSpacing = 1.sp
                            )
                        }
                        items(wakelocks.take(10)) { wl ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                                modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
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
                                            text = "Count: ${wl.count}",
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val durationMins = wl.totalDurationMs / 1000 / 60
                                        val durationText = if (durationMins > 60) "${durationMins/60}h ${durationMins%60}m" else "${durationMins}m"
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
                        Text(
                            text = "APP BATTERY CONSUMPTION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Time" to "Screen Time", "Battery" to "Battery", "Name" to "Name").forEach { (key, label) ->
                                val active = appSort == key
                                Button(
                                    onClick = { appSort = key },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) colors.cardSurface else Color.Transparent,
                                        contentColor = if (active) colors.accentGreen else colors.textSecondary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, if (active) colors.accentGreen.copy(alpha = 0.2f) else colors.borderColor
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    if (appUsageList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No SOT usage stats recorded", color = colors.textSecondary, fontSize = 13.sp)
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
    val gridColor = if (colors.isDark) Color(0x0CFFFFFF) else Color(0x0C000000)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height - 20.dp.toPx()

        val paint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        for (pct in listOf(25, 50, 75, 100)) {
            val y = height * (1f - pct / 100f)
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$pct%", width - 4.dp.toPx(), y - 4.dp.toPx(), paint)
        }

        val coords = sortedPoints.map { pt ->
            val xRatio = (pt.timestamp - minTime).toFloat() / timeSpan
            val yRatio = pt.batteryLevel / 100f
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
            drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(colors.accentPurple.copy(alpha = 0.15f), Color.Transparent)))

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
        for (i in 0..4) {
            val targetTime = minTime + i * step
            val x = (i / 4f) * width
            val dateStr = labelFormat.format(Date(targetTime))
            drawContext.canvas.nativeCanvas.drawText(
                dateStr,
                x.coerceIn(24.dp.toPx(), width - 24.dp.toPx()),
                height + 15.dp.toPx(),
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderColor.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(40.dp).background(colors.borderColor.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
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
                Text(
                    text = app.packageName,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (app.batteryUsagePct / 100f).coerceIn(0f, 1f),
                    color = colors.accentGreen,
                    trackColor = if (colors.isDark) Color(0x11FFFFFF) else Color(0x11000000),
                    modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.Transparent, RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
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
