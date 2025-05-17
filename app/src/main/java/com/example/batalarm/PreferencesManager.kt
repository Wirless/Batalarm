package com.example.batalarm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property for Context to get the DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "battery_alarm_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        // Keys for preferences
        private val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        private val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        private val BATTERY_THRESHOLD = intPreferencesKey("battery_threshold")
        
        // Default values
        const val DEFAULT_BATTERY_THRESHOLD = 15
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

    // Update background monitoring enabled preference
    suspend fun updateMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MONITORING_ENABLED] = enabled
        }
    }

    // Update alarm enabled preference
    suspend fun updateAlarmEnabled(enabled: Boolean) {
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
} 