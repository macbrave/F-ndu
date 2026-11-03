package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ConnectionStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.RedOffline
import com.example.ui.theme.Slate950
import com.example.util.DeviceUtils
import com.example.util.LocationHelper
import kotlinx.coroutines.delay

@Composable
fun ActiveAlarmDialog(
    isVisible: Boolean,
    status: ConnectionStatus,
    isRinging: Boolean,
    reason: String?,
    remoteDeviceName: String?,
    startTimeMs: Long?,
    latitude: Double? = null,
    longitude: Double? = null,
    displacementMeters: Double? = null,
    emergencyPhoneNumber: String = "",
    isEmergencySmsEnabled: Boolean = false,
    lastEmergencySmsStatus: String? = null,
    onSendEmergencySms: () -> Unit = {},
    onMuteAlarm: () -> Unit,
    onResetAnchor: () -> Unit,
    onOpenLiveMap: () -> Unit = {},
    onViewHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    val context = LocalContext.current
    val isLocationAlarm = status == ConnectionStatus.LOCATION_CHANGED_ALERT

    var elapsedSeconds by remember(startTimeMs) {
        mutableLongStateOf(
            if (startTimeMs != null) ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(0) else 0L
        )
    }

    LaunchedEffect(startTimeMs) {
        while (true) {
            delay(1000L)
            if (startTimeMs != null) {
                elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(0)
            }
        }
    }

    val themeColor = if (isLocationAlarm) AmberWarning else RedOffline
    val containerBg = if (isLocationAlarm) Color(0xFF231805) else Color(0xFF1E1417)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Slate950)
                .border(2.dp, themeColor, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pulsing Alert Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLocationAlarm) Icons.Default.MyLocation else Icons.Default.Warning,
                        contentDescription = "هشدار",
                        tint = themeColor,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isLocationAlarm) "📍 هشدار تغییر لوکیشن دستگاه!" else "🚨 قطع ارتباط اینترنت!",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = themeColor
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isLocationAlarm)
                        "دستگاه «${remoteDeviceName ?: "دستگاه پایش‌شده"}» جابجا شده و از موقعیت مجاز خارج شده است!"
                    else
                        "ارتباط با دستگاه «${remoteDeviceName ?: "دستگاه پایش‌شده"}» قطع شد و اینترنت آن در دسترس نیست.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detail Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = containerBg)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (isLocationAlarm && displacementMeters != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "میزان جابجایی ثبت‌شده:",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray)
                                )
                                Text(
                                    text = LocationHelper.formatDistance(displacementMeters),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AmberWarning
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مدت زمان قطع بودن:",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray)
                                )
                                Text(
                                    text = DeviceUtils.formatDuration(elapsedSeconds),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = RedOffline
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "شرح رویداد:",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray)
                            )
                            Text(
                                text = reason ?: "تغییر موقعیت دستگاه",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                ),
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        // Location Display
                        if (latitude != null && longitude != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        text = "موقعیت کنونی:",
                                        style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray)
                                    )
                                }
                                Text(
                                    text = LocationHelper.formatCoordinates(latitude, longitude),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // If location is available, show direct Map launch button
                if (latitude != null && longitude != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onOpenLiveMap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("dialog_open_live_map_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CyanAccent,
                            contentColor = Slate950
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = Slate950,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📍 مشاهده موقعیت زنده در نقشه داخلی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Emergency SMS dispatch status or manual send button
                if (isEmergencySmsEnabled && emergencyPhoneNumber.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSendEmergencySms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_send_emergency_sms_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CyanAccent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📩 ارسال پیامک موقعیت به $emergencyPhoneNumber",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (lastEmergencySmsStatus != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastEmergencySmsStatus,
                            style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Stop / Mute Alarm
                if (isRinging) {
                    Button(
                        onClick = onMuteAlarm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("mute_alarm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "🛑 قطع فوری صدا و خاموش کردن آژیر",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "با لمس این دکمه صدا کاملاً قطع شده و تا اتصال مجدد تکرار نمی‌شود",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    // Reassuring badge when alarm has been muted
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "صدای آژیر قطع شد • دیگر تکرار نخواهد شد",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // If location changed, allow resetting baseline from this dialog
                if (isLocationAlarm) {
                    OutlinedButton(
                        onClick = onResetAnchor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("dialog_reset_anchor_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "تأیید موقعیت جدید به عنوان مبنا (Reset)", fontSize = 13.sp, color = AmberWarning)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewHistory,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("view_history_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "مشاهده لاگ", fontSize = 14.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dismiss_dialog_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text(text = "متوجه شدم", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
