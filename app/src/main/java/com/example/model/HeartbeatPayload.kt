package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HeartbeatPayload(
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val timestamp: Long,
    val sequenceNumber: Long,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val networkType: String, // "WIFI", "CELLULAR", "ETHERNET", "UNKNOWN"
    val isEmergencyWill: Boolean = false,
    val message: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null
)
