package com.sociallimiter.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_app ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_app WHERE isEnabled = 1")
    suspend fun getEnabled(): List<MonitoredApp>

    @Query("SELECT * FROM monitored_app WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): MonitoredApp?

    @Upsert
    suspend fun upsert(app: MonitoredApp)

    @Delete
    suspend fun delete(app: MonitoredApp)

    @Query("DELETE FROM monitored_app WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}

@Dao
interface CooldownDao {
    @Query("SELECT * FROM cooldown_state")
    fun observeAll(): Flow<List<CooldownState>>

    @Query("SELECT * FROM cooldown_state WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): CooldownState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CooldownState)

    @Query("DELETE FROM cooldown_state WHERE packageName = :packageName")
    suspend fun clear(packageName: String)

    @Query("DELETE FROM cooldown_state WHERE unlockTimestamp <= :now")
    suspend fun clearExpired(now: Long)
}

@Dao
interface ActiveSessionDao {
    @Query("SELECT * FROM active_session")
    fun observeAll(): Flow<List<ActiveSession>>

    @Query("SELECT * FROM active_session")
    suspend fun getAll(): List<ActiveSession>

    @Query("SELECT * FROM active_session WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): ActiveSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ActiveSession)

    @Query("DELETE FROM active_session WHERE packageName = :packageName")
    suspend fun clear(packageName: String)

    @Query("DELETE FROM active_session")
    suspend fun clearAll()
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date LIMIT 1")
    fun observe(date: String): Flow<DailyUsage?>

    @Query("SELECT * FROM daily_usage ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyUsage>>

    @Query("SELECT usedMillis FROM daily_usage WHERE date = :date LIMIT 1")
    suspend fun getUsed(date: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: DailyUsage)

    // ISO yyyy-MM-dd sorts lexicographically, so a string compare prunes old days.
    @Query("DELETE FROM daily_usage WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}

@Dao
interface UsageEventDao {
    @Insert
    suspend fun insert(event: UsageEvent)

    @Query("SELECT * FROM usage_event WHERE timestampMillis >= :since ORDER BY timestampMillis DESC")
    fun observeSince(since: Long): Flow<List<UsageEvent>>

    @Query("DELETE FROM usage_event WHERE timestampMillis < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("DELETE FROM usage_event")
    suspend fun clearAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule ORDER BY startMinuteOfDay ASC")
    fun observeAll(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedule WHERE isEnabled = 1")
    suspend fun getEnabled(): List<Schedule>

    @Upsert
    suspend fun upsert(schedule: Schedule)

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun delete(id: Long)
}
