package com.sociallimiter.app.ui

/** Per-day rollup used by the trend charts. */
data class DayBucket(
    val date: String,        // yyyy-MM-dd
    val label: String,       // e.g. "Mon"
    val dayOfMonth: String,  // e.g. "24"
    val usedMillis: Long,    // foreground time that day (all monitored apps)
    val opens: Int,          // sessions started that day
    val blocked: Int,        // blocked attempts that day
)

/** Per-app rollup over the visible window. */
data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val opens: Int,
    val abandoned: Int,
    val blockedSchedule: Int,
    val blockedCooldown: Int,
    val blockedBudget: Int,
) {
    val totalBlocked: Int get() = blockedSchedule + blockedCooldown + blockedBudget
}

data class DashboardState(
    val todayOpens: Int = 0,
    val todayBlocked: Int = 0,
    val todayUsedMillis: Long = 0L,
    val weekOpens: Int = 0,
    val weekBlocked: Int = 0,
    val weekAbandoned: Int = 0,
    val perApp: List<AppUsageStat> = emptyList(),
    val days: List<DayBucket> = emptyList(),  // last 7 days, oldest first
    val hasData: Boolean = false,
)
