package com.sociallimiter.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Global, app-wide settings. Currently just the default cooldown duration used
 * when a monitored app has no per-app override.
 */
class SettingsStore(private val context: Context) {

    val defaultCooldownMinutes: Flow<Int> =
        context.dataStore.data.map { it[KEY_DEFAULT_COOLDOWN] ?: DEFAULT_COOLDOWN_MINUTES }

    suspend fun getDefaultCooldownMinutes(): Int =
        defaultCooldownMinutes.first()

    suspend fun setDefaultCooldownMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_DEFAULT_COOLDOWN] = minutes.coerceAtLeast(1) }
    }

    val dailyBudgetMinutes: Flow<Int> =
        context.dataStore.data.map { it[KEY_DAILY_BUDGET] ?: DEFAULT_DAILY_BUDGET_MINUTES }

    suspend fun getDailyBudgetMinutes(): Int =
        dailyBudgetMinutes.first()

    suspend fun setDailyBudgetMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_DAILY_BUDGET] = minutes.coerceAtLeast(1) }
    }

    /** Global "protection paused" switch, toggled from the persistent notification. */
    val enforcementPaused: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ENFORCEMENT_PAUSED] ?: false }

    suspend fun isEnforcementPaused(): Boolean =
        enforcementPaused.first()

    suspend fun setEnforcementPaused(paused: Boolean) {
        context.dataStore.edit { it[KEY_ENFORCEMENT_PAUSED] = paused }
    }

    companion object {
        const val DEFAULT_COOLDOWN_MINUTES = 15
        const val DEFAULT_DAILY_BUDGET_MINUTES = 120
        private val KEY_DEFAULT_COOLDOWN = intPreferencesKey("default_cooldown_minutes")
        private val KEY_DAILY_BUDGET = intPreferencesKey("daily_budget_minutes")
        private val KEY_ENFORCEMENT_PAUSED = booleanPreferencesKey("enforcement_paused")
    }
}
