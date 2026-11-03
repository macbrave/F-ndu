package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NetAlertApp
import com.example.R
import com.example.data.local.DisconnectLogEntity
import com.example.model.ConnectionStatus
import com.example.model.DeviceRole
import com.example.model.HeartbeatPayload
import com.example.network.MqttManager
import com.example.util.DeviceUtils
import com.example.util.EmergencySmsHelper
import com.example.util.LocationHelper
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ServiceStateData(
    val isRunning: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val roomId: String = "",
    val role: DeviceRole = DeviceRole.MONITOR_AND_ALARM,
    val isBrokerConnected: Boolean = false,
    val isAlarmRinging: Boolean = false,
    val isLocationAlarmRinging: Boolean = false,
    val isAlarmSilenced: Boolean = false,
    val lastHeartbeatReceivedTime: Long? = null,
    val lastHeartbeatSentTime: Long? = null,
    val remoteDeviceName: String? = null,
    val remoteDeviceId: String? = null,
    val remoteBatteryPercentage: Int? = null,
    val remoteNetworkType: String? = null,
    val activeDisconnectReason: String? = null,
    val activeDisconnectStartTime: Long? = null,
    val latencyMs: Long? = null,
    val remoteLatitude: Double? = null,
    val remoteLongitude: Double? = null,
    val remoteLocationAccuracy: Float? = null,
    // Baseline anchor and distance tracking
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null,
    val distanceFromAnchorMeters: Double? = null,
    val locationChangeDisplacementMeters: Double? = null,
    val isLocationAlarmTriggered: Boolean = false,
    // Offline emergency SMS status
    val isEmergencySmsEnabled: Boolean = false,
    val emergencyPhoneNumber: String = "",
    val lastEmergencySmsTime: Long? = null,
    val lastEmergencySmsStatus: String? = null
)

class ConnectionMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var watchdogJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var mqttManager: MqttManager
    private lateinit var alarmPlayer: AlarmPlayer
    private lateinit var networkMonitor: NetworkMonitor

    private var activeRoomId: String = ""
    private var currentRole: DeviceRole = DeviceRole.MONITOR_AND_ALARM
    private var myDeviceId: String = ""
    private var myDeviceName: String = ""
    private var pingIntervalSec: Int = 3
    private var timeoutSec: Int = 9
    private var shareLocation: Boolean = false
    private var locationAlertThresholdMeters: Int = 50
    private var isLocationAlertEnabled: Boolean = true
    private var currentActiveLogId: Long? = null
    private var seqNumber: Long = 0
    private var lastKnownLocalLocation: Pair<Double, Double>? = null

    // Tracking the anchor position of the remote device
    private var initialRemoteLatitude: Double? = null
    private var initialRemoteLongitude: Double? = null
    private var hasTriggeredLocationAlarm: Boolean = false

    // Latch flags to ensure once silenced, alarm does not loop or re-sound repeatedly
    private var isDisconnectAlarmSilenced: Boolean = false
    private var isLocationAlarmSilenced: Boolean = false

    // Offline emergency SMS configuration
    private var emergencyPhoneNumber: String = ""
    private var isEmergencySmsEnabled: Boolean = false
    private var sendOnLocalInternetLoss: Boolean = true
    private var sendOnRemoteDisconnect: Boolean = true
    private var hasSentSmsForCurrentIncident: Boolean = false

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager()
        alarmPlayer = AlarmPlayer(this)
        networkMonitor = NetworkMonitor(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NetAlert::MonitorLock")

        observeMqtt()
    }

    private fun observeMqtt() {
        serviceScope.launch {
            mqttManager.isConnected.collect { connected ->
                _state.update { it.copy(isBrokerConnected = connected) }
                if (!connected && _state.value.isRunning && !_state.value.isAlarmRinging && !_state.value.isLocationAlarmRinging) {
                    updateForegroundNotification("در حال اتصال مجدد به سرور...")
                }
            }
        }

        serviceScope.launch {
            mqttManager.incomingHeartbeats.collect { payload ->
                handleIncomingHeartbeat(payload)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: "DEFAULT"
                val roleName = intent.getStringExtra(EXTRA_ROLE) ?: DeviceRole.MONITOR_AND_ALARM.name
                val role = runCatching { DeviceRole.valueOf(roleName) }.getOrDefault(DeviceRole.MONITOR_AND_ALARM)
                pingIntervalSec = intent.getIntExtra(EXTRA_PING_INTERVAL_SEC, 3)
                timeoutSec = intent.getIntExtra(EXTRA_TIMEOUT_SEC, 9)
                shareLocation = intent.getBooleanExtra(EXTRA_SHARE_LOCATION, false)
                isLocationAlertEnabled = intent.getBooleanExtra(EXTRA_LOCATION_ALERT_ENABLED, true)
                locationAlertThresholdMeters = intent.getIntExtra(EXTRA_LOCATION_ALERT_THRESHOLD, 50)
                myDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: DeviceUtils.getDeviceDisplayName()
                myDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: DeviceUtils.getDeviceDisplayName()
                val brokerUrl = intent.getStringExtra(EXTRA_BROKER_URL) ?: mqttManager.publicBrokers[0]

                emergencyPhoneNumber = intent.getStringExtra(EXTRA_EMERGENCY_PHONE_NUMBER) ?: ""
                isEmergencySmsEnabled = intent.getBooleanExtra(EXTRA_EMERGENCY_SMS_ENABLED, false)
                sendOnLocalInternetLoss = intent.getBooleanExtra(EXTRA_SEND_ON_LOCAL_INTERNET_LOSS, true)
                sendOnRemoteDisconnect = intent.getBooleanExtra(EXTRA_SEND_ON_REMOTE_DISCONNECT, true)

                startMonitoring(roomId, role, brokerUrl)
            }
            ACTION_STOP -> stopMonitoring()
            ACTION_DISMISS_ALARM -> dismissAlarm()
            ACTION_RESET_LOCATION_ANCHOR -> resetLocationAnchor()
            ACTION_SIMULATE_DISCONNECT -> simulateDisconnect()
            ACTION_SIMULATE_LOCATION_CHANGE -> simulateLocationChange()
            ACTION_SEND_EMERGENCY_SMS_NOW -> {
                val target = intent.getStringExtra(EXTRA_EMERGENCY_PHONE_NUMBER) ?: emergencyPhoneNumber
                sendEmergencySms(
                    reason = "ارسال فوری موقعیت اضطراری به درخواست کاربر",
                    targetNumber = target,
                    isLocalDevice = true
                )
            }
        }
        return START_STICKY
    }

    private fun startMonitoring(roomId: String, role: DeviceRole, brokerUrl: String) {
        activeRoomId = roomId.trim().uppercase()
        currentRole = role
        initialRemoteLatitude = null
        initialRemoteLongitude = null
        hasTriggeredLocationAlarm = false
        isDisconnectAlarmSilenced = false
        isLocationAlarmSilenced = false
        hasSentSmsForCurrentIncident = false

        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard

        _state.update {
            it.copy(
                isRunning = true,
                connectionStatus = ConnectionStatus.CONNECTING,
                roomId = activeRoomId,
                role = role,
                isAlarmRinging = false,
                isLocationAlarmRinging = false,
                isAlarmSilenced = false,
                lastHeartbeatReceivedTime = null,
                lastHeartbeatSentTime = null,
                activeDisconnectReason = null,
                activeDisconnectStartTime = null,
                remoteLatitude = null,
                remoteLongitude = null,
                anchorLatitude = null,
                anchorLongitude = null,
                distanceFromAnchorMeters = null,
                locationChangeDisplacementMeters = null,
                isLocationAlarmTriggered = false,
                isEmergencySmsEnabled = isEmergencySmsEnabled,
                emergencyPhoneNumber = emergencyPhoneNumber
            )
        }

        networkMonitor.startMonitoring {
            onLocalInternetLost()
        }

        startInForeground()

        mqttManager.connect(
            roomId = activeRoomId,
            deviceId = myDeviceId,
            deviceName = myDeviceName,
            brokerUrl = brokerUrl,
            onConnected = {
                _state.update { it.copy(connectionStatus = ConnectionStatus.ONLINE_MONITORING) }
                updateForegroundNotification("پایش فعال است - اتاق $activeRoomId")
            },
            onError = { err ->
                Log.e("MonitorService", "Broker error: $err")
            }
        )

        // Launch Heartbeat sender if role requires it
        if (role == DeviceRole.MONITORED_SENDER || role == DeviceRole.MUTUAL) {
            startHeartbeatSender()
        }

        // Launch Watchdog if role requires it
        if (role == DeviceRole.MONITOR_AND_ALARM || role == DeviceRole.MUTUAL) {
            startWatchdog()
        }
    }

    private fun startInForeground() {
        val notification = buildForegroundNotification("در حال اتصال به اتاق $activeRoomId...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && shareLocation) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID_FOREGROUND, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun startHeartbeatSender() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                sendHeartbeat()
                delay(pingIntervalSec * 1000L)
            }
        }
    }

    private suspend fun sendHeartbeat() {
        val (battery, isCharging) = DeviceUtils.getBatteryInfo(this)
        val networkType = DeviceUtils.getNetworkType(this)
        val now = System.currentTimeMillis()

        var lat: Double? = null
        var lng: Double? = null

        if (shareLocation) {
            val loc = LocationHelper.getCurrentLocation(this) ?: lastKnownLocalLocation
            if (loc != null) {
                lat = loc.first
                lng = loc.second
                lastKnownLocalLocation = loc
            }
        }

        val payload = HeartbeatPayload(
            deviceId = myDeviceId,
            deviceName = myDeviceName,
            role = currentRole.name,
            timestamp = now,
            sequenceNumber = ++seqNumber,
            batteryPercentage = battery,
            isCharging = isCharging,
            networkType = networkType,
            isEmergencyWill = false,
            latitude = lat,
            longitude = lng
        )

        mqttManager.publishHeartbeat(payload) { success ->
            if (success) {
                _state.update { it.copy(lastHeartbeatSentTime = now) }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val currentState = _state.value
                val lastReceived = currentState.lastHeartbeatReceivedTime

                // Only trigger alarm if not already ringing AND user has not silenced this disconnect incident
                if (lastReceived != null && !currentState.isAlarmRinging && !isDisconnectAlarmSilenced) {
                    val elapsed = System.currentTimeMillis() - lastReceived
                    if (elapsed > (timeoutSec * 1000L)) {
                        triggerDisconnectionAlarm(
                            reason = "عدم دریافت ضربان (تایم‌اوت ${timeoutSec} ثانیه‌ای)",
                            remoteName = currentState.remoteDeviceName ?: "دستگاه پایش‌شده",
                            remoteId = currentState.remoteDeviceId ?: "unknown"
                        )
                    }
                }
            }
        }
    }

    private fun handleIncomingHeartbeat(payload: HeartbeatPayload) {
        // Ignore self messages
        if (payload.deviceId == myDeviceId) return

        val now = System.currentTimeMillis()

        if (payload.isEmergencyWill) {
            // Broker notified us that target unexpectedly dropped connection!
            triggerDisconnectionAlarm(
                reason = "قطع ناگهانی اینترنت دستگاه مقابل (شناسایی توسط بروکر Last-Will)",
                remoteName = payload.deviceName,
                remoteId = payload.deviceId
            )
            return
        }

        val latency = if (now >= payload.timestamp) (now - payload.timestamp) else null

        // If we were in disconnect alarm or muted state and device came back online!
        if (_state.value.connectionStatus == ConnectionStatus.DISCONNECTED_ALERT || _state.value.connectionStatus == ConnectionStatus.MUTED_ALERT) {
            handleReconnection(payload)
        }

        // Check for Location Change Detection
        var currentDistance: Double? = null
        var shouldTriggerLocationAlarm = false
        var displacement = 0.0

        if (payload.latitude != null && payload.longitude != null) {
            if (initialRemoteLatitude == null || initialRemoteLongitude == null) {
                // First valid location received — set as anchor
                initialRemoteLatitude = payload.latitude
                initialRemoteLongitude = payload.longitude
                currentDistance = 0.0
            } else {
                currentDistance = LocationHelper.calculateDistanceMeters(
                    initialRemoteLatitude!!,
                    initialRemoteLongitude!!,
                    payload.latitude,
                    payload.longitude
                )

                if (isLocationAlertEnabled &&
                    (currentRole == DeviceRole.MONITOR_AND_ALARM || currentRole == DeviceRole.MUTUAL) &&
                    currentDistance >= locationAlertThresholdMeters &&
                    !hasTriggeredLocationAlarm &&
                    !isLocationAlarmSilenced
                ) {
                    shouldTriggerLocationAlarm = true
                    displacement = currentDistance
                }
            }
        }

        _state.update {
            it.copy(
                connectionStatus = if (shouldTriggerLocationAlarm || (it.connectionStatus == ConnectionStatus.LOCATION_CHANGED_ALERT && it.isLocationAlarmRinging))
                    ConnectionStatus.LOCATION_CHANGED_ALERT
                else if (it.connectionStatus == ConnectionStatus.MUTED_ALERT)
                    ConnectionStatus.MUTED_ALERT
                else
                    ConnectionStatus.ONLINE_MONITORING,
                lastHeartbeatReceivedTime = now,
                remoteDeviceName = payload.deviceName,
                remoteDeviceId = payload.deviceId,
                remoteBatteryPercentage = payload.batteryPercentage,
                remoteNetworkType = payload.networkType,
                latencyMs = latency,
                remoteLatitude = payload.latitude ?: it.remoteLatitude,
                remoteLongitude = payload.longitude ?: it.remoteLongitude,
                remoteLocationAccuracy = payload.locationAccuracy ?: it.remoteLocationAccuracy,
                anchorLatitude = initialRemoteLatitude,
                anchorLongitude = initialRemoteLongitude,
                distanceFromAnchorMeters = currentDistance ?: it.distanceFromAnchorMeters
            )
        }

        if (shouldTriggerLocationAlarm) {
            triggerLocationChangedAlarm(
                displacementMeters = displacement,
                newLat = payload.latitude!!,
                newLng = payload.longitude!!,
                remoteName = payload.deviceName,
                remoteId = payload.deviceId
            )
        }

        val distText = if (currentDistance != null && currentDistance > 0) " • جابجایی: ${LocationHelper.formatDistance(currentDistance)}" else ""
        val locInfo = if (payload.latitude != null && payload.longitude != null) " • موقعیت فعال$distText" else ""
        updateForegroundNotification("ارتباط آنلاین است • باتری: ${payload.batteryPercentage}% • شبکه: ${payload.networkType}$locInfo")
    }

    private fun triggerLocationChangedAlarm(
        displacementMeters: Double,
        newLat: Double,
        newLng: Double,
        remoteName: String,
        remoteId: String
    ) {
        if (isLocationAlarmSilenced) return
        hasTriggeredLocationAlarm = true
        val now = System.currentTimeMillis()
        val reasonText = "جابجایی و تغییر لوکیشن به میزان ${LocationHelper.formatDistance(displacementMeters)} (بیشتر از آستانه مجاز ${locationAlertThresholdMeters} متر)"

        _state.update {
            it.copy(
                connectionStatus = ConnectionStatus.LOCATION_CHANGED_ALERT,
                isLocationAlarmRinging = true,
                isLocationAlarmTriggered = true,
                isAlarmSilenced = false,
                locationChangeDisplacementMeters = displacementMeters,
                activeDisconnectReason = reasonText,
                activeDisconnectStartTime = now
            )
        }

        // Trigger audible and vibration alarm
        alarmPlayer.startAlarm(forceMaxVolume = true)

        // Show prominent Notification
        showLocationChangeNotification(reasonText, remoteName, displacementMeters, newLat, newLng)

        // Save incident to Room Database log
        serviceScope.launch {
            val db = (applicationContext as NetAlertApp).database
            val log = DisconnectLogEntity(
                roomId = activeRoomId,
                startTime = now,
                endTime = now, // Point event
                durationSeconds = 0,
                reason = "📍 هشدار جابجایی مکانی: ${LocationHelper.formatDistance(displacementMeters)} تغییر موقعیت",
                remoteDeviceName = remoteName,
                remoteDeviceId = remoteId,
                batteryAtDisconnect = _state.value.remoteBatteryPercentage,
                networkTypeAtDisconnect = _state.value.remoteNetworkType,
                wasAlarmTriggered = true,
                latitudeAtDisconnect = newLat,
                longitudeAtDisconnect = newLng
            )
            db.disconnectLogDao().insertLog(log)
        }
    }

    private fun triggerDisconnectionAlarm(reason: String, remoteName: String, remoteId: String) {
        if (isDisconnectAlarmSilenced) return
        val now = System.currentTimeMillis()
        val current = _state.value

        _state.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED_ALERT,
                isAlarmRinging = true,
                isAlarmSilenced = false,
                activeDisconnectReason = reason,
                activeDisconnectStartTime = now
            )
        }

        // Trigger Emergency SMS if enabled for remote disconnect
        if (isEmergencySmsEnabled && sendOnRemoteDisconnect && !hasSentSmsForCurrentIncident) {
            hasSentSmsForCurrentIncident = true
            sendEmergencySms(
                reason = reason,
                targetNumber = emergencyPhoneNumber,
                isLocalDevice = false
            )
        }

        // Start ringing and vibrating
        alarmPlayer.startAlarm(forceMaxVolume = true)

        // Show loud emergency notification
        showAlarmNotification(reason, remoteName)

        // Save log in Room
        serviceScope.launch {
            val db = (applicationContext as NetAlertApp).database
            val log = DisconnectLogEntity(
                roomId = activeRoomId,
                startTime = now,
                reason = reason,
                remoteDeviceName = remoteName,
                remoteDeviceId = remoteId,
                batteryAtDisconnect = current.remoteBatteryPercentage,
                networkTypeAtDisconnect = current.remoteNetworkType,
                wasAlarmTriggered = true,
                latitudeAtDisconnect = current.remoteLatitude,
                longitudeAtDisconnect = current.remoteLongitude
            )
            val newId = db.disconnectLogDao().insertLog(log)
            currentActiveLogId = newId
        }
    }

    private fun onLocalInternetLost() {
        if (!_state.value.isRunning) return
        Log.w("MonitorService", "Local internet connectivity lost!")

        if (isEmergencySmsEnabled && sendOnLocalInternetLoss && !hasSentSmsForCurrentIncident) {
            hasSentSmsForCurrentIncident = true
            sendEmergencySms(
                reason = "قطع کامل اینترنت و آفلاین شدن دستگاه در حین پایش",
                targetNumber = emergencyPhoneNumber,
                isLocalDevice = true
            )
        }
    }

    private fun sendEmergencySms(
        reason: String,
        targetNumber: String,
        isLocalDevice: Boolean
    ) {
        if (!EmergencySmsHelper.isValidPhoneNumber(targetNumber)) {
            Log.w("MonitorService", "Cannot send emergency SMS: invalid phone number '$targetNumber'")
            return
        }

        serviceScope.launch {
            val (battery, _) = DeviceUtils.getBatteryInfo(this@ConnectionMonitorService)
            val loc = if (isLocalDevice) {
                LocationHelper.getCurrentLocation(this@ConnectionMonitorService) ?: lastKnownLocalLocation
            } else {
                val rLat = _state.value.remoteLatitude
                val rLng = _state.value.remoteLongitude
                if (rLat != null && rLng != null) Pair(rLat, rLng) else (LocationHelper.getCurrentLocation(this@ConnectionMonitorService) ?: lastKnownLocalLocation)
            }

            val deviceName = if (isLocalDevice) myDeviceName else (_state.value.remoteDeviceName ?: "دستگاه طرف مقابل")
            val message = EmergencySmsHelper.formatEmergencyMessage(
                deviceName = deviceName,
                reason = reason,
                latitude = loc?.first,
                longitude = loc?.second,
                batteryPercentage = if (isLocalDevice) battery else (_state.value.remoteBatteryPercentage ?: battery),
                isLocalDevice = isLocalDevice
            )

            val now = System.currentTimeMillis()
            val result = EmergencySmsHelper.sendDirectSms(this@ConnectionMonitorService, targetNumber, message)
            if (result.isSuccess) {
                val statusText = "پیامک حاوی لوکیشن به $targetNumber با موفقیت ارسال شد"
                Log.i("MonitorService", statusText)
                _state.update {
                    it.copy(
                        lastEmergencySmsTime = now,
                        lastEmergencySmsStatus = statusText
                    )
                }
            } else {
                Log.w("MonitorService", "Direct SMS failed, showing heads-up notification fallback: ${result.exceptionOrNull()?.message}")
                EmergencySmsHelper.showEmergencySmsNotification(
                    this@ConnectionMonitorService,
                    targetNumber,
                    message,
                    "🚨 قطع اینترنت: آماده ارسال پیامک به $targetNumber"
                )
                val statusText = "اعلان ارسال پیامک اضطراری به $targetNumber صادر شد"
                _state.update {
                    it.copy(
                        lastEmergencySmsTime = now,
                        lastEmergencySmsStatus = statusText
                    )
                }
            }
        }
    }

    private fun handleReconnection(payload: HeartbeatPayload) {
        hasSentSmsForCurrentIncident = false
        isDisconnectAlarmSilenced = false
        isLocationAlarmSilenced = false
        alarmPlayer.stopAlarm()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(NOTIFICATION_ID_ALARM)

        val now = System.currentTimeMillis()
        val activeLogId = currentActiveLogId

        if (activeLogId != null) {
            serviceScope.launch {
                val db = (applicationContext as NetAlertApp).database
                val activeLog = db.disconnectLogDao().getActiveUnresolvedLog()
                if (activeLog != null) {
                    val duration = (now - activeLog.startTime) / 1000
                    db.disconnectLogDao().updateLog(
                        activeLog.copy(
                            endTime = now,
                            durationSeconds = duration
                        )
                    )
                }
                currentActiveLogId = null
            }
        }

        _state.update {
            it.copy(
                isAlarmRinging = false,
                isLocationAlarmRinging = false,
                isAlarmSilenced = false,
                connectionStatus = ConnectionStatus.ONLINE_MONITORING,
                activeDisconnectReason = null,
                activeDisconnectStartTime = null
            )
        }

        updateForegroundNotification("اتصال مجدداً برقرار شد • دستگاه: ${payload.deviceName}")
    }

    fun dismissAlarm() {
        // Latch so the alarm will not repeatedly ring or make noise
        isDisconnectAlarmSilenced = true
        isLocationAlarmSilenced = true
        alarmPlayer.stopAlarm()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(NOTIFICATION_ID_ALARM)

        _state.update {
            it.copy(
                isAlarmRinging = false,
                isLocationAlarmRinging = false,
                isAlarmSilenced = true,
                connectionStatus = if (it.connectionStatus == ConnectionStatus.DISCONNECTED_ALERT || it.connectionStatus == ConnectionStatus.MUTED_ALERT)
                    ConnectionStatus.MUTED_ALERT else ConnectionStatus.ONLINE_MONITORING
            )
        }
        updateForegroundNotification("آژیر با موفقیت خاموش و بی‌صدا شد")
    }

    fun resetLocationAnchor() {
        val currLat = _state.value.remoteLatitude
        val currLng = _state.value.remoteLongitude
        if (currLat != null && currLng != null) {
            initialRemoteLatitude = currLat
            initialRemoteLongitude = currLng
        } else {
            initialRemoteLatitude = null
            initialRemoteLongitude = null
        }
        hasTriggeredLocationAlarm = false
        isLocationAlarmSilenced = false
        alarmPlayer.stopAlarm()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(NOTIFICATION_ID_ALARM)

        _state.update {
            it.copy(
                isLocationAlarmRinging = false,
                isLocationAlarmTriggered = false,
                isAlarmSilenced = false,
                anchorLatitude = initialRemoteLatitude,
                anchorLongitude = initialRemoteLongitude,
                distanceFromAnchorMeters = 0.0,
                locationChangeDisplacementMeters = null,
                connectionStatus = if (it.connectionStatus == ConnectionStatus.LOCATION_CHANGED_ALERT)
                    ConnectionStatus.ONLINE_MONITORING else it.connectionStatus
            )
        }
        updateForegroundNotification("مبنای لوکیشن بازنشانی شد")
    }

    fun simulateDisconnect() {
        isDisconnectAlarmSilenced = false
        triggerDisconnectionAlarm(
            reason = "شبیه‌سازی دستی قطع اتصال جهت آزمایش سیستم",
            remoteName = _state.value.remoteDeviceName ?: "دستگاه آزمایشی",
            remoteId = "simulated-test"
        )
    }

    fun simulateLocationChange() {
        isLocationAlarmSilenced = false
        hasTriggeredLocationAlarm = false
        val currentLat = _state.value.remoteLatitude ?: 35.6892
        val currentLng = _state.value.remoteLongitude ?: 51.3890
        val simulatedNewLat = currentLat + 0.003 // ~330 meters displacement
        val simulatedNewLng = currentLng + 0.003

        if (initialRemoteLatitude == null) {
            initialRemoteLatitude = currentLat
            initialRemoteLongitude = currentLng
        }

        triggerLocationChangedAlarm(
            displacementMeters = 350.0,
            newLat = simulatedNewLat,
            newLng = simulatedNewLng,
            remoteName = _state.value.remoteDeviceName ?: "دستگاه آزمایشی",
            remoteId = "simulated-test"
        )
    }

    private fun stopMonitoring() {
        isDisconnectAlarmSilenced = false
        isLocationAlarmSilenced = false
        alarmPlayer.stopAlarm()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(NOTIFICATION_ID_ALARM)

        heartbeatJob?.cancel()
        watchdogJob?.cancel()
        networkMonitor.stopMonitoring()
        mqttManager.disconnect()

        val activeLogId = currentActiveLogId
        if (activeLogId != null) {
            serviceScope.launch {
                val db = (applicationContext as NetAlertApp).database
                val activeLog = db.disconnectLogDao().getActiveUnresolvedLog()
                if (activeLog != null) {
                    val now = System.currentTimeMillis()
                    db.disconnectLogDao().updateLog(
                        activeLog.copy(
                            endTime = now,
                            durationSeconds = (now - activeLog.startTime) / 1000
                        )
                    )
                }
            }
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        _state.value = ServiceStateData()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ConnectionMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NetAlertApp.CHANNEL_MONITOR_ID)
            .setContentTitle("NetAlert: پایش اینترنت و موقعیت فعال")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingLaunch)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف", pendingStop)
            .build()
    }

    private fun updateForegroundNotification(content: String) {
        val notif = buildForegroundNotification(content)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.notify(NOTIFICATION_ID_FOREGROUND, notif)
    }

    private fun showAlarmNotification(reason: String, remoteDeviceName: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this, 2, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, ConnectionMonitorService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val pendingDismiss = PendingIntent.getService(
            this, 3, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmNotification = NotificationCompat.Builder(this, NetAlertApp.CHANNEL_ALARM_ID)
            .setContentTitle("🚨 قطع ارتباط اینترنت!")
            .setContentText("ارتباط با $remoteDeviceName قطع شد: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("هشدار! دستگاه مقابل آفلاین شد.\nدلیل: $reason\nزمان: ${DeviceUtils.formatTimeOnly(System.currentTimeMillis())}"))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingLaunch)
            .setFullScreenIntent(pendingLaunch, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_lock_silent_mode_off, "🛑 قطع صدا و آژیر", pendingDismiss)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.notify(NOTIFICATION_ID_ALARM, alarmNotification)
    }

    private fun showLocationChangeNotification(
        reason: String,
        remoteDeviceName: String,
        displacementMeters: Double,
        newLat: Double,
        newLng: Double
    ) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this, 4, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, ConnectionMonitorService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val pendingDismiss = PendingIntent.getService(
            this, 5, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NetAlertApp.CHANNEL_ALARM_ID)
            .setContentTitle("📍 هشدار تغییر لوکیشن دستگاه!")
            .setContentText("دستگاه $remoteDeviceName جابجا شد: ${LocationHelper.formatDistance(displacementMeters)}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "دستگاه «$remoteDeviceName» از موقعیت اولیه خارج شد!\n" +
                    "میزان جابجایی: ${LocationHelper.formatDistance(displacementMeters)}\n" +
                    "موقعیت جدید: ${LocationHelper.formatCoordinates(newLat, newLng)}\n" +
                    "زمان: ${DeviceUtils.formatTimeOnly(System.currentTimeMillis())}"
                )
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingLaunch)
            .setFullScreenIntent(pendingLaunch, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_lock_silent_mode_off, "🛑 قطع صدا و آژیر", pendingDismiss)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.notify(NOTIFICATION_ID_ALARM, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmPlayer.stopAlarm()
        networkMonitor.stopMonitoring()
        mqttManager.disconnect()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.netalert.START"
        const val ACTION_STOP = "com.example.netalert.STOP"
        const val ACTION_DISMISS_ALARM = "com.example.netalert.DISMISS_ALARM"
        const val ACTION_RESET_LOCATION_ANCHOR = "com.example.netalert.RESET_LOCATION_ANCHOR"
        const val ACTION_SIMULATE_DISCONNECT = "com.example.netalert.SIMULATE_DISCONNECT"
        const val ACTION_SIMULATE_LOCATION_CHANGE = "com.example.netalert.SIMULATE_LOCATION_CHANGE"
        const val ACTION_SEND_EMERGENCY_SMS_NOW = "com.example.netalert.SEND_EMERGENCY_SMS_NOW"

        const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        const val EXTRA_ROLE = "EXTRA_ROLE"
        const val EXTRA_PING_INTERVAL_SEC = "EXTRA_PING_INTERVAL_SEC"
        const val EXTRA_TIMEOUT_SEC = "EXTRA_TIMEOUT_SEC"
        const val EXTRA_SHARE_LOCATION = "EXTRA_SHARE_LOCATION"
        const val EXTRA_LOCATION_ALERT_ENABLED = "EXTRA_LOCATION_ALERT_ENABLED"
        const val EXTRA_LOCATION_ALERT_THRESHOLD = "EXTRA_LOCATION_ALERT_THRESHOLD"
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
        const val EXTRA_DEVICE_ID = "EXTRA_DEVICE_ID"
        const val EXTRA_BROKER_URL = "EXTRA_BROKER_URL"
        const val EXTRA_EMERGENCY_PHONE_NUMBER = "EXTRA_EMERGENCY_PHONE_NUMBER"
        const val EXTRA_EMERGENCY_SMS_ENABLED = "EXTRA_EMERGENCY_SMS_ENABLED"
        const val EXTRA_SEND_ON_LOCAL_INTERNET_LOSS = "EXTRA_SEND_ON_LOCAL_INTERNET_LOSS"
        const val EXTRA_SEND_ON_REMOTE_DISCONNECT = "EXTRA_SEND_ON_REMOTE_DISCONNECT"

        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_ALARM = 1002

        private val _state = MutableStateFlow(ServiceStateData())
        val state: StateFlow<ServiceStateData> = _state.asStateFlow()
    }
}
