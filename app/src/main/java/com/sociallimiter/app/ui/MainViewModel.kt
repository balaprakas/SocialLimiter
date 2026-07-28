package com.sociallimiter.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sociallimiter.app.data.DailyUsage
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.data.MonitoredApp
import com.sociallimiter.app.data.Schedule
import com.sociallimiter.app.data.UsageEvent
import com.sociallimiter.app.data.UsageEventType
import com.sociallimiter.app.util.InstalledApp
import com.sociallimiter.app.util.PackageUtils
import com.sociallimiter.app.util.TimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val enforcementPaused: Boolean = false,
)

private data class Settings5(
    val cooldown: Int,
    val budget: Int,
    val usedMillis: Long,
    val schedules: List<Schedule>,
    val paused: Boolean,
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
        repo.enforcementPaused,
    ) { cooldown, budget, usage, schedules, paused ->
        Settings5(cooldown, budget, usage?.usedMillis ?: 0L, schedules, paused)
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
            enforcementPaused = settings.paused,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    val dashboardState: StateFlow<DashboardState> = combine(
        repo.observeRecentEvents(),
        repo.observeDailyUsage(),
    ) { events, usage ->
        buildDashboard(events, usage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    private fun buildDashboard(events: List<UsageEvent>, usage: List<DailyUsage>): DashboardState {
        val today = TimeUtils.dayKey()
        val usageByDate = usage.associate { it.date to it.usedMillis }

        fun isBlocked(type: String) = type == UsageEventType.BLOCKED_SCHEDULE.name ||
            type == UsageEventType.BLOCKED_COOLDOWN.name ||
            type == UsageEventType.BLOCKED_BUDGET.name

        // Last 7 days (oldest first) for the trend charts.
        val labelFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val days = (6 downTo 0).map { back ->
            val date = LocalDate.now().minusDays(back.toLong())
            val key = date.format(TimeUtils.isoDate)
            val dayEvents = events.filter { TimeUtils.dayKey(it.timestampMillis) == key }
            DayBucket(
                date = key,
                label = date.format(labelFmt),
                dayOfMonth = date.dayOfMonth.toString(),
                usedMillis = usageByDate[key] ?: 0L,
                opens = dayEvents.count { it.type == UsageEventType.SESSION_START.name },
                blocked = dayEvents.count { isBlocked(it.type) },
            )
        }

        val perApp = events.groupBy { it.packageName }.map { (pkg, evs) ->
            AppUsageStat(
                packageName = pkg,
                appName = evs.firstOrNull()?.appName ?: pkg,
                opens = evs.count { it.type == UsageEventType.SESSION_START.name },
                abandoned = evs.count { it.type == UsageEventType.PROMPT_ABANDONED.name },
                blockedSchedule = evs.count { it.type == UsageEventType.BLOCKED_SCHEDULE.name },
                blockedCooldown = evs.count { it.type == UsageEventType.BLOCKED_COOLDOWN.name },
                blockedBudget = evs.count { it.type == UsageEventType.BLOCKED_BUDGET.name },
            )
        }.sortedByDescending { it.opens + it.totalBlocked }

        val todayEvents = events.filter { TimeUtils.dayKey(it.timestampMillis) == today }
        return DashboardState(
            todayOpens = todayEvents.count { it.type == UsageEventType.SESSION_START.name },
            todayBlocked = todayEvents.count { isBlocked(it.type) },
            todayUsedMillis = usageByDate[today] ?: 0L,
            weekOpens = days.sumOf { it.opens },
            weekBlocked = days.sumOf { it.blocked },
            weekAbandoned = events.filter { TimeUtils.dayKey(it.timestampMillis) >= days.first().date }
                .count { it.type == UsageEventType.PROMPT_ABANDONED.name },
            perApp = perApp,
            days = days,
            hasData = events.isNotEmpty(),
        )
    }

    fun setEnforcementPaused(paused: Boolean) {
        viewModelScope.launch { repo.setEnforcementPaused(paused) }
    }

    fun clearUsageHistory() {
        viewModelScope.launch { repo.clearUsageHistory() }
    }

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
