package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false
    private var previousVolume: Int? = null

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Synchronized
    fun startAlarm(forceMaxVolume: Boolean = true) {
        if (isPlaying) return
        isPlaying = true

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (forceMaxVolume && audioManager != null) {
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }

            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Failed to start media player alarm", e)
        }

        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    val pattern = longArrayOf(0, 1000, 400, 1000, 400, 1200, 500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(pattern, 0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Failed to vibrate", e)
        }
    }

    @Synchronized
    fun stopAlarm() {
        isPlaying = false

        try {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                } catch (e: Exception) {
                    Log.w("AlarmPlayer", "Error calling mp.stop", e)
                }
                try {
                    mp.reset()
                } catch (e: Exception) {
                    Log.w("AlarmPlayer", "Error calling mp.reset", e)
                }
                try {
                    mp.release()
                } catch (e: Exception) {
                    Log.w("AlarmPlayer", "Error calling mp.release", e)
                }
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Error cleaning up MediaPlayer", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Error cancelling vibration", e)
        }

        // Restore previous volume if changed
        previousVolume?.let { prevVol ->
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, prevVol, 0)
            } catch (e: Exception) {
                Log.e("AlarmPlayer", "Error restoring volume", e)
            }
            previousVolume = null
        }
    }

    fun isAlarmActive(): Boolean = isPlaying
}
