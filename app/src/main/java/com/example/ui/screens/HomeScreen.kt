package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.DeviceRole
import com.example.service.ServiceStateData
import com.example.ui.components.PulseRadarAnimation
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GreenOnline
import com.example.ui.theme.RedOffline
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.util.DeviceUtils
import com.example.util.LocationHelper
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    serviceState: ServiceStateData,
    roomIdInput: String,
    selectedRole: DeviceRole,
    isTestingAlarm: Boolean,
    isShareLocationEnabled: Boolean,
    emergencyPhoneNumber: String = "",
    isEmergencySmsEnabled: Boolean = false,
    onRoomIdChanged: (String) -> Unit,
    onGenerateNewRoom: () -> Unit,
    onRoleSelected: (DeviceRole) -> Unit,
    onShareLocationToggled: (Boolean) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onDismissAlarm: () -> Unit,
    onResetLocationAnchor: () -> Unit,
    onSimulateDisconnect: () -> Unit,
    onSimulateLocationChange: () -> Unit,
    onToggleTestAlarm: () -> Unit,
    onTriggerEmergencySmsNow: () -> Unit = {},
    onNavigateToLiveMap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onShareLocationToggled(granted)
        if (!granted) {
            Toast.makeText(context, "جهت ارسال موقعیت، دسترسی لوکیشن لازم است", Toast.LENGTH_SHORT).show()
        }
    }

    var elapsedHeartbeatSec by remember(serviceState.lastHeartbeatReceivedTime) {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(serviceState.lastHeartbeatReceivedTime, serviceState.isRunning) {
        while (serviceState.isRunning) {
            delay(1000L)
            val lastTime = serviceState.lastHeartbeatReceivedTime
            if (lastTime != null) {
                elapsedHeartbeatSec = ((System.currentTimeMillis() - lastTime) / 1000).coerceAtLeast(0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Alarm Emergency Banner if Active (Disconnect or Location change)
        val isAlarmActive = serviceState.isAlarmRinging || serviceState.isLocationAlarmRinging
        val isLocationAlert = serviceState.isLocationAlarmRinging || serviceState.connectionStatus == ConnectionStatus.LOCATION_CHANGED_ALERT
        val bannerColor = if (isLocationAlert) AmberWarning else RedOffline

        AnimatedVisibility(
            visible = isAlarmActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(2.dp, bannerColor, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isLocationAlert) Icons.Default.MyLocation else Icons.Default.Warning,
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isLocationAlert) "هشدار جابجایی لوکیشن!" else "هشدار قطع ارتباط اینترنت!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = bannerColor
                                )
                            )
                            Text(
                                text = serviceState.activeDisconnectReason ?: "رویداد اضطراری",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = onDismissAlarm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = bannerColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier.testTag("banner_mute_alarm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🛑 قطع فوری صدا",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (isEmergencySmsEnabled && emergencyPhoneNumber.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onTriggerEmergencySmsNow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("banner_send_emergency_sms_now_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📩 ارسال فوری پیامک موقعیت به $emergencyPhoneNumber",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Emergency SMS Dispatch Status Banner
        if (serviceState.lastEmergencySmsStatus != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2537)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "گزارش ارسال پیامک اضطراری",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = serviceState.lastEmergencySmsStatus ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                        )
                    }
                }
            }
        }

        // Silenced Alarm Confirmation Banner (when user has muted alarm but disconnection still persists)
        val isMutedIncident = serviceState.isAlarmSilenced &&
                !isAlarmActive &&
                (serviceState.connectionStatus == ConnectionStatus.MUTED_ALERT ||
                 serviceState.connectionStatus == ConnectionStatus.DISCONNECTED_ALERT)

        AnimatedVisibility(
            visible = isMutedIncident,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "صدای آژیر قطع شد",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "ارتباط هنوز قطع است ولی سروصدای تکراری تا برقراری مجدد ایجاد نخواهد شد.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                        )
                    }
                }
            }
        }

        // Radar & Live Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulse Animation
                PulseRadarAnimation(
                    status = serviceState.connectionStatus,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Connection Status Chip
                val (statusText, statusColor) = when (serviceState.connectionStatus) {
                    ConnectionStatus.ONLINE_MONITORING -> Pair("🟢 آنلاین و در حال پایش مستمر", GreenOnline)
                    ConnectionStatus.DISCONNECTED_ALERT -> Pair("🔴 اینترنت دستگاه مقابل قطع شد!", RedOffline)
                    ConnectionStatus.LOCATION_CHANGED_ALERT -> Pair("📍 هشدار: تغییر لوکیشن دستگاه مقابل!", AmberWarning)
                    ConnectionStatus.MUTED_ALERT -> Pair("⚠️ ارتباط قطع است (صدا خاموش)", AmberWarning)
                    ConnectionStatus.CONNECTING -> Pair("🟡 در حال اتصال به سرور واسط...", AmberWarning)
                    ConnectionStatus.RECONNECTING -> Pair("🟡 در حال برقراری مجدد تماس...", AmberWarning)
                    ConnectionStatus.IDLE -> Pair("⚪ پایش غیرفعال است", Color.Gray)
                }

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )
                }

                if (isEmergencySmsEnabled && emergencyPhoneNumber.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyanAccent.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "پشتیبان پیامک اضطراری فعال: $emergencyPhoneNumber",
                                style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent)
                            )
                        }
                    }
                }

                if (serviceState.isRunning) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Remote Device Telemetry Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCardElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Remote Device Name
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "دستگاه مقابل",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = serviceState.remoteDeviceName ?: "در انتظار تماس...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                        }

                        // Battery
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "باتری",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (serviceState.remoteBatteryPercentage ?: 0 > 20)
                                        Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (serviceState.remoteBatteryPercentage ?: 0 > 20) GreenOnline else RedOffline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (serviceState.remoteBatteryPercentage != null)
                                        "${serviceState.remoteBatteryPercentage}%" else "--",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                )
                            }
                        }

                        // Network
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "نوع شبکه",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (serviceState.remoteNetworkType == "WIFI")
                                        Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = serviceState.remoteNetworkType ?: "--",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                )
                            }
                        }

                        // Latency / Ping
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "پینگ",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = if (serviceState.latencyMs != null) "${serviceState.latencyMs} ms" else "--",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyanAccent
                                )
                            )
                        }
                    }

                    // Remote Location Telemetry Row if available
                    if (serviceState.remoteLatitude != null && serviceState.remoteLongitude != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyanAccent.copy(alpha = 0.12f))
                                .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "موقعیت زنده دستگاه مقابل:",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                                        )
                                        Text(
                                            text = LocationHelper.formatCoordinates(
                                                serviceState.remoteLatitude,
                                                serviceState.remoteLongitude
                                            ),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = onNavigateToLiveMap,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CyanAccent)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = null,
                                            tint = Slate950,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("نقشه زنده", fontSize = 12.sp, color = Slate950, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Geofence / Distance from Anchor Display
                            if (serviceState.distanceFromAnchorMeters != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NearMe,
                                            contentDescription = null,
                                            tint = if (serviceState.isLocationAlarmTriggered) AmberWarning else GreenOnline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "فاصله از موقعیت مبنا: ${LocationHelper.formatDistance(serviceState.distanceFromAnchorMeters)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (serviceState.isLocationAlarmTriggered) AmberWarning else Color.LightGray
                                            )
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onResetLocationAnchor,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockReset,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تنظیم مجدد مبنا", fontSize = 10.sp, color = CyanAccent)
                                    }
                                }
                            }
                        }
                    }

                    if (serviceState.lastHeartbeatReceivedTime != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "آخرین ضربان دریافتی: $elapsedHeartbeatSec ثانیه قبل",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                        )
                    }
                }
            }
        }

        // Room ID & Pairing Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "🔑 شناسه اتصال و جفت‌سازی (Room ID)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "این کد را در هر دو گوشی یکسان وارد کنید تا به یکدیگر متصل شوند.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = roomIdInput,
                        onValueChange = onRoomIdChanged,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_id_input"),
                        enabled = !serviceState.isRunning,
                        placeholder = { Text("مثال: 789-214") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onGenerateNewRoom,
                        enabled = !serviceState.isRunning,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCardElevated)
                            .testTag("generate_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "تولید کد جدید",
                            tint = CyanAccent
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("NetAlert Room ID", roomIdInput)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "کد اتاق کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCardElevated)
                            .testTag("copy_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "کپی کد",
                            tint = CyanAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Role Selection
                Text(
                    text = "نقش این دستگاه در ارتباط:",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                DeviceRole.values().forEach { role ->
                    val isSelected = selectedRole == role
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !serviceState.isRunning) { onRoleSelected(role) }
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) CyanAccent else Slate800,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        color = if (isSelected) CyanAccent.copy(alpha = 0.12f) else SurfaceCardElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = if (isSelected) CyanAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = role.titleFa,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color.LightGray
                                    )
                                )
                                Text(
                                    text = role.descriptionFa,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Location Sharing Toggle Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp)),
                    color = SurfaceCardElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = if (isShareLocationEnabled) CyanAccent else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ارسال موقعیت مکانی (GPS)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "مختصات این دستگاه در پکت ضربان ارسال شود تا آخرین محل و جابجایی آن ثبت گردد.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                )
                            }
                        }

                        Switch(
                            checked = isShareLocationEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        onShareLocationToggled(true)
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                } else {
                                    onShareLocationToggled(false)
                                }
                            },
                            enabled = !serviceState.isRunning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyanAccent,
                                checkedTrackColor = CyanAccent.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        // Primary Control Button (Start / Stop)
        if (!serviceState.isRunning) {
            Button(
                onClick = onStartMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_monitoring_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Slate950
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "شروع پایش ارتباط (Start)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Slate950
                    )
                )
            }
        } else {
            Button(
                onClick = onStopMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("stop_monitoring_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RedOffline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "توقف پایش ارتباط (Stop)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Test Tools
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🛠️ ابزارهای آزمایش و شبیه‌سازی",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onToggleTestAlarm,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_alarm_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isTestingAlarm) AmberWarning else Color(0xFF293548)
                        )
                    ) {
                        Icon(
                            imageVector = if (isTestingAlarm) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTestingAlarm) "توقف آژیر" else "تست آژیر",
                            fontSize = 11.sp
                        )
                    }

                    FilledTonalButton(
                        onClick = onSimulateDisconnect,
                        enabled = serviceState.isRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulate_disconnect_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF3B2931))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RedOffline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست قطعی", fontSize = 11.sp, color = RedOffline)
                    }

                    FilledTonalButton(
                        onClick = onSimulateLocationChange,
                        enabled = serviceState.isRunning,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("simulate_location_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF3A311F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست جابجایی", fontSize = 11.sp, color = AmberWarning)
                    }
                }
            }
        }
    }
}
