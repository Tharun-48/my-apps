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
import com.example.prostats.data.AppBatteryUsage
import com.example.prostats.data.BatteryTracker
import com.example.prostats.data.HistoryPoint
import com.example.prostats.data.SystemMonitor
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

    // Always since last unplug from full charge
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

    // Screen Off Drain
    val screenOffDrain = remember(lastUnplugTs) {
        if (!hasData) 0f
        else systemMonitor.getScreenOffBatteryDrainPct()
    }

    val screenOffDrainFormatted = remember(screenOffDrain, hasData) {
        if (!hasData) "—"
        else String.format(Locale.US, "%.1f%%", screenOffDrain)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen-on Time & Battery", fontWeight = FontWeight.Bold, color = Color.White) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0A0A0C))
        ) {
            if (!hasData) {
                // No-data placeholder
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("⚡", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No data yet",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Charge your device to 100%, then unplug the charger to start tracking Screen-on Time and battery drain.",
                            color = Color.Gray,
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(20.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "BATTERY CHARGE HISTORY — SINCE UNPLUGGED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
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

                    // Stats summary — SCREEN ON TIME + SCREEN OFF DRAIN
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                                modifier = Modifier.weight(1f).border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("SCREEN ON TIME", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(totalSotFormatted, fontSize = 18.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                                    Text("Since unplugged", fontSize = 10.sp, color = Color(0x88FFFFFF))
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                                modifier = Modifier.weight(1f).border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("SCREEN OFF DRAIN", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(screenOffDrainFormatted, fontSize = 18.sp, color = Color(0xFFFB923C), fontWeight = FontWeight.Bold)
                                    Text("Background drain", fontSize = 10.sp, color = Color(0x88FFFFFF))
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
                            color = Color.Gray,
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
                                        containerColor = if (active) Color(0xFF1F1F23) else Color.Transparent,
                                        contentColor = if (active) Color(0xFF4ADE80) else Color.Gray
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, if (active) Color(0x334ADE80) else Color(0x11FFFFFF)
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
                                Text("No SOT usage stats recorded", color = Color.DarkGray, fontSize = 13.sp)
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
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No history captured yet", color = Color.DarkGray, fontSize = 12.sp)
        }
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.timestamp } }
    val minTime = sortedPoints.first().timestamp
    val maxTime = sortedPoints.last().timestamp
    val timeSpan = (maxTime - minTime).coerceAtLeast(1L)

    val labelFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height - 20.dp.toPx()

        val gridColor = Color(0x0CFFFFFF)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
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
            drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(Color(0x25A78BFA), Color.Transparent)))

            val linePath = Path().apply {
                moveTo(coords[0].x, coords[0].y)
                for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
            }
            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(colors = listOf(Color(0xFFA78BFA), Color(0xFF4ADE80))),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val xLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
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
    val durationFormatted = remember(app.foregroundTimeMs) {
        val mins = app.foregroundTimeMs / 1000 / 60
        val hrs = mins / 60
        val remMins = mins % 60
        if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x0CFFFFFF), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(40.dp).background(Color(0x11FFFFFF), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (app.batteryUsagePct / 100f).coerceIn(0f, 1f),
                    color = Color(0xFF4ADE80),
                    trackColor = Color(0x11FFFFFF),
                    modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.Transparent, RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", app.batteryUsagePct),
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = durationFormatted,
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            val width = icon.intrinsicWidth.coerceAtLeast(1)
            val height = icon.intrinsicHeight.coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (iconBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = iconBitmap,
            contentDescription = "App Icon",
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF2C2C2E), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = packageName.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
