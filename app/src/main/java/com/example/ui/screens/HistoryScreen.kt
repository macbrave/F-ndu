package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import com.example.util.LocationHelper
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DisconnectLogEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenOnline
import com.example.ui.theme.RedOffline
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.util.DeviceUtils

enum class HistoryFilter { ALL, TODAY, LONG_DOWNTIME }

@Composable
fun HistoryScreen(
    logs: List<DisconnectLogEntity>,
    onDeleteLog: (DisconnectLogEntity) -> Unit,
    onClearAllLogs: () -> Unit,
    onExportReport: () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    // Filtered logs
    val filteredLogs by remember(logs, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                HistoryFilter.ALL -> logs
                HistoryFilter.TODAY -> {
                    val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    logs.filter { it.startTime >= oneDayAgo }
                }
                HistoryFilter.LONG_DOWNTIME -> {
                    logs.filter { (it.durationSeconds ?: 0L) >= 60L }
                }
            }
        }
    }

    // Metrics calculations
    val totalDisconnects = logs.size
    val totalDowntimeSec = remember(logs) {
        logs.sumOf { it.durationSeconds ?: 0L }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header with export and clear buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📋 تاریخچه قطعی‌های اینترنت",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "ثبت دقیق زمان‌بندی و علل قطع ارتباط",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }

            Row {
                IconButton(
                    onClick = {
                        val report = onExportReport()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "گزارش قطعی ارتباط NetAlert")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری گزارش"))
                    },
                    modifier = Modifier.testTag("export_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "اشتراک‌گذاری گزارش",
                        tint = CyanAccent
                    )
                }

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "پاکسازی همه لاگ‌ها",
                            tint = RedOffline
                        )
                    }
                }
            }
        }

        // Summary Metric Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "تعداد رخدادها",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalDisconnects مورد",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (totalDisconnects > 0) RedOffline else GreenOnline
                        )
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1.3f),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "مجموع زمان قطعی",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DeviceUtils.formatDuration(totalDowntimeSec),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    )
                }
            }
        }

        // Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == HistoryFilter.ALL,
                onClick = { selectedFilter = HistoryFilter.ALL },
                label = { Text("همه موارد (${logs.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                    selectedLabelColor = CyanAccent
                )
            )
            FilterChip(
                selected = selectedFilter == HistoryFilter.TODAY,
                onClick = { selectedFilter = HistoryFilter.TODAY },
                label = { Text("۲۴ ساعت گذشته") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                    selectedLabelColor = CyanAccent
                )
            )
            FilterChip(
                selected = selectedFilter == HistoryFilter.LONG_DOWNTIME,
                onClick = { selectedFilter = HistoryFilter.LONG_DOWNTIME },
                label = { Text("بیش از ۱ دقیقه") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                    selectedLabelColor = CyanAccent
                )
            )
        }

        // List or Empty State
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GreenOnline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (logs.isEmpty()) "هیچ قطعی اینترنتی ثبت نشده است!" else "هیچ موردی مطابق با فیلتر یافت نشد",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "با قطع شدن اینترنت دستگاه طرف مقابل، جزئیات زمان و علت اینجا ثبت می‌شود.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredLogs, key = { it.id }) { logItem ->
                    DisconnectLogCard(
                        log = logItem,
                        onDelete = { onDeleteLog(logItem) }
                    )
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("حذف تمام لاگ‌ها") },
            text = { Text("آیا از پاک کردن کامل تاریخچه قطعی‌ها اطمینان دارید؟ این عمل غیرقابل بازگشت است.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllLogs()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedOffline)
                ) {
                    Text("بله، پاک شود")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun DisconnectLogCard(
    log: DisconnectLogEntity,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Bar: Room + Device + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RedOffline.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RedOffline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "اتاق: ${log.roomId}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = RedOffline
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.remoteDeviceName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف لاگ",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timestamps & Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "زمان شروع قطعی:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                    )
                    Text(
                        text = DeviceUtils.formatTimestamp(log.startTime),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مدت زمان قطع بودن:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                    )
                    Text(
                        text = if (log.durationSeconds != null)
                            DeviceUtils.formatDuration(log.durationSeconds) else "ادامه دارد...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (log.durationSeconds != null) CyanAccent else RedOffline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason and Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "علت: ${log.reason}",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCBD5E1)),
                    modifier = Modifier.weight(1f)
                )

                if (log.batteryAtDisconnect != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${log.batteryAtDisconnect}%",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                        )
                    }
                }
            }

            // Location coordinates and Map button if available
            if (log.latitudeAtDisconnect != null && log.longitudeAtDisconnect != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanAccent.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = LocationHelper.formatCoordinates(
                                log.latitudeAtDisconnect,
                                log.longitudeAtDisconnect
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            LocationHelper.openMapIntent(
                                context,
                                log.latitudeAtDisconnect,
                                log.longitudeAtDisconnect,
                                "محل قطعی ${log.remoteDeviceName}"
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CyanAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFF090D16),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نقشه", fontSize = 11.sp, color = Color(0xFF090D16), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
