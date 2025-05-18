package com.example.batalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 9999
        private const val MONITOR_CHANNEL_ID = "battery_monitor_channel"
        const val ACTION_STOP_MONITOR = "com.example.batalarm.STOP_MONITOR"
        private const val WAKELOCK_TAG = "BatalarmBatteryMonitor:WakeLock"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var monitoringJob: Job? = null
    private var preferencesManager: PreferencesManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        preferencesManager = PreferencesManager(this)
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITOR) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Start monitoring in foreground
        startForeground(NOTIFICATION_ID, createMonitoringNotification(getBatteryLevel().toInt()))
        startMonitoring()
        
        // If service is killed, restart it
        return START_STICKY
    }
    
    override fun onDestroy() {
        monitoringJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    val batteryLevel = getBatteryLevel()
                    val alarmThreshold = preferencesManager?.getBatteryThreshold()?.toFloat() ?: PreferencesManager.DEFAULT_BATTERY_THRESHOLD.toFloat()
                    val isAlarmEnabled = preferencesManager?.isAlarmEnabled() ?: true
                    
                    // Update notification with current battery level
                    updateNotification(batteryLevel.toInt())
                    
                    // Check if we need to sound the alarm
                    if (isAlarmEnabled && batteryLevel <= alarmThreshold && !AlarmService.isTemporarilyDisabled) {
                        if (!isAlarmServiceRunning()) {
                            Log.d("BatteryMonitorService", "Starting alarm service - battery at $batteryLevel%")
                            val alarmIntent = Intent(this@BatteryMonitorService, AlarmService::class.java)
                            startForegroundService(alarmIntent)
                        }
                    }
                    
                    // Determine check interval based on battery level
                    val checkIntervalMinutes = when {
                        batteryLevel > 30f -> 5L // Check every 5 minutes when above 30%
                        batteryLevel > alarmThreshold -> {
                            // Gradually check more frequently as the battery gets closer to the threshold
                            val range = 30f - alarmThreshold
                            val position = batteryLevel - alarmThreshold
                            val ratio = position / range
                            
                            // Scale between 1 and 2 minutes
                            (1 + ratio).toLong().coerceIn(1L, 2L)
                        }
                        else -> 1L // Check every minute when at or below threshold
                    }
                    
                    delay(checkIntervalMinutes * 60 * 1000) // Convert to milliseconds
                } catch (e: Exception) {
                    Log.e("BatteryMonitorService", "Error in monitoring loop", e)
                    delay(60 * 1000) // Wait a minute and try again on error
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Battery Monitor",
                NotificationManager.IMPORTANCE_LOW // LOW importance to avoid sound/vibration
            ).apply {
                description = "Persistent notification for battery monitoring"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createMonitoringNotification(batteryLevel: Int): Notification {
        // Intent to open app when notification is tapped
        val contentIntent = Intent(this, MainActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent to stop monitoring
        val stopIntent = Intent(this, BatteryMonitorService::class.java).apply {
            action = ACTION_STOP_MONITOR
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val threshold = preferencesManager?.getBatteryThreshold() 
            ?: PreferencesManager.DEFAULT_BATTERY_THRESHOLD
        
        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Battery Alarm")
            .setContentText("Monitoring: $batteryLevel% (Alarm set at $threshold%)")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Monitoring", stopPendingIntent)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun updateNotification(batteryLevel: Int) {
        val notification = createMonitoringNotification(batteryLevel)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun getBatteryLevel(): Float {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
            registerReceiver(null, iFilter)
        }
        
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level != -1 && scale != -1) {
            level * 100 / scale.toFloat()
        } else {
            -1f // Error or unknown
        }
    }
    
    private fun isAlarmServiceRunning(): Boolean {
        // Use modern approach to check if AlarmService is running
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return (applicationContext as? BatteryAlarmApplication)
                ?.isServiceRunning(AlarmService::class.java) == true
        } else {
            @Suppress("DEPRECATION")
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (AlarmService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                acquire(10*60*1000L /*10 minutes*/)
            }
        }
    }
    
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
        }
    }
} 