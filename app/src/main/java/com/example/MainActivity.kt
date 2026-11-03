package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.example.ui.components.LiveMapView
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.example.model.ConnectionStatus
import com.example.ui.components.ActiveAlarmDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NetAlertTheme
import com.example.ui.theme.SurfaceDark
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NetAlertTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var hasDismissedAlarmDialogForCurrentEvent by remember { mutableStateOf(false) }

    val serviceState by viewModel.serviceState.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val roomIdInput by viewModel.roomIdInput.collectAsState()
    val selectedRole by viewModel.selectedRole.collectAsState()
    val pingIntervalSec by viewModel.pingIntervalSec.collectAsState()
    val timeoutSec by viewModel.timeoutSec.collectAsState()
    val selectedBroker by viewModel.selectedBroker.collectAsState()
    val isTestingAlarm by viewModel.isTestingAlarm.collectAsState()
    val isShareLocationEnabled by viewModel.isShareLocationEnabled.collectAsState()
    val isLocationChangeAlertEnabled by viewModel.isLocationChangeAlertEnabled.collectAsState()
    val locationAlertThresholdMeters by viewModel.locationAlertThresholdMeters.collectAsState()

    val emergencyPhoneNumber by viewModel.emergencyPhoneNumber.collectAsState()
    val isEmergencySmsEnabled by viewModel.isEmergencySmsEnabled.collectAsState()
    val sendOnLocalInternetLoss by viewModel.sendOnLocalInternetLoss.collectAsState()
    val sendOnRemoteDisconnect by viewModel.sendOnRemoteDisconnect.collectAsState()

    // Request notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Reset dialog dismissal when back online
    LaunchedEffect(serviceState.connectionStatus) {
        if (serviceState.connectionStatus == ConnectionStatus.ONLINE_MONITORING) {
            hasDismissedAlarmDialogForCurrentEvent = false
        }
    }

    val isAlarmActive = serviceState.connectionStatus == ConnectionStatus.DISCONNECTED_ALERT ||
            serviceState.connectionStatus == ConnectionStatus.LOCATION_CHANGED_ALERT ||
            serviceState.isAlarmRinging ||
            serviceState.isLocationAlarmRinging

    val showEmergencyDialog = isAlarmActive && !hasDismissedAlarmDialogForCurrentEvent

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SurfaceDark,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "خانه"
                        )
                    },
                    label = { Text("پایش زنده") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_home_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Map else Icons.Outlined.Map,
                            contentDescription = "نقشه زنده"
                        )
                    },
                    label = { Text("نقشه زنده") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_map_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "تاریخچه"
                        )
                    },
                    label = { Text("تاریخچه") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_history_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "تنظیمات"
                        )
                    },
                    label = { Text("تنظیمات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_settings_tab")
                )
            }
        }
    ) { innerPadding ->
        BackHandler(enabled = selectedTab != 0) {
            selectedTab = 0
        }

        val isAnyAlarmRinging = serviceState.isAlarmRinging || serviceState.isLocationAlarmRinging

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Persistent Emergency Stop Banner across ALL screens when alarm is sounding
            AnimatedVisibility(
                visible = isAnyAlarmRinging,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFFDC2626),
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "آژیر در حال پخش است!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.dismissAlarm(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFDC2626)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("global_sticky_stop_alarm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🛑 قطع فوری صدا",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        serviceState = serviceState,
                        roomIdInput = roomIdInput,
                        selectedRole = selectedRole,
                        isTestingAlarm = isTestingAlarm,
                        isShareLocationEnabled = isShareLocationEnabled,
                        emergencyPhoneNumber = emergencyPhoneNumber,
                        isEmergencySmsEnabled = isEmergencySmsEnabled,
                        onRoomIdChanged = viewModel::onRoomIdChanged,
                        onGenerateNewRoom = viewModel::generateNewRoomCode,
                        onRoleSelected = viewModel::onRoleSelected,
                        onShareLocationToggled = viewModel::setShareLocationEnabled,
                        onStartMonitoring = { viewModel.startMonitoring(context) },
                        onStopMonitoring = { viewModel.stopMonitoring(context) },
                        onDismissAlarm = { viewModel.dismissAlarm(context) },
                        onResetLocationAnchor = { viewModel.resetLocationAnchor(context) },
                        onSimulateDisconnect = { viewModel.simulateDisconnect(context) },
                        onSimulateLocationChange = { viewModel.simulateLocationChange(context) },
                        onToggleTestAlarm = viewModel::toggleTestAlarm,
                        onTriggerEmergencySmsNow = { viewModel.triggerEmergencySmsNow(context) },
                        onNavigateToLiveMap = { selectedTab = 1 }
                    )

                    1 -> LiveMapView(
                        remoteLat = serviceState.remoteLatitude,
                        remoteLng = serviceState.remoteLongitude,
                        remoteDeviceName = serviceState.remoteDeviceName,
                        anchorLat = serviceState.anchorLatitude,
                        anchorLng = serviceState.anchorLongitude,
                        thresholdMeters = locationAlertThresholdMeters,
                        distanceFromAnchor = serviceState.distanceFromAnchorMeters,
                        isLocationAlarmTriggered = serviceState.isLocationAlarmTriggered,
                        onResetAnchor = { viewModel.resetLocationAnchor(context) }
                    )

                    2 -> HistoryScreen(
                        logs = allLogs,
                        onDeleteLog = viewModel::deleteLog,
                        onClearAllLogs = viewModel::clearAllLogs,
                        onExportReport = viewModel::formatHistoryReport
                    )

                    3 -> SettingsScreen(
                        availableBrokers = viewModel.availableBrokers,
                        selectedBroker = selectedBroker,
                        pingIntervalSec = pingIntervalSec,
                        timeoutSec = timeoutSec,
                        isLocationChangeAlertEnabled = isLocationChangeAlertEnabled,
                        locationAlertThresholdMeters = locationAlertThresholdMeters,
                        myDeviceName = viewModel.myDeviceName,
                        myDeviceId = viewModel.myDeviceId,
                        isMonitoringRunning = serviceState.isRunning,
                        emergencyPhoneNumber = emergencyPhoneNumber,
                        isEmergencySmsEnabled = isEmergencySmsEnabled,
                        sendOnLocalInternetLoss = sendOnLocalInternetLoss,
                        sendOnRemoteDisconnect = sendOnRemoteDisconnect,
                        onBrokerSelected = viewModel::onBrokerSelected,
                        onPingIntervalChanged = viewModel::onPingIntervalChanged,
                        onTimeoutChanged = viewModel::onTimeoutChanged,
                        onLocationChangeAlertToggled = viewModel::setLocationChangeAlertEnabled,
                        onLocationAlertThresholdChanged = viewModel::setLocationAlertThresholdMeters,
                        onEmergencyPhoneChanged = viewModel::onEmergencyPhoneNumberChanged,
                        onEmergencySmsToggled = viewModel::setEmergencySmsEnabled,
                        onSendOnLocalLossToggled = viewModel::setSendOnLocalInternetLoss,
                        onSendOnRemoteDisconnectToggled = viewModel::setSendOnRemoteDisconnect,
                        onSendTestSms = {
                            viewModel.sendTestEmergencySms(context) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }

        // Active Emergency / Location Alert Modal
        ActiveAlarmDialog(
            isVisible = showEmergencyDialog,
            status = serviceState.connectionStatus,
            isRinging = serviceState.isAlarmRinging || serviceState.isLocationAlarmRinging,
            reason = serviceState.activeDisconnectReason,
            remoteDeviceName = serviceState.remoteDeviceName,
            startTimeMs = serviceState.activeDisconnectStartTime,
            latitude = serviceState.remoteLatitude,
            longitude = serviceState.remoteLongitude,
            displacementMeters = serviceState.locationChangeDisplacementMeters ?: serviceState.distanceFromAnchorMeters,
            emergencyPhoneNumber = emergencyPhoneNumber,
            isEmergencySmsEnabled = isEmergencySmsEnabled,
            lastEmergencySmsStatus = serviceState.lastEmergencySmsStatus,
            onSendEmergencySms = {
                viewModel.triggerEmergencySmsNow(context)
                Toast.makeText(context, "درخواست ارسال پیامک اضطراری صادر شد", Toast.LENGTH_SHORT).show()
            },
            onMuteAlarm = { viewModel.dismissAlarm(context) },
            onResetAnchor = {
                viewModel.resetLocationAnchor(context)
                hasDismissedAlarmDialogForCurrentEvent = true
            },
            onOpenLiveMap = {
                hasDismissedAlarmDialogForCurrentEvent = true
                selectedTab = 1
            },
            onViewHistory = {
                hasDismissedAlarmDialogForCurrentEvent = true
                selectedTab = 2
            },
            onDismiss = {
                hasDismissedAlarmDialogForCurrentEvent = true
            }
        )
    }
}
