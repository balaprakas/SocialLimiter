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
