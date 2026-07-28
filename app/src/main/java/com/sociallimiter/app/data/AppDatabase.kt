package com.sociallimiter.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MonitoredApp::class,
        CooldownState::class,
        ActiveSession::class,
        DailyUsage::class,
        Schedule::class,
        UsageEvent::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun cooldownDao(): CooldownDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun usageEventDao(): UsageEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_usage` " +
                        "(`date` TEXT NOT NULL, `usedMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`date`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule` " +
                        "(`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`daysMask` INTEGER NOT NULL, `startMinuteOfDay` INTEGER NOT NULL, " +
                        "`endMinuteOfDay` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `active_session` " +
                        "ADD COLUMN `pausedRemainingMillis` INTEGER",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_event` " +
                        "(`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, " +
                        "`extra` INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "social_limiter.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
    }
}
