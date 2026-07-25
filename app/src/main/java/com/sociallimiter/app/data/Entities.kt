package com.sociallimiter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app the user has chosen to monitor. This is the sole source of "which apps
 * are limited" — there is no hardcoded package list anywhere in the code, so new
 * apps are added purely by toggling them on in the settings UI.
 *
 * @param cooldownMinutesOverride when non-null, overrides the global default
 *        cooldown for this specific package.
 */
@Entity(tableName = "monitored_app")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val cooldownMinutesOverride: Int? = null,
)

/**
 * Records that a package is currently locked out. The package stays locked until
 * [unlockTimestamp] (epoch millis) is reached. Persisted so a device reboot or
 * process death cannot bypass an active cooldown.
 */
@Entity(tableName = "cooldown_state")
data class CooldownState(
    @PrimaryKey val packageName: String,
    val unlockTimestamp: Long,
)

/**
 * A timed usage session that is currently running for a package. [endTimestamp]
 * (epoch millis) is when the allotted minutes expire. Persisted so the countdown
 * can be restored after process death / reboot.
 */
@Entity(tableName = "active_session")
data class ActiveSession(
    @PrimaryKey val packageName: String,
    val endTimestamp: Long,
)

/**
 * Accumulated foreground time across ALL monitored apps for a single local day.
 * [date] is an ISO `yyyy-MM-dd` string in the device's local time zone, so a new
 * day naturally starts with a fresh (absent) row — i.e. the budget resets at
 * midnight with no scheduled job.
 */
@Entity(tableName = "daily_usage")
data class DailyUsage(
    @PrimaryKey val date: String,
    val usedMillis: Long,
)

/**
 * A recurring blackout window during which every monitored app is blocked. The
 * window starts on each selected day of [daysMask] at [startMinuteOfDay] and ends
 * at [endMinuteOfDay]; if end <= start it is treated as crossing midnight into the
 * following day.
 *
 * @param daysMask bit i (0 = Monday .. 6 = Sunday) set means the window applies on
 *        that day (the day the window *starts*).
 * @param startMinuteOfDay minutes since local midnight, 0..1439.
 * @param endMinuteOfDay minutes since local midnight, 0..1439.
 */
@Entity(tableName = "schedule")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val daysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isEnabled: Boolean = true,
)
