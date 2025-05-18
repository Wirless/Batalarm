package com.example.batalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "battery_alarm_channel"
        const val ACTION_STOP_ALARM = "com.example.batalarm.STOP_ALARM"
        
        // Broadcast actions for syncing with UI
        const val ACTION_ALARM_STARTED = "com.example.batalarm.ALARM_STARTED"
        const val ACTION_ALARM_STOPPED = "com.example.batalarm.ALARM_STOPPED"
        
        // Flag to track if alarm is temporarily disabled
        var isTemporarilyDisabled = false
        
        // Track the last battery level when alarm was stopped
        var lastDisabledBatteryLevel = 0f
        
        // Reset temporary disable flag
        fun resetTemporaryDisable() {
            isTemporarilyDisabled = false
            lastDisabledBatteryLevel = 0f
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var preferencesManager: PreferencesManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        preferencesManager = PreferencesManager(this)
        
        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            // Temporarily disable alarm until battery recovers
            isTemporarilyDisabled = true
            
            // Get current battery level
            val batteryStatus = registerReceiver(null, Intent(Intent.ACTION_BATTERY_CHANGED).let { IntentFilter(it.action) })
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            
            if (level != -1 && scale != -1) {
                lastDisabledBatteryLevel = level * 100f / scale
                Log.d("AlarmService", "Alarm temporarily disabled at battery level: $lastDisabledBatteryLevel%")
            }
            
            stopSelf()
            return START_NOT_STICKY
        }

        // Create stop intent for notification
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent to open app and stop alarm when notification is tapped
        val contentIntent = Intent(this, MainActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        contentIntent.action = ACTION_STOP_ALARM
        
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create and show notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Low!")
            .setContentText("Tap to stop alarm and open app")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Alarm", stopPendingIntent)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)  // Remove notification when tapped
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        // Play alarm sound and start vibration
        playAlarmSound()
        startVibration()
        
        // Broadcast that alarm has started (for UI sync)
        sendAlarmStateBroadcast(true)

        return START_STICKY
    }

    override fun onDestroy() {
        // Stop playing sound and vibration
        stopAlarmSound()
        stopVibration()
        vibrationJob?.cancel()
        vibrationJob = null
        
        // Broadcast that alarm has stopped (for UI sync)
        sendAlarmStateBroadcast(false)
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for low battery alerts"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun playAlarmSound() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.chargeme)
            mediaPlayer?.isLooping = true
            
            // Set volume based on user preference (0.0 to 1.0)
            val volume = getVolumeFromPreferences()
            mediaPlayer?.setVolume(volume, volume)
            
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("AlarmService", "Error playing alarm sound", e)
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
    
    private fun getVolumeFromPreferences(): Float {
        // Default volume is 70%
        return preferencesManager?.getAlarmVolume() ?: 0.7f
    }
    
    private fun shouldVibrate(): Boolean {
        return preferencesManager?.isVibrationEnabled() ?: false
    }
    
    private fun getVibrationStrength(): Int {
        // Scale from 1-100 to appropriate vibration amplitude
        val strength = preferencesManager?.getVibrationStrength() ?: 50
        // Map the 1-100 range to something appropriate for vibration
        return (strength * 2.55).toInt().coerceIn(1, 255)
    }
    
    private fun startVibration() {
        if (!shouldVibrate()) return
        
        vibrationJob?.cancel()
        vibrationJob = coroutineScope.launch {
            val vibrationStrength = getVibrationStrength()
            
            while (true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(
                        1000, // 1 second
                        vibrationStrength // Amplitude (1-255)
                    )
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(1000) // For older devices
                }
                delay(2000) // Vibrate every 2 seconds
            }
        }
    }
    
    private fun stopVibration() {
        vibrationJob?.cancel()
        vibrator?.cancel()
    }
    
    private fun sendAlarmStateBroadcast(isPlaying: Boolean) {
        val intent = Intent(if (isPlaying) ACTION_ALARM_STARTED else ACTION_ALARM_STOPPED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
} 