package com.sociallimiter.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.data.MonitoredApp
import com.sociallimiter.app.data.Schedule
import com.sociallimiter.app.util.InstalledApp
import com.sociallimiter.app.util.PackageUtils
import com.sociallimiter.app.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val dailyBudgetMinutes: Int = 120,
    val remainingTodayMillis: Long = 0L,
    val monitored: List<MonitoredRow> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val monitoredPackages: Set<String> = emptySet(),
    val schedules: List<Schedule> = emptyList(),
    val globalBlockLabel: String? = null,
)

private data class Settings4(
    val cooldown: Int,
    val budget: Int,
    val usedMillis: Long,
    val schedules: List<Schedule>,
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

    private val settingsFlow = combine(
        repo.defaultCooldownMinutes,
        repo.dailyBudgetMinutes,
        repo.observeTodayUsage(),
        repo.schedules,
    ) { cooldown, budget, usage, schedules ->
        Settings4(cooldown, budget, usage?.usedMillis ?: 0L, schedules)
    }

    val uiState: StateFlow<UiState> = combine(
        repo.monitoredApps,
        repo.cooldowns,
        repo.activeSessions,
        settingsFlow,
        combine(installedFlow, ticker) { installed, now -> installed to now },
    ) { monitored, cooldowns, sessions, settings, installedAndNow ->
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
                session?.pausedRemainingMillis != null ->
                    MonitoredRow(m.packageName, m.appName, m.isEnabled, m.cooldownMinutesOverride,
                        AppStatus.ACTIVE_SESSION, session.pausedRemainingMillis)
                session != null && session.endTimestamp > now ->
                    MonitoredRow(m.packageName, m.appName, m.isEnabled, m.cooldownMinutesOverride,
                        AppStatus.ACTIVE_SESSION, session.endTimestamp - now)
                else ->
                    MonitoredRow(m.packageName, m.appName, m.isEnabled, m.cooldownMinutesOverride,
                        AppStatus.IDLE, 0L)
            }
        }

        val remainingToday =
            (settings.budget * 60_000L - settings.usedMillis).coerceAtLeast(0L)
        val activeWindow = TimeUtils.activeWindow(settings.schedules, now)
        val globalBlock = when {
            activeWindow != null ->
                "Scheduled break — no social apps until ${TimeUtils.formatClock(activeWindow.untilMillis)}"
            remainingToday <= 0L ->
                "Daily limit reached — resets at midnight"
            else -> null
        }

        UiState(
            defaultCooldownMinutes = settings.cooldown,
            dailyBudgetMinutes = settings.budget,
            remainingTodayMillis = remainingToday,
            monitored = rows,
            installedApps = installed,
            monitoredPackages = monitored.map { it.packageName }.toSet(),
            schedules = settings.schedules,
            globalBlockLabel = globalBlock,
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

    fun setDailyBudget(minutes: Int) {
        viewModelScope.launch { repo.setDailyBudgetMinutes(minutes) }
    }

    fun setPerAppCooldown(row: MonitoredRow, minutes: Int?) {
        viewModelScope.launch {
            val existing = repo.getMonitored(row.packageName) ?: return@launch
            repo.upsertMonitored(existing.copy(cooldownMinutesOverride = minutes))
        }
    }

    fun saveSchedule(schedule: Schedule) {
        viewModelScope.launch { repo.upsertSchedule(schedule) }
    }

    fun toggleSchedule(schedule: Schedule, enabled: Boolean) {
        viewModelScope.launch { repo.upsertSchedule(schedule.copy(isEnabled = enabled)) }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch { repo.deleteSchedule(schedule.id) }
    }
}
