package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.DeviceUtils
import com.example.util.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NetAlert", appName)
  }

  @Test
  fun `test duration formatting`() {
    assertEquals("45 ثانیه", DeviceUtils.formatDuration(45))
    assertEquals("2 دقیقه و 10 ثانیه", DeviceUtils.formatDuration(130))
  }

  @Test
  fun `test distance calculation and formatting`() {
    // Distance between Azadi Tower and Milad Tower in Tehran (~6.8 km)
    val distance = LocationHelper.calculateDistanceMeters(
      lat1 = 35.6997,
      lon1 = 51.3380,
      lat2 = 35.7448,
      lon2 = 51.3753
    )
    assertTrue("Distance should be between 5000 and 8000 meters", distance in 5000.0..8000.0)
    assertEquals("45 متر", LocationHelper.formatDistance(45.2))
  }

  @Test
  fun `test alarm silence latch state`() {
    val state = com.example.service.ServiceStateData(
      isAlarmRinging = false,
      isLocationAlarmRinging = false,
      isAlarmSilenced = true,
      connectionStatus = com.example.model.ConnectionStatus.MUTED_ALERT,
      isEmergencySmsEnabled = true,
      emergencyPhoneNumber = "09121234567"
    )
    assertTrue("Alarm should be marked as silenced", state.isAlarmSilenced)
    assertEquals(com.example.model.ConnectionStatus.MUTED_ALERT, state.connectionStatus)
    assertTrue("Emergency SMS should be enabled", state.isEmergencySmsEnabled)
    assertEquals("09121234567", state.emergencyPhoneNumber)
  }

  @Test
  fun `test emergency phone sanitization and validation`() {
    val sanitizedPersian = com.example.util.EmergencySmsHelper.sanitizePhoneNumber("۰۹۱۲-۳۴۵ ۶۷۸۹")
    assertEquals("09123456789", sanitizedPersian)
    assertTrue(com.example.util.EmergencySmsHelper.isValidPhoneNumber(sanitizedPersian))
    assertTrue(com.example.util.EmergencySmsHelper.isValidPhoneNumber("+989123456789"))
    org.junit.Assert.assertFalse(com.example.util.EmergencySmsHelper.isValidPhoneNumber("123"))
  }

  @Test
  fun `test emergency message formatting contains map link and details`() {
    val msg = com.example.util.EmergencySmsHelper.formatEmergencyMessage(
      deviceName = "Galaxy S23",
      reason = "قطع کامل اینترنت",
      latitude = 35.6892,
      longitude = 51.3890,
      batteryPercentage = 85,
      isLocalDevice = true
    )
    assertTrue("Message should contain emergency header", msg.contains("هشدار اضطراری"))
    assertTrue("Message should contain device name", msg.contains("Galaxy S23"))
    assertTrue("Message should contain Google Maps link", msg.contains("https://maps.google.com/?q=35.689200,51.389000"))
    assertTrue("Message should contain battery", msg.contains("85%"))
  }
}
