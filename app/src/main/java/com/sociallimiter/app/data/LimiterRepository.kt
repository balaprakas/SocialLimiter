package com.sociallimiter.app.data

import android.content.Context
import com.sociallimiter.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow

/**
 * Single access point for all persisted limiter state. Holds the Room DAOs and
 * the settings store, and exposes the small set of operations the service and UI
 * layers need. Constructed via [get] so services/receivers share one instance.
 */
class LimiterRepository private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val monitoredDao = db.monitoredAppDao()
    private val cooldownDao = db.cooldownDao()
    private val sessionDao = db.activeSessionDao()
    private val dailyUsageDao = db.dailyUsageDao()
    private val scheduleDao = db.scheduleDao()
    private val usageEventDao = db.usageEventDao()
    private val settings = SettingsStore(context)

    // --- Monitored apps ---
    val monitoredApps: Flow<List<MonitoredApp>> = monitoredDao.observeAll()
    suspend fun enabledApps(): List<MonitoredApp> = monitoredDao.getEnabled()
    suspend fun getMonitored(pkg: String): MonitoredApp? = monitoredDao.get(pkg)
    suspend fun upsertMonitored(app: MonitoredApp) = monitoredDao.upsert(app)
    suspend fun removeMonitored(pkg: String) {
        monitoredDao.deleteByPackage(pkg)
        cooldownDao.clear(pkg)
        sessionDao.clear(pkg)
    }

    // --- Cooldown ---
    val cooldowns: Flow<List<CooldownState>> = cooldownDao.observeAll()
    suspend fun getCooldown(pkg: String): CooldownState? = cooldownDao.get(pkg)
    suspend fun startCooldown(pkg: String, unlockTimestamp: Long) =
        cooldownDao.upsert(CooldownState(pkg, unlockTimestamp))
    suspend fun clearCooldown(pkg: String) = cooldownDao.clear(pkg)
    suspend fun clearExpiredCooldowns(now: Long) = cooldownDao.clearExpired(now)

    // --- Active session ---
    val activeSessions: Flow<List<ActiveSession>> = sessionDao.observeAll()
    suspend fun activeSessionsSnapshot(): List<ActiveSession> = sessionDao.getAll()
    suspend fun getSession(pkg: String): ActiveSession? = sessionDao.get(pkg)
    suspend fun startSession(pkg: String, endTimestamp: Long) =
        sessionDao.upsert(ActiveSession(pkg, endTimestamp, pausedRemainingMillis = null))
    suspend fun saveSession(session: ActiveSession) = sessionDao.upsert(session)
    suspend fun clearSession(pkg: String) = sessionDao.clear(pkg)

    // --- Daily usage / budget ---
    val dailyBudgetMinutes: Flow<Int> = settings.dailyBudgetMinutes
    suspend fun setDailyBudgetMinutes(minutes: Int) = settings.setDailyBudgetMinutes(minutes)

    fun observeTodayUsage(): Flow<DailyUsage?> = dailyUsageDao.observe(TimeUtils.dayKey())

    suspend fun usedMillisToday(): Long = dailyUsageDao.getUsed(TimeUtils.dayKey()) ?: 0L

    /** Adds [deltaMillis] of foreground time to today's bucket, keeping history. */
    suspend fun addUsage(deltaMillis: Long) {
        if (deltaMillis <= 0) return
        val key = TimeUtils.dayKey()
        val current = dailyUsageDao.getUsed(key) ?: 0L
        dailyUsageDao.upsert(DailyUsage(key, current + deltaMillis))
        // Retain a rolling window of daily totals so the dashboard can chart trends.
        dailyUsageDao.deleteOlderThan(TimeUtils.dayKey(System.currentTimeMillis() - HISTORY_WINDOW_MS))
    }

    fun observeDailyUsage(): Flow<List<DailyUsage>> = dailyUsageDao.observeAll()

    /** Remaining daily budget in millis (>= 0). */
    suspend fun remainingBudgetMillis(): Long {
        val budgetMillis = settings.getDailyBudgetMinutes() * 60_000L
        return (budgetMillis - usedMillisToday()).coerceAtLeast(0L)
    }

    // --- Schedules ---
    val schedules: Flow<List<Schedule>> = scheduleDao.observeAll()
    suspend fun enabledSchedules(): List<Schedule> = scheduleDao.getEnabled()
    suspend fun upsertSchedule(schedule: Schedule) = scheduleDao.upsert(schedule)
    suspend fun deleteSchedule(id: Long) = scheduleDao.delete(id)

    // --- Usage events (dashboard) ---
    fun observeRecentEvents(): Flow<List<UsageEvent>> =
        usageEventDao.observeSince(System.currentTimeMillis() - HISTORY_WINDOW_MS)

    suspend fun logEvent(pkg: String, appName: String, type: UsageEventType, extra: Long = 0L) {
        usageEventDao.insert(
            UsageEvent(
                packageName = pkg,
                appName = appName,
                type = type.name,
                timestampMillis = System.currentTimeMillis(),
                extra = extra,
            ),
        )
        usageEventDao.pruneOlderThan(System.currentTimeMillis() - HISTORY_WINDOW_MS)
    }

    suspend fun clearUsageHistory() {
        usageEventDao.clearAll()
    }

    // --- Enforcement pause ---
    val enforcementPaused: Flow<Boolean> = settings.enforcementPaused
    suspend fun isEnforcementPaused(): Boolean = settings.isEnforcementPaused()
    suspend fun setEnforcementPaused(paused: Boolean) = settings.setEnforcementPaused(paused)

    // --- Settings ---
    val defaultCooldownMinutes: Flow<Int> = settings.defaultCooldownMinutes
    suspend fun setDefaultCooldownMinutes(minutes: Int) =
        settings.setDefaultCooldownMinutes(minutes)

    /** Resolves the effective cooldown (per-app override or global default) in minutes. */
    suspend fun effectiveCooldownMinutes(pkg: String): Int {
        val override = monitoredDao.get(pkg)?.cooldownMinutesOverride
        return override ?: settings.getDefaultCooldownMinutes()
    }

    companion object {
        /** Rolling retention window for daily totals and usage events (~90 days). */
        private const val HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000

        @Volatile
        private var INSTANCE: LimiterRepository? = null

        fun get(context: Context): LimiterRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LimiterRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
