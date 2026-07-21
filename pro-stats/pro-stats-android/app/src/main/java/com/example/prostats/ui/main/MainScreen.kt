package com.example.prostats.ui.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prostats.data.ProcessItem

// Extension helper to convert Drawable to Bitmap for Compose
fun Drawable.toBitmap(): Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            icon.toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (iconBitmap != null) {
        Image(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainScreenUiState,
    onForceStop: (String) -> Unit,
    onFreeze: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortBy by remember { mutableStateOf("CPU") }
    
    val isShizuku = when (uiState) {
        is MainScreenUiState.Success -> uiState.data.firstOrNull()?.isShizukuMode ?: false
        else -> false
    }

    // Set default sort for Basic Mode vs Pro Mode
    LaunchedEffect(isShizuku) {
        sortBy = if (isShizuku) "CPU" else "Time"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Running Processes", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0A0C)
                )
            )
        },
        containerColor = Color(0xFF0A0A0C),
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                MainScreenUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF4ADE80))
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Failed to query processes: ${uiState.throwable.message}", color = Color.White)
                        }
                    }
                }
                is MainScreenUiState.Success -> {
                    val processes = uiState.data

                    // Current Mode Banner
                    ModeBanner(isShizuku)

                    // Sorting Header
                    SortingHeader(
                        isShizuku = isShizuku,
                        selectedSort = sortBy,
                        onSortChange = { sortBy = it }
                    )

                    // Sort Data
                    val sortedList = remember(processes, sortBy) {
                        when (sortBy) {
                            "CPU" -> processes.sortedByDescending { it.cpuUsage }
                            "RAM" -> processes.sortedByDescending { it.ramUsageMb }
                            "Name" -> processes.sortedBy { it.name }
                            "Time" -> processes.sortedByDescending { it.systemTimeForegroundMs }
                            "Battery" -> processes.sortedByDescending { it.batteryUsagePct }
                            else -> processes
                        }
                    }

                    if (sortedList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No processes running", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sortedList, key = { "${it.pid}-${it.packageName}" }) { item ->
                                ProcessRow(
                                    item = item,
                                    sortBy = sortBy,
                                    onForceStop = onForceStop,
                                    onFreeze = onFreeze
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeBanner(isShizuku: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isShizuku) Color(0xFF142B1B) else Color(0xFF2C1E14)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Mode info",
                tint = if (isShizuku) Color(0xFF4ADE80) else Color(0xFFFB923C)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isShizuku) "Pro Mode Active (Shizuku)" else "Basic Mode Active (Usage Access)",
                    color = if (isShizuku) Color(0xFF4ADE80) else Color(0xFFFB923C),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isShizuku) "Displaying real-time CPU/RAM stats. System management commands enabled."
                    else "Real-time CPU/RAM is disabled. To upgrade to Pro Mode, set up Shizuku and relaunch.",
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun SortingHeader(
    isShizuku: Boolean,
    selectedSort: String,
    onSortChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isShizuku) {
            SortTab(title = "Sort by CPU", active = selectedSort == "CPU", onClick = { onSortChange("CPU") })
            SortTab(title = "Sort by RAM", active = selectedSort == "RAM", onClick = { onSortChange("RAM") })
        }
        SortTab(title = "Sort by Screen Time", active = selectedSort == "Time", onClick = { onSortChange("Time") })
        SortTab(title = "Sort by Battery Usage", active = selectedSort == "Battery", onClick = { onSortChange("Battery") })
        SortTab(title = "Sort by Name", active = selectedSort == "Name", onClick = { onSortChange("Name") })
    }
}

@Composable
fun SortTab(title: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF1F1F23) else Color.Transparent,
            contentColor = if (active) Color(0xFF4ADE80) else Color.Gray
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color(0x334ADE80) else Color(0x11FFFFFF)),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun formatSot(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessRow(
    item: ProcessItem,
    sortBy: String,
    onForceStop: (String) -> Unit,
    onFreeze: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                showDialog = true
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444)
                else -> Color(0xFF2C2C2E)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            onFreeze(item.packageName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA78BFA), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Freeze", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onForceStop(item.packageName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Force Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showDialog = true
                }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                AppIcon(
                    packageName = item.packageName,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x11FFFFFF), RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.packageName,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(horizontalAlignment = Alignment.End) {
                    when (sortBy) {
                        "CPU" -> {
                            Text(
                                text = "${item.cpuUsage}% CPU",
                                color = if (item.cpuUsage > 15f) Color(0xFFFB923C) else Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "RAM: ${String.format(java.util.Locale.US, "%.1f", item.ramUsageMb)} MB",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        "RAM" -> {
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", item.ramUsageMb)} MB",
                                color = Color(0xFFFB923C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (item.isShizukuMode) {
                                Text(
                                    text = "CPU: ${item.cpuUsage}%",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        "Time" -> {
                            Text(
                                text = formatSot(item.systemTimeForegroundMs),
                                color = Color(0xFFA78BFA),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Active SOT",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        "Battery" -> {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f%%", item.batteryUsagePct),
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Est. Battery",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        else -> {
                            if (item.isShizukuMode) {
                                Text(
                                    text = "${item.cpuUsage}% CPU",
                                    color = if (item.cpuUsage > 15f) Color(0xFFFB923C) else Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "RAM: ${String.format(java.util.Locale.US, "%.1f", item.ramUsageMb)} MB",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            } else {
                                Text(
                                    text = formatSot(item.systemTimeForegroundMs),
                                    color = Color(0xFFA78BFA),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Est: ${String.format(java.util.Locale.US, "%.1f%%", item.batteryUsagePct)}",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Manage Process") },
            text = { Text("What action would you like to take on '${item.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onForceStop(item.packageName)
                        showDialog = false
                    }
                ) {
                    Text("Force Stop", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onFreeze(item.packageName)
                        showDialog = false
                    }
                ) {
                    Text("Freeze App", color = Color(0xFFA78BFA))
                }
            }
        )
    }
}
