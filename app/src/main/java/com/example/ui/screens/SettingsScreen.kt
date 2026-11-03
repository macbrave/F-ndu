package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.Slate800
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.example.ui.theme.GreenOnline
import com.example.util.EmergencySmsHelper

@Composable
fun SettingsScreen(
    availableBrokers: List<String>,
    selectedBroker: String,
    pingIntervalSec: Int,
    timeoutSec: Int,
    isLocationChangeAlertEnabled: Boolean,
    locationAlertThresholdMeters: Int,
    myDeviceName: String,
    myDeviceId: String,
    isMonitoringRunning: Boolean,
    emergencyPhoneNumber: String,
    isEmergencySmsEnabled: Boolean,
    sendOnLocalInternetLoss: Boolean,
    sendOnRemoteDisconnect: Boolean,
    onBrokerSelected: (String) -> Unit,
    onPingIntervalChanged: (Int) -> Unit,
    onTimeoutChanged: (Int) -> Unit,
    onLocationChangeAlertToggled: (Boolean) -> Unit,
    onLocationAlertThresholdChanged: (Int) -> Unit,
    onEmergencyPhoneChanged: (String) -> Unit,
    onEmergencySmsToggled: (Boolean) -> Unit,
    onSendOnLocalLossToggled: (Boolean) -> Unit,
    onSendOnRemoteDisconnectToggled: (Boolean) -> Unit,
    onSendTestSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "مجوز ارسال پیامک اضطراری تایید شد", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "در نبود مجوز، پیامک از طریق برنامه پیش‌فرض پیام‌ها باز خواهد شد", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "⚙️ تنظیمات و راهنمای NetAlert",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            text = "شخصی‌سازی حساسیت زمان‌بندی، آستانه تغییر موقعیت و کارگزار",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Guide Card (راهنمای استفاده بین دو گوشی)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "راهنمای اتصال و پایش موقعیت",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val steps = listOf(
                    "۱. نرم‌افزار را روی هر دو گوشی اندرویدی نصب و اجرا نمایید.",
                    "۲. در هر دو گوشی یک «شناسه اتصال (Room ID)» کاملاً یکسان وارد کنید (مثلاً: 554-123).",
                    "۳. روی گوشی اول که می‌خواهید پایش شود، گزینه «تحت پایش (Sender)» و سوییچ «ارسال موقعیت مکانی» را روشن کنید.",
                    "۴. روی گوشی دوم (همراه شما)، گزینه «گیرنده و هشداردهنده (Listener)» را انتخاب و شروع را بزنید.",
                    "۵. هر زمان گوشی اول به هر دلیلی آفلاین شود یا بیشتر از آستانه مجاز (مثلاً ۵۰ متر) جابجا شود، بلافاصله روی گوشی دوم آژیر پرقدرت به صدا درمی‌آید!"
                )

                steps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.LightGray,
                            lineHeight = 22.sp
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Emergency SMS Alert Card (ارسال پیامک اضطراری در نبود اینترنت)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ارسال پیامک اضطراری در قطع اینترنت",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Switch(
                        checked = isEmergencySmsEnabled,
                        onCheckedChange = { onEmergencySmsToggled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanAccent,
                            checkedTrackColor = CyanAccent.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("emergency_sms_switch")
                    )
                }

                Text(
                    text = "در صورت قطع کامل اینترنت و نبود ارتباط، آخرین موقعیت مکانی به همراه لینک مستقیم نقشه به شماره اضطراری زیر پیامک می‌گردد.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.LightGray,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (isEmergencySmsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emergencyPhoneNumber,
                        onValueChange = onEmergencyPhoneChanged,
                        label = { Text("شماره تماس اضطراری (گیرنده پیامک)") },
                        placeholder = { Text("مثال: 09123456789 یا +98912...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = CyanAccent
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SMS Permission status & button
                    val hasSmsPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasSmsPerm) Color(0xFF064E3B) else Color(0xFF451A03),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (hasSmsPerm) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (hasSmsPerm) GreenOnline else AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasSmsPerm)
                                        "دسترسی ارسال خودکار پیامک فعال است"
                                    else
                                        "نیاز به تایید مجوز جهت ارسال خودکار در پس‌زمینه",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }

                            if (!hasSmsPerm) {
                                Button(
                                    onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("request_sms_permission_button")
                                ) {
                                    Text("اعطای مجوز", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sub options
                    Text(
                        text = "شرایط ارسال پیامک اضطراری:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Switch 1: Local Internet Loss
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSendOnLocalLossToggled(!sendOnLocalInternetLoss) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "هنگام قطع اینترنت همین گوشی (ارسال لوکیشن زنده)",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = sendOnLocalInternetLoss,
                            onCheckedChange = onSendOnLocalLossToggled,
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                        )
                    }

                    // Switch 2: Remote Device Disconnect
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSendOnRemoteDisconnectToggled(!sendOnRemoteDisconnect) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "هنگام قطع ارتباط گوشی مقابل (ارسال آخرین لوکیشن)",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = sendOnRemoteDisconnect,
                            onCheckedChange = onSendOnRemoteDisconnectToggled,
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test SMS Button
                    OutlinedButton(
                        onClick = onSendTestSms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("test_emergency_sms_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📩 ارسال پیامک آزمایشی به شماره اضطراری", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Location Change Alert Sensitivity Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "هشدار تغییر لوکیشن (Geofence)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "آژیر کشیدن در صورت جابجایی دستگاه طرف مقابل",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                        }
                    }

                    Switch(
                        checked = isLocationChangeAlertEnabled,
                        onCheckedChange = onLocationChangeAlertToggled,
                        enabled = !isMonitoringRunning,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmberWarning,
                            checkedTrackColor = AmberWarning.copy(alpha = 0.4f)
                        )
                    )
                }

                if (isLocationChangeAlertEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "شعاع حساسیت جابجایی: $locationAlertThresholdMeters متر",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Text(
                        text = "اگر دستگاه بیشتر از این فاصله از موقعیت اولیه فاصله بگیرد، آژیر کشیده خواهد شد.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Slider(
                        value = locationAlertThresholdMeters.toFloat(),
                        onValueChange = { onLocationAlertThresholdChanged(it.toInt()) },
                        valueRange = 10f..500f,
                        steps = 48,
                        enabled = !isMonitoringRunning,
                        colors = SliderDefaults.colors(
                            thumbColor = AmberWarning,
                            activeTrackColor = AmberWarning
                        )
                    )
                }
            }
        }

        // Timing Sensitivity Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "دوره ارسال ضربان (Ping Interval): $pingIntervalSec ثانیه",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                Text(
                    text = "فاصله زمانی بین ارسال هر پکت ضربان اتصال توسط دستگاه مبدأ",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Slider(
                    value = pingIntervalSec.toFloat(),
                    onValueChange = { onPingIntervalChanged(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    enabled = !isMonitoringRunning,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = CyanAccent
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "آستانه هشدار قطعی (Timeout): $timeoutSec ثانیه",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                Text(
                    text = "اگر پس از این مدت هیچ ضربانی دریافت نشود، آژیر اضطراری روشن می‌شود.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Slider(
                    value = timeoutSec.toFloat(),
                    onValueChange = { onTimeoutChanged(it.toInt()) },
                    valueRange = 5f..30f,
                    steps = 24,
                    enabled = !isMonitoringRunning,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = CyanAccent
                    )
                )
            }
        }

        // Broker Server Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "سرور کارگزار پیام (MQTT Broker)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                Text(
                    text = "سرورهای عمومی پرسرعت جهت تبادل پیام و شناسایی Last-Will",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                availableBrokers.forEach { broker ->
                    val isSelected = selectedBroker == broker
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isMonitoringRunning) { onBrokerSelected(broker) }
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CyanAccent else Slate800,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        color = if (isSelected) CyanAccent.copy(alpha = 0.1f) else SurfaceCardElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) CyanAccent else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = broker,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }

        // Device Specs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PermDeviceInformation,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مشخصات این گوشی",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "نام دستگاه:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = myDeviceName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "شناسه منحصر‌به‌فرد:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = myDeviceId,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
