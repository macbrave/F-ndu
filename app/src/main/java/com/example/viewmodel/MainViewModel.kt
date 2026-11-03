package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NetAlertApp
import com.example.data.local.DisconnectLogEntity
import com.example.model.DeviceRole
import com.example.repository.NetAlertRepository
import com.example.service.AlarmPlayer
import com.example.service.ConnectionMonitorService
import com.example.service.ServiceStateData
import com.example.util.DeviceUtils
import com.example.util.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NetAlertRepository
    private val testAlarmPlayer = AlarmPlayer(application)
    private val prefs = application.getSharedPreferences("netalert_prefs", Context.MODE_PRIVATE)

    val serviceState: StateFlow<ServiceStateData> = ConnectionMonitorService.state

    val allLogs: StateFlow<List<DisconnectLogEntity>>

    val myDeviceId: String
    val myDeviceName: String = DeviceUtils.getDeviceDisplayName()

    private val _roomIdInput = MutableStateFlow("")
    val roomIdInput: StateFlow<String> = _roomIdInput.asStateFlow()

    private val _selectedRole = MutableStateFlow(DeviceRole.MONITOR_AND_ALARM)
    val selectedRole: StateFlow<DeviceRole> = _selectedRole.asStateFlow()

    private val _pingIntervalSec = MutableStateFlow(3)
    val pingIntervalSec: StateFlow<Int> = _pingIntervalSec.asStateFlow()

    private val _timeoutSec = MutableStateFlow(9)
    val timeoutSec: StateFlow<Int> = _timeoutSec.asStateFlow()

    private val _isShareLocationEnabled = MutableStateFlow(false)
    val isShareLocationEnabled: StateFlow<Boolean> = _isShareLocationEnabled.asStateFlow()

    private val _isLocationChangeAlertEnabled = MutableStateFlow(true)
    val isLocationChangeAlertEnabled: StateFlow<Boolean> = _isLocationChangeAlertEnabled.asStateFlow()

    private val _locationAlertThresholdMeters = MutableStateFlow(50)
    val locationAlertThresholdMeters: StateFlow<Int> = _locationAlertThresholdMeters.asStateFlow()

    val availableBrokers = listOf(
        "tcp://broker.hivemq.com:1883",
        "tcp://broker.emqx.io:1883",
        "tcp://test.mosquitto.org:1883"
    )

    private val _selectedBroker = MutableStateFlow(availableBrokers[0])
    val selectedBroker: StateFlow<String> = _selectedBroker.asStateFlow()

    private val _isTestingAlarm = MutableStateFlow(false)
    val isTestingAlarm: StateFlow<Boolean> = _isTestingAlarm.asStateFlow()

    private val _emergencyPhoneNumber = MutableStateFlow("")
    val emergencyPhoneNumber: StateFlow<String> = _emergencyPhoneNumber.asStateFlow()

    private val _isEmergencySmsEnabled = MutableStateFlow(false)
    val isEmergencySmsEnabled: StateFlow<Boolean> = _isEmergencySmsEnabled.asStateFlow()

    private val _sendOnLocalInternetLoss = MutableStateFlow(true)
    val sendOnLocalInternetLoss: StateFlow<Boolean> = _sendOnLocalInternetLoss.asStateFlow()

    private val _sendOnRemoteDisconnect = MutableStateFlow(true)
    val sendOnRemoteDisconnect: StateFlow<Boolean> = _sendOnRemoteDisconnect.asStateFlow()

    init {
        val db = (application as NetAlertApp).database
        repository = NetAlertRepository(db.disconnectLogDao())

        allLogs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Persistent Device ID
        var savedDevId = prefs.getString(KEY_DEVICE_ID, null)
        if (savedDevId.isNullOrEmpty()) {
            savedDevId = UUID.randomUUID().toString().take(6).uppercase()
            prefs.edit().putString(KEY_DEVICE_ID, savedDevId).apply()
        }
        myDeviceId = savedDevId

        // Persistent Room ID: load last used room or generate first one
        val savedRoomId = prefs.getString(KEY_ROOM_ID, null)
        if (!savedRoomId.isNullOrEmpty()) {
            _roomIdInput.value = savedRoomId
        } else {
            val initialRoom = generateRandomCode()
            _roomIdInput.value = initialRoom
            prefs.edit().putString(KEY_ROOM_ID, initialRoom).apply()
        }

        // Persistent preferences
        val savedRoleName = prefs.getString(KEY_ROLE, null)
        if (savedRoleName != null) {
            runCatching { DeviceRole.valueOf(savedRoleName) }.getOrNull()?.let {
                _selectedRole.value = it
            }
        }

        _pingIntervalSec.value = prefs.getInt(KEY_PING_INTERVAL, 3)
        _timeoutSec.value = prefs.getInt(KEY_TIMEOUT, 9)
        _isShareLocationEnabled.value = prefs.getBoolean(KEY_SHARE_LOCATION, false)
        _isLocationChangeAlertEnabled.value = prefs.getBoolean(KEY_LOCATION_ALERT_ENABLED, true)
        _locationAlertThresholdMeters.value = prefs.getInt(KEY_LOCATION_ALERT_THRESHOLD, 50)
        _selectedBroker.value = prefs.getString(KEY_BROKER, availableBrokers[0]) ?: availableBrokers[0]

        _emergencyPhoneNumber.value = prefs.getString(KEY_EMERGENCY_PHONE, "") ?: ""
        _isEmergencySmsEnabled.value = prefs.getBoolean(KEY_EMERGENCY_SMS_ENABLED, false)
        _sendOnLocalInternetLoss.value = prefs.getBoolean(KEY_SEND_ON_LOCAL_INTERNET_LOSS, true)
        _sendOnRemoteDisconnect.value = prefs.getBoolean(KEY_SEND_ON_REMOTE_DISCONNECT, true)
    }

    fun onRoomIdChanged(newCode: String) {
        val sanitized = newCode.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(16)
        _roomIdInput.value = sanitized
        prefs.edit().putString(KEY_ROOM_ID, sanitized).apply()
    }

    fun onRoleSelected(role: DeviceRole) {
        _selectedRole.value = role
        prefs.edit().putString(KEY_ROLE, role.name).apply()
    }

    fun onPingIntervalChanged(interval: Int) {
        _pingIntervalSec.value = interval
        prefs.edit().putInt(KEY_PING_INTERVAL, interval).apply()
    }

    fun onTimeoutChanged(timeout: Int) {
        _timeoutSec.value = timeout
        prefs.edit().putInt(KEY_TIMEOUT, timeout).apply()
    }

    fun onBrokerSelected(broker: String) {
        _selectedBroker.value = broker
        prefs.edit().putString(KEY_BROKER, broker).apply()
    }

    fun setShareLocationEnabled(enabled: Boolean) {
        _isShareLocationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SHARE_LOCATION, enabled).apply()
    }

    fun setLocationChangeAlertEnabled(enabled: Boolean) {
        _isLocationChangeAlertEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOCATION_ALERT_ENABLED, enabled).apply()
    }

    fun setLocationAlertThresholdMeters(meters: Int) {
        _locationAlertThresholdMeters.value = meters
        prefs.edit().putInt(KEY_LOCATION_ALERT_THRESHOLD, meters).apply()
    }

    fun onEmergencyPhoneNumberChanged(number: String) {
        val sanitized = com.example.util.EmergencySmsHelper.sanitizePhoneNumber(number)
        _emergencyPhoneNumber.value = sanitized
        prefs.edit().putString(KEY_EMERGENCY_PHONE, sanitized).apply()
    }

    fun setEmergencySmsEnabled(enabled: Boolean) {
        _isEmergencySmsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_EMERGENCY_SMS_ENABLED, enabled).apply()
    }

    fun setSendOnLocalInternetLoss(enabled: Boolean) {
        _sendOnLocalInternetLoss.value = enabled
        prefs.edit().putBoolean(KEY_SEND_ON_LOCAL_INTERNET_LOSS, enabled).apply()
    }

    fun setSendOnRemoteDisconnect(enabled: Boolean) {
        _sendOnRemoteDisconnect.value = enabled
        prefs.edit().putBoolean(KEY_SEND_ON_REMOTE_DISCONNECT, enabled).apply()
    }

    fun sendTestEmergencySms(context: Context, onResult: (Boolean, String) -> Unit) {
        val number = _emergencyPhoneNumber.value
        if (!com.example.util.EmergencySmsHelper.isValidPhoneNumber(number)) {
            onResult(false, "شماره تماس اضطراری نامعتبر است (مثال: 09121234567)")
            return
        }

        viewModelScope.launch {
            val (battery, _) = DeviceUtils.getBatteryInfo(context)
            val loc = LocationHelper.getCurrentLocation(context)
            val testMessage = com.example.util.EmergencySmsHelper.formatEmergencyMessage(
                deviceName = "$myDeviceName (تست)",
                reason = "تست صحت ارسال پیامک اضطراری NetAlert",
                latitude = loc?.first ?: 35.6892,
                longitude = loc?.second ?: 51.3890,
                batteryPercentage = battery,
                isLocalDevice = true
            )

            if (com.example.util.EmergencySmsHelper.hasSmsPermission(context)) {
                val res = com.example.util.EmergencySmsHelper.sendDirectSms(context, number, testMessage)
                if (res.isSuccess) {
                    onResult(true, "پیامک تستی با موفقیت به $number ارسال شد.")
                } else {
                    onResult(false, "خطا در ارسال پیامک مستقیم: ${res.exceptionOrNull()?.message ?: "نامشخص"}")
                }
            } else {
                // Open SMS app as zero-permission compliant fallback
                context.startActivity(com.example.util.EmergencySmsHelper.createSmsIntent(number, testMessage))
                onResult(true, "برنامه پیام‌رسان با متن آماده باز شد.")
            }
        }
    }

    fun triggerEmergencySmsNow(context: Context) {
        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_SEND_EMERGENCY_SMS_NOW
            putExtra(ConnectionMonitorService.EXTRA_EMERGENCY_PHONE_NUMBER, _emergencyPhoneNumber.value)
        }
        context.startService(intent)
    }

    fun generateNewRoomCode() {
        val newCode = generateRandomCode()
        _roomIdInput.value = newCode
        prefs.edit().putString(KEY_ROOM_ID, newCode).apply()
    }

    private fun generateRandomCode(): String {
        val num1 = Random.nextInt(100, 999)
        val num2 = Random.nextInt(100, 999)
        return "$num1-$num2"
    }

    fun startMonitoring(context: Context) {
        // Ensure latest Room ID is saved
        prefs.edit().putString(KEY_ROOM_ID, _roomIdInput.value).apply()

        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_START
            putExtra(ConnectionMonitorService.EXTRA_ROOM_ID, _roomIdInput.value)
            putExtra(ConnectionMonitorService.EXTRA_ROLE, _selectedRole.value.name)
            putExtra(ConnectionMonitorService.EXTRA_PING_INTERVAL_SEC, _pingIntervalSec.value)
            putExtra(ConnectionMonitorService.EXTRA_TIMEOUT_SEC, _timeoutSec.value)
            putExtra(ConnectionMonitorService.EXTRA_SHARE_LOCATION, _isShareLocationEnabled.value)
            putExtra(ConnectionMonitorService.EXTRA_LOCATION_ALERT_ENABLED, _isLocationChangeAlertEnabled.value)
            putExtra(ConnectionMonitorService.EXTRA_LOCATION_ALERT_THRESHOLD, _locationAlertThresholdMeters.value)
            putExtra(ConnectionMonitorService.EXTRA_DEVICE_NAME, myDeviceName)
            putExtra(ConnectionMonitorService.EXTRA_DEVICE_ID, myDeviceId)
            putExtra(ConnectionMonitorService.EXTRA_BROKER_URL, _selectedBroker.value)
            putExtra(ConnectionMonitorService.EXTRA_EMERGENCY_PHONE_NUMBER, _emergencyPhoneNumber.value)
            putExtra(ConnectionMonitorService.EXTRA_EMERGENCY_SMS_ENABLED, _isEmergencySmsEnabled.value)
            putExtra(ConnectionMonitorService.EXTRA_SEND_ON_LOCAL_INTERNET_LOSS, _sendOnLocalInternetLoss.value)
            putExtra(ConnectionMonitorService.EXTRA_SEND_ON_REMOTE_DISCONNECT, _sendOnRemoteDisconnect.value)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopMonitoring(context: Context) {
        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun dismissAlarm(context: Context) {
        if (_isTestingAlarm.value) {
            testAlarmPlayer.stopAlarm()
            _isTestingAlarm.value = false
        }
        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_DISMISS_ALARM
        }
        context.startService(intent)
    }

    fun resetLocationAnchor(context: Context) {
        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_RESET_LOCATION_ANCHOR
        }
        context.startService(intent)
    }

    fun simulateDisconnect(context: Context) {
        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_SIMULATE_DISCONNECT
        }
        context.startService(intent)
    }

    fun simulateLocationChange(context: Context) {
        val intent = Intent(context, ConnectionMonitorService::class.java).apply {
            action = ConnectionMonitorService.ACTION_SIMULATE_LOCATION_CHANGE
        }
        context.startService(intent)
    }

    fun toggleTestAlarm() {
        if (_isTestingAlarm.value) {
            testAlarmPlayer.stopAlarm()
            _isTestingAlarm.value = false
        } else {
            testAlarmPlayer.startAlarm(forceMaxVolume = false)
            _isTestingAlarm.value = true
        }
    }

    fun deleteLog(log: DisconnectLogEntity) {
        viewModelScope.launch {
            repository.deleteLog(log)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    fun formatHistoryReport(): String {
        val list = allLogs.value
        val sb = StringBuilder()
        sb.append("گزارش تاریخچه و لاگ قطعی‌ها و هشدارهای NetAlert\n")
        sb.append("تاریخ خروجی: ${DeviceUtils.formatTimestamp(System.currentTimeMillis())}\n")
        sb.append("دستگاه گزارش‌گیرنده: $myDeviceName\n")
        sb.append("تعداد کل رخدادها: ${list.size}\n")
        sb.append("------------------------------------------\n\n")

        list.forEachIndexed { index, item ->
            sb.append("${index + 1}. اتاق: ${item.roomId} | دستگاه: ${item.remoteDeviceName}\n")
            sb.append("   شروع رویداد: ${DeviceUtils.formatTimestamp(item.startTime)}\n")
            if (item.endTime != null && (item.durationSeconds ?: 0) > 0) {
                sb.append("   پایان: ${DeviceUtils.formatTimestamp(item.endTime)}\n")
                sb.append("   مدت زمان: ${DeviceUtils.formatDuration(item.durationSeconds ?: 0)}\n")
            } else if (item.durationSeconds == 0L) {
                sb.append("   نوع: هشدار لحظه‌ای تغییر موقعیت مکانی\n")
            } else {
                sb.append("   وضعیت: همچنان قطع یا در حال رفع\n")
            }
            sb.append("   علت/توضیحات: ${item.reason}\n")
            if (item.batteryAtDisconnect != null) {
                sb.append("   درصد باتری طرف مقابل: ${item.batteryAtDisconnect}%\n")
            }
            if (item.latitudeAtDisconnect != null && item.longitudeAtDisconnect != null) {
                sb.append("   موقعیت مکانی ثبت‌شده: ${LocationHelper.formatCoordinates(item.latitudeAtDisconnect, item.longitudeAtDisconnect)}\n")
                sb.append("   لینک نقشه: https://maps.google.com/?q=${item.latitudeAtDisconnect},${item.longitudeAtDisconnect}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        testAlarmPlayer.stopAlarm()
    }

    companion object {
        private const val KEY_ROOM_ID = "saved_room_id"
        private const val KEY_DEVICE_ID = "saved_device_id"
        private const val KEY_ROLE = "saved_role"
        private const val KEY_PING_INTERVAL = "saved_ping_interval"
        private const val KEY_TIMEOUT = "saved_timeout"
        private const val KEY_SHARE_LOCATION = "saved_share_location"
        private const val KEY_LOCATION_ALERT_ENABLED = "saved_location_alert_enabled"
        private const val KEY_LOCATION_ALERT_THRESHOLD = "saved_location_alert_threshold"
        private const val KEY_BROKER = "saved_broker"
        private const val KEY_EMERGENCY_PHONE = "saved_emergency_phone"
        private const val KEY_EMERGENCY_SMS_ENABLED = "saved_emergency_sms_enabled"
        private const val KEY_SEND_ON_LOCAL_INTERNET_LOSS = "saved_send_on_local_internet_loss"
        private const val KEY_SEND_ON_REMOTE_DISCONNECT = "saved_send_on_remote_disconnect"
    }
}
