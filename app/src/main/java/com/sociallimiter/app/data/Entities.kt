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
