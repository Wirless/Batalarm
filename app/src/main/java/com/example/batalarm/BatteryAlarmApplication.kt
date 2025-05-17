package com.example.batalarm

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer

class BatteryAlarmApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        // Initialize work manager here
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        // Convenience method to start monitoring from MainActivity
        fun startMonitoring(context: Context) {
            // Start the battery monitoring worker immediately
            val workRequest = OneTimeWorkRequestBuilder<BatteryMonitorWorker>()
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "batteryMonitor",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        // Convenience method to stop monitoring from MainActivity
        fun stopMonitoring(context: Context) {
            // Cancel any pending work
            WorkManager.getInstance(context).cancelUniqueWork("batteryMonitor")
        }
    }
} 