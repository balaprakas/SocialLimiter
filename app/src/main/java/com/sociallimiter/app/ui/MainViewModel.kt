package com.sociallimiter.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.data.MonitoredApp
import com.sociallimiter.app.util.InstalledApp
import com.sociallimiter.app.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

enum class AppStatus { IDLE, ACTIVE_SESSION, COOLDOWN }

data class MonitoredRow(
    val packageName: String,
    val label: String,
    val isEnabled: Boolean,
    val cooldownOverride: Int?,
    val status: AppStatus,
    val remainingMillis: Long,
)

data class UiState(
    val defaultCooldownMinutes: Int = 15,
    val monitored: List<MonitoredRow> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val monitoredPackages: Set<String> = emptySet(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LimiterRepository.get(app)

    private val installedFlow = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    val uiState: StateFlow<UiState> = combine(
        repo.monitoredApps,
        repo.cooldowns,
        repo.activeSessions,
        repo.defaultCooldownMinutes,
        combine(installedFlow, ticker) { installed, now -> installed to now },
    ) { monitored, cooldowns, sessions, defaultCooldown, installedAndNow ->
        val (installed, now) = installedAndNow
        val cooldownMap = cooldowns.associateBy { it.packageName }
        val sessionMap = sessions.associateBy { it.packageName }

        val rows = monitored.map { m ->
            val cd = cooldownMap[m.packageName]
            val session = sessionMap[m.packageName]
            when {
                cd != null && cd.unlockTimestamp > now ->
                    MonitoredRow(m.packageName, m.appName, m.isEnabled, m.cooldownMinutesOverride,
                        AppStatus.COOLDOWN, cd.unlockTimestamp - now)
                session != null && session.endTimestamp > now ->
                    MonitoredRow(m.packageName, m.appName, m.isEnabled, m.cooldownMinutesOverride,
                        AppStatus.ACTIVE_SESSION, session.endTimestamp - now)
                else ->
                    MonitoredRow(m.packageName, m.appName, m.isEnabled, m.cooldownMinutesOverride,
                        AppStatus.IDLE, 0L)
            }
        }
        UiState(
            defaultCooldownMinutes = defaultCooldown,
            monitored = rows,
            installedApps = installed,
            monitoredPackages = monitored.map { it.packageName }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                PackageUtils.listLaunchableApps(getApplication())
            }
            installedFlow.value = apps
        }
    }

    fun setMonitored(app: InstalledApp, monitored: Boolean) {
        viewModelScope.launch {
            if (monitored) {
                repo.upsertMonitored(MonitoredApp(app.packageName, app.label, isEnabled = true))
            } else {
                repo.removeMonitored(app.packageName)
            }
        }
    }

    fun setEnabled(row: MonitoredRow, enabled: Boolean) {
        viewModelScope.launch {
            val existing = repo.getMonitored(row.packageName) ?: return@launch
            repo.upsertMonitored(existing.copy(isEnabled = enabled))
        }
    }

    fun removeMonitored(row: MonitoredRow) {
        viewModelScope.launch { repo.removeMonitored(row.packageName) }
    }

    fun setDefaultCooldown(minutes: Int) {
        viewModelScope.launch { repo.setDefaultCooldownMinutes(minutes) }
    }

    fun setPerAppCooldown(row: MonitoredRow, minutes: Int?) {
        viewModelScope.launch {
            val existing = repo.getMonitored(row.packageName) ?: return@launch
            repo.upsertMonitored(existing.copy(cooldownMinutesOverride = minutes))
        }
    }
}
