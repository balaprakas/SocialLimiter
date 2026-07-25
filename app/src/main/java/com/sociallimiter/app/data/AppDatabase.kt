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
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun cooldownDao(): CooldownDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun scheduleDao(): ScheduleDao

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

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "social_limiter.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
