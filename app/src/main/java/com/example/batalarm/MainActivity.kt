package com.example.batalarm

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.batalarm.ui.theme.BatalarmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private var isAlarmServiceRunning = false

    // Request post notifications permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        preferencesManager = PreferencesManager(this)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check if alarm service is running
        lifecycleScope.launch {
            while (true) {
                val batteryState = getCurrentBatteryState()
                isAlarmServiceRunning = isAlarmServiceRunning()
                delay(2000) // Update every 2 seconds
            }
        }
        
        setContent {
            BatalarmTheme {
                BatteryAlarmApp(
                    preferencesManager = preferencesManager,
                    getBatteryState = { getCurrentBatteryState() },
                    isAlarmRunning = isAlarmServiceRunning,
                    onStopAlarm = { stopAlarmService() },
                    onToggleMonitoring = { enabled ->
                        if (enabled) {
                            BatteryAlarmApplication.startMonitoring(this)
                        } else {
                            BatteryAlarmApplication.stopMonitoring(this)
                        }
                    }
                )
            }
        }
    }

    private fun getCurrentBatteryState(): BatteryState {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
            registerReceiver(null, iFilter)
        }

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        
        val batteryPct = if (level != -1 && scale != -1) level * 100f / scale else 0f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryState(batteryPct, isCharging)
    }

    private fun isAlarmServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AlarmService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java)
        intent.action = AlarmService.ACTION_STOP_ALARM
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryAlarmApp(
    preferencesManager: PreferencesManager,
    getBatteryState: () -> BatteryState,
    isAlarmRunning: Boolean,
    onStopAlarm: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit
) {
    // Collect preferences
    val monitoringEnabled by preferencesManager.monitoringEnabledFlow.collectAsState(initial = false)
    val alarmEnabled by preferencesManager.alarmEnabledFlow.collectAsState(initial = true)
    val alarmThreshold by preferencesManager.batteryThresholdFlow.collectAsState(initial = PreferencesManager.DEFAULT_BATTERY_THRESHOLD)
    
    // Get battery state
    var batteryState by remember { mutableStateOf(BatteryState(0f, false)) }
    var sliderPosition by remember { mutableFloatStateOf(alarmThreshold.toFloat()) }
    
    // Update battery state periodically
    LaunchedEffect(key1 = Unit) {
        while(true) {
            batteryState = getBatteryState()
            delay(2000)
        }
    }
    
    // Update slider when settings change
    LaunchedEffect(key1 = alarmThreshold) {
        sliderPosition = alarmThreshold.toFloat()
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (isAlarmRunning) {
                FloatingActionButton(
                    onClick = onStopAlarm,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    containerColor = Color.Red,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Stop Alarm",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Battery status card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Battery Level",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "${DecimalFormat("#.#").format(batteryState.level)}%",
                        style = MaterialTheme.typography.displayMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (batteryState.isCharging) "Charging" else "Not Charging",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Enable background monitoring
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Enable Background Monitoring")
                Switch(
                    checked = monitoringEnabled,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            preferencesManager.updateMonitoringEnabled(checked)
                            onToggleMonitoring(checked)
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Enable alarm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Enable Alarm")
                Switch(
                    checked = alarmEnabled,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            preferencesManager.updateAlarmEnabled(checked)
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Battery threshold
            Text(
                text = "Battery Alarm Threshold: ${sliderPosition.toInt()}%",
                modifier = Modifier.align(Alignment.Start)
            )
            
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 5f..50f,
                steps = 45,
                onValueChangeFinished = {
                    coroutineScope.launch {
                        // Save the new threshold without affecting monitoring state
                        preferencesManager.updateBatteryThreshold(sliderPosition.toInt())
                        
                        // If monitoring is enabled, make sure it stays on with the new threshold
                        if (monitoringEnabled) {
                            onToggleMonitoring(true)
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}