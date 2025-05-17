package com.example.batalarm

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class BatteryMonitorWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val context = appContext
    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result {
        // Get user preferences
        val isMonitoringEnabled = preferencesManager.monitoringEnabledFlow.first()
        val isAlarmEnabled = preferencesManager.alarmEnabledFlow.first()
        val alarmPercentage = preferencesManager.batteryThresholdFlow.first()

        // If monitoring is disabled, don't continue
        if (!isMonitoringEnabled) {
            Log.d("BatteryMonitorWorker", "Battery monitoring disabled")
            return Result.success()
        }

        val batteryPct = getBatteryLevel()

        if (batteryPct != -1f) {
            Log.d("BatteryMonitorWorker", "Current Battery: $batteryPct%")

            // Check if alarm is temporarily disabled and if battery has recovered
            if (AlarmService.isTemporarilyDisabled) {
                // If battery level has increased by at least 5% from when it was disabled,
                // we consider it "recovered" and re-enable the alarm
                if (batteryPct >= AlarmService.lastDisabledBatteryLevel + 5) {
                    Log.d("BatteryMonitorWorker", "Battery recovered to $batteryPct%, re-enabling alarm")
                    AlarmService.isTemporarilyDisabled = false
                } else {
                    Log.d("BatteryMonitorWorker", "Alarm temporarily disabled, waiting for battery recovery")
                }
            }

            // Only start alarm if it's enabled, battery is below threshold, and not temporarily disabled
            if (isAlarmEnabled && batteryPct <= alarmPercentage && !AlarmService.isTemporarilyDisabled) {
                if (!isAlarmServiceRunning()) { // Prevent starting multiple times
                    Log.d("BatteryMonitorWorker", "Starting alarm service.")
                    val intent = Intent(context, AlarmService::class.java)
                    context.startForegroundService(intent)
                }
            } else {
                if (isAlarmServiceRunning()) {
                    Log.d("BatteryMonitorWorker", "Stopping alarm service.")
                    val intent = Intent(context, AlarmService::class.java)
                    context.stopService(intent)
                }
            }
        }

        // Dynamic Rescheduling
        val nextIntervalMinutes = if (batteryPct > 30f) {
            5L // Check every 5 minutes when battery is above 30%
        } else if (batteryPct > alarmPercentage) {
            // More sophisticated logic for 1-2 minutes can be added
            // For now, check more frequently as battery gets closer to threshold
            val range = 30f - alarmPercentage
            val position = batteryPct - alarmPercentage
            val ratio = position / range
            
            // Scale between 1 and 2 minutes based on how close to threshold
            (1 + ratio).toLong().coerceIn(1L, 2L)
        } else {
            1L // Check every minute when low or alarming
        }

        // Only reschedule if monitoring is still enabled
        if (isMonitoringEnabled) {
            val workRequest = OneTimeWorkRequestBuilder<BatteryMonitorWorker>()
                .setInitialDelay(nextIntervalMinutes, TimeUnit.MINUTES)
                .build()
                
            // Use unique work to replace any existing scheduled work
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "batteryMonitor",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d("BatteryMonitorWorker", "Rescheduled for $nextIntervalMinutes minutes")
        }

        return Result.success()
    }

    private fun getBatteryLevel(): Float {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
            context.registerReceiver(null, iFilter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            level * 100 / scale.toFloat()
        } else {
            -1f // Error or unknown
        }
    }

    // Helper to check if service is running
    private fun isAlarmServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AlarmService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}