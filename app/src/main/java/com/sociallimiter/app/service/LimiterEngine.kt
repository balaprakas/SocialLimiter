package com.sociallimiter.app.service

import android.content.Context
import android.content.Intent
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.overlay.OverlayManager
import com.sociallimiter.app.util.TimeUtils

/**
 * Central decision logic shared by the accessibility service and the UsageStats
 * polling fallback. Given the current foreground package it decides whether to
 * show the "how many minutes?" prompt, bounce the user out with a "locked"
 * overlay, or do nothing (session in progress / not a monitored app).
 *
 * Enforcement precedence when a monitored app opens:
 *   1. Scheduled blackout window active  -> blocked until the window ends
 *   2. Cooldown active                   -> blocked until the cooldown ends
 *   3. Active timed session              -> allowed until it expires
 *   4. Daily budget exhausted            -> blocked until midnight
 *   5. Otherwise                         -> ask for minutes (capped to budget)
 *
 * All methods are suspend and expected to run on a background dispatcher; overlay
 * calls internally hop to the main thread.
 */
class LimiterEngine(context: Context) {

    private val appContext = context.applicationContext
    private val repo = LimiterRepository.get(appContext)

    /** Why a running session was ended, controlling what block follows it. */
    enum class EndReason { TIMER, DAILY_BUDGET }

    /** React to [pkg] coming to the foreground. */
    suspend fun onForeground(pkg: String) {
        val monitored = repo.getMonitored(pkg)
        if (monitored == null || !monitored.isEnabled) {
            // Left the monitored app (e.g. went to the launcher). Dismiss only the
            // "how many minutes?" prompt, since its target app is no longer up.
            // The locked overlay manages its own lifecycle, so leave it alone.
            OverlayManager.dismissPrompt()
            return
        }

        val now = System.currentTimeMillis()

        // 1. Scheduled blackout window blocks everything.
        val window = TimeUtils.activeWindow(repo.enabledSchedules(), now)
        if (window != null) {
            goHome()
            OverlayManager.showLocked(
                appContext, pkg, monitored.appName,
                "Scheduled break",
                "No social apps until ${TimeUtils.formatClock(window.untilMillis)}",
                window.untilMillis,
            ) { goHome() }
            return
        }

        // 2. Per-app cooldown.
        val cooldown = repo.getCooldown(pkg)
        if (cooldown != null) {
            if (now < cooldown.unlockTimestamp) {
                goHome()
                OverlayManager.showLocked(
                    appContext, pkg, monitored.appName,
                    "Time's up",
                    "Locked until ${TimeUtils.formatClock(cooldown.unlockTimestamp)}",
                    cooldown.unlockTimestamp,
                ) { goHome() }
                return
            }
            repo.clearCooldown(pkg)
        }

        // 3. An active timed session: allow use until it expires.
        val session = repo.getSession(pkg)
        if (session != null) {
            if (now < session.endTimestamp) {
                OverlayManager.dismissAll()
                return
            }
            // Session already expired but wasn't cleaned up (e.g. service killed).
            expireSession(pkg, EndReason.TIMER)
            return
        }

        // 4. Shared daily budget across all monitored apps.
        val remainingMillis = repo.remainingBudgetMillis()
        if (remainingMillis <= 0L) {
            goHome()
            OverlayManager.showLocked(
                appContext, pkg, monitored.appName,
                "Daily limit reached",
                "Social apps reset at midnight",
                TimeUtils.nextMidnightMillis(now),
            ) { goHome() }
            return
        }

        // 5. Fresh open: ask for a duration, capped to what's left today.
        val remainingMinutes = (remainingMillis / 60_000L).toInt().coerceAtLeast(1)
        OverlayManager.dismissLocked()
        OverlayManager.showPrompt(
            appContext, pkg, monitored.appName, remainingMinutes, remainingMinutes,
        ) { p, minutes -> beginSession(p, minutes) }
    }

    /** Called from the prompt's OK button: start a timed session + countdown. */
    fun beginSession(pkg: String, minutes: Int) {
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        CountdownService.start(appContext, pkg, endAt)
    }

    /**
     * Enforce end-of-session: clear the session, send the user home and show the
     * appropriate locked overlay. A [EndReason.TIMER] end opens the per-app
     * cooldown window; a [EndReason.DAILY_BUDGET] end blocks until midnight (the
     * daily-budget check re-enforces it on the next open). Safe to call repeatedly.
     */
    suspend fun expireSession(pkg: String, reason: EndReason = EndReason.TIMER) {
        val monitored = repo.getMonitored(pkg)
        val appName = monitored?.appName ?: pkg
        repo.clearSession(pkg)
        CountdownService.stop(appContext)
        goHome()

        when (reason) {
            EndReason.TIMER -> {
                val cooldownMinutes = repo.effectiveCooldownMinutes(pkg)
                val unlockAt = System.currentTimeMillis() + cooldownMinutes * 60_000L
                repo.startCooldown(pkg, unlockAt)
                OverlayManager.showLocked(
                    appContext, pkg, appName,
                    "Time's up",
                    "Locked until ${TimeUtils.formatClock(unlockAt)}",
                    unlockAt,
                ) { goHome() }
            }
            EndReason.DAILY_BUDGET -> {
                val midnight = TimeUtils.nextMidnightMillis()
                OverlayManager.showLocked(
                    appContext, pkg, appName,
                    "Daily limit reached",
                    "Social apps reset at midnight",
                    midnight,
                ) { goHome() }
            }
        }
    }

    fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(intent)
    }
}
