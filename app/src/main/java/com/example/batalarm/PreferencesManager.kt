package com.example.batalarm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Extension property for Context to get the DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "battery_alarm_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        // Keys for preferences
        private val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        private val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        private val BATTERY_THRESHOLD = intPreferencesKey("battery_threshold")
        private val ALARM_VOLUME = floatPreferencesKey("alarm_volume")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val VIBRATION_STRENGTH = intPreferencesKey("vibration_strength")
        private val FOREGROUND_SERVICE_ENABLED = booleanPreferencesKey("foreground_service_enabled")
        
        // Default values
        const val DEFAULT_BATTERY_THRESHOLD = 15
        const val DEFAULT_ALARM_VOLUME = 0.7f // 70%
        const val DEFAULT_VIBRATION_ENABLED = false
        const val DEFAULT_VIBRATION_STRENGTH = 50 // 50% strength
        const val DEFAULT_FOREGROUND_SERVICE_ENABLED = false
    }

    // Get background monitoring enabled preference
    val monitoringEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MONITORING_ENABLED] ?: false
        }

    // Get alarm enabled preference
    val alarmEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ALARM_ENABLED] ?: true
        }

    // Get battery threshold preference
    val batteryThresholdFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[BATTERY_THRESHOLD] ?: DEFAULT_BATTERY_THRESHOLD
        }
        
    // Get alarm volume preference
    val alarmVolumeFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[ALARM_VOLUME] ?: DEFAULT_ALARM_VOLUME
        }
        
    // Get vibration enabled preference
    val vibrationEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[VIBRATION_ENABLED] ?: DEFAULT_VIBRATION_ENABLED
        }
        
    // Get vibration strength preference
    val vibrationStrengthFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[VIBRATION_STRENGTH] ?: DEFAULT_VIBRATION_STRENGTH
        }
        
    // Get foreground service enabled preference
    val foregroundServiceEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[FOREGROUND_SERVICE_ENABLED] ?: DEFAULT_FOREGROUND_SERVICE_ENABLED
        }

    // Update background monitoring enabled preference
    suspend fun updateMonitoringEnabled(enabled: Boolean) {
        // When re-enabling monitoring, also clear any temporary disable flag
        if (enabled) {
            AlarmService.resetTemporaryDisable()
        }
        
        context.dataStore.edit { preferences ->
            preferences[MONITORING_ENABLED] = enabled
        }
    }

    // Update alarm enabled preference
    suspend fun updateAlarmEnabled(enabled: Boolean) {
        // When re-enabling alarm, also clear any temporary disable flag
        if (enabled) {
            AlarmService.resetTemporaryDisable()
        }
        
        context.dataStore.edit { preferences ->
            preferences[ALARM_ENABLED] = enabled
        }
    }

    // Update battery threshold preference
    suspend fun updateBatteryThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[BATTERY_THRESHOLD] = threshold
        }
    }
    
    // Update alarm volume preference
    suspend fun updateAlarmVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[ALARM_VOLUME] = volume
        }
    }
    
    // Update vibration enabled preference
    suspend fun updateVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED] = enabled
        }
    }
    
    // Update vibration strength preference
    suspend fun updateVibrationStrength(strength: Int) {
        context.dataStore.edit { preferences ->
            preferences[VIBRATION_STRENGTH] = strength
        }
    }
    
    // Update foreground service enabled preference
    suspend fun updateForegroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FOREGROUND_SERVICE_ENABLED] = enabled
        }
    }
    
    // Synchronous methods for services to use
    fun getAlarmVolume(): Float = runBlocking {
        alarmVolumeFlow.firstOrNull() ?: DEFAULT_ALARM_VOLUME
    }
    
    fun isVibrationEnabled(): Boolean = runBlocking {
        vibrationEnabledFlow.firstOrNull() ?: DEFAULT_VIBRATION_ENABLED
    }
    
    fun getVibrationStrength(): Int = runBlocking {
        vibrationStrengthFlow.firstOrNull() ?: DEFAULT_VIBRATION_STRENGTH
    }
    
    fun isAlarmEnabled(): Boolean = runBlocking {
        alarmEnabledFlow.firstOrNull() ?: true
    }
    
    fun getBatteryThreshold(): Int = runBlocking {
        batteryThresholdFlow.firstOrNull() ?: DEFAULT_BATTERY_THRESHOLD
    }
    
    fun isMonitoringEnabled(): Boolean = runBlocking {
        monitoringEnabledFlow.firstOrNull() ?: false
    }
    
    fun isForegroundServiceEnabled(): Boolean = runBlocking {
        foregroundServiceEnabledFlow.firstOrNull() ?: DEFAULT_FOREGROUND_SERVICE_ENABLED
    }
} 