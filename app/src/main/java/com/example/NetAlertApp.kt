package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.data.local.NetAlertDatabase

class NetAlertApp : Application() {

    val database by lazy { NetAlertDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel 1: Monitoring Status (Persistent, silent)
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "پایش وضعیت ارتباط (Monitoring Status)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت زنده پایش اتصال بین دو دستگاه"
                setShowBadge(false)
            }

            // Channel 2: Disconnection Alarm (Emergency Alert)
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "هشدار اضطراری قطع ارتباط (Disconnect Alarm)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "آژیر و نوتیفیکیشن با اولویت بالا هنگام قطع اینترنت طرف مقابل"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1000)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            notificationManager.createNotificationChannel(monitorChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    companion object {
        const val CHANNEL_MONITOR_ID = "netalert_monitor_channel"
        const val CHANNEL_ALARM_ID = "netalert_alarm_channel"
    }
}
