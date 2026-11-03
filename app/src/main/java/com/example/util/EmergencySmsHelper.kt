package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EmergencySmsHelper {

    private const val NOTIFICATION_CHANNEL_SMS = "netalert_sms_channel"
    private const val NOTIFICATION_ID_SMS = 3001

    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun sanitizePhoneNumber(raw: String): String {
        return raw.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            // Convert Persian/Arabic digits to English digits
            .map { ch ->
                when (ch) {
                    '۰', '٠' -> '0'
                    '۱', '١' -> '1'
                    '۲', '٢' -> '2'
                    '۳', '٣' -> '3'
                    '۴', '٤' -> '4'
                    '۵', '٥' -> '5'
                    '۶', '٦' -> '6'
                    '۷', '٧' -> '7'
                    '۸', '٨' -> '8'
                    '۹', '٩' -> '9'
                    else -> ch
                }
            }.joinToString("")
    }

    fun isValidPhoneNumber(number: String): Boolean {
        val sanitized = sanitizePhoneNumber(number)
        // Matches Iranian format (09..., +989...) or international numbers with 7 to 15 digits
        return sanitized.matches(Regex("""^(\+?[0-9]{7,15})$"""))
    }

    fun formatEmergencyMessage(
        deviceName: String,
        reason: String,
        latitude: Double?,
        longitude: Double?,
        batteryPercentage: Int?,
        isLocalDevice: Boolean = true
    ): String {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date())

        val locationUrl = if (latitude != null && longitude != null) {
            String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
        } else {
            "موقعیت مکانی ثبت نشده"
        }

        val batteryText = if (batteryPercentage != null) "باتری: $batteryPercentage%" else ""
        val sourceText = if (isLocalDevice) "اینترنت همین گوشی قطع شد" else "ارتباط با دستگاه طرف مقابل قطع شد"

        return buildString {
            appendLine("🚨 هشدار اضطراری NetAlert")
            appendLine("وضعیت: $sourceText")
            appendLine("دستگاه: $deviceName")
            appendLine("علت: $reason")
            appendLine("زمان: $timeStr")
            if (batteryText.isNotEmpty()) {
                appendLine(batteryText)
            }
            appendLine("موقعیت:")
            append(locationUrl)
        }
    }

    /**
     * Sends SMS directly using SmsManager when SEND_SMS permission is granted.
     */
    fun sendDirectSms(
        context: Context,
        phoneNumber: String,
        message: String
    ): Result<Unit> {
        val cleanNumber = sanitizePhoneNumber(phoneNumber)
        if (!isValidPhoneNumber(cleanNumber)) {
            return Result.failure(IllegalArgumentException("شماره تلفن نامعتبر است: $phoneNumber"))
        }

        if (!hasSmsPermission(context)) {
            return Result.failure(SecurityException("دسترسی ارسال پیامک (SEND_SMS) اعطا نشده است"))
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            Log.d("EmergencySmsHelper", "Direct SMS dispatched to $cleanNumber (${parts.size} parts)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EmergencySmsHelper", "Failed to send direct SMS to $cleanNumber", e)
            Result.failure(e)
        }
    }

    /**
     * Creates an Intent to open the user's SMS messenger app with the number and message pre-filled.
     * Complies with Google Play zero-permission fallback.
     */
    fun createSmsIntent(phoneNumber: String, message: String): Intent {
        val cleanNumber = sanitizePhoneNumber(phoneNumber)
        val uri = Uri.parse("smsto:${Uri.encode(cleanNumber)}")
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Shows a high-priority heads-up notification with a 1-tap action to launch SMS sending.
     * Used when direct SMS permission is missing or as an emergency backup.
     */
    fun showEmergencySmsNotification(
        context: Context,
        phoneNumber: String,
        message: String,
        title: String = "🚨 ارسال پیامک اضطراری به شماره $phoneNumber"
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_SMS,
                "پیامک اضطراری (Emergency SMS)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ارسال لوکیشن از طریق پیامک در صورت قطعی اینترنت"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val smsIntent = createSmsIntent(phoneNumber, message)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_SMS,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("اینترنت قطع است. برای ارسال پیامک موقعیت مکانی لمس کنید.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "ارسال پیامک هم‌اکنون",
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_SMS, notification)
    }
}
