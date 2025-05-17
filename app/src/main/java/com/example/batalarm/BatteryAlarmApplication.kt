package com.example.batalarm

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer
import java.util.concurrent.atomic.AtomicBoolean

class BatteryAlarmApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        // Initialize work manager here
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
            
    // Modern method to check if a service is running
    fun <T : Service> isServiceRunning(serviceClass: Class<T>): Boolean {
        val isRunning = AtomicBoolean(false)
        val waitForConnection = AtomicBoolean(true)
        
        val intent = Intent(this, serviceClass)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isRunning.set(true)
                waitForConnection.set(false)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isRunning.set(false)
                waitForConnection.set(false)
            }
        }
        
        // Try to bind to the service - will succeed only if it's running
        val canBind = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (canBind) {
            // If we could bind, let's wait a moment for the connection to be established
            try {
                var attempts = 0
                while (waitForConnection.get() && attempts < 10) {
                    Thread.sleep(10)
                    attempts++
                }
            } catch (e: InterruptedException) {
                // Ignore
            } finally {
                // Don't forget to unbind
                unbindService(connection)
            }
        }
        
        return isRunning.get()
    }

    companion object {
        // Convenience method to start monitoring from MainActivity
        fun startMonitoring(context: Context) {
            // Reset any temporary disables when monitoring is started
            AlarmService.resetTemporaryDisable()
            
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