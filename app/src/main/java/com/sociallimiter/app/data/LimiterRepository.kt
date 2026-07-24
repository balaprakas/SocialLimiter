package com.sociallimiter.app.data

import android.content.Context
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
        sessionDao.upsert(ActiveSession(pkg, endTimestamp))
    suspend fun clearSession(pkg: String) = sessionDao.clear(pkg)

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
        @Volatile
        private var INSTANCE: LimiterRepository? = null

        fun get(context: Context): LimiterRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LimiterRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
