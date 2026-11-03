package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disconnect_logs")
data class DisconnectLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val roomId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long? = null,
    val reason: String,
    val remoteDeviceName: String,
    val remoteDeviceId: String,
    val batteryAtDisconnect: Int? = null,
    val networkTypeAtDisconnect: String? = null,
    val wasAlarmTriggered: Boolean = true,
    val latitudeAtDisconnect: Double? = null,
    val longitudeAtDisconnect: Double? = null
)
