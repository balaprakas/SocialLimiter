package com.sociallimiter.app.service

import android.content.Context
import android.content.Intent
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.overlay.OverlayManager

/**
 * Central decision logic shared by the accessibility service and the UsageStats
 * polling fallback. Given the current foreground package it decides whether to
 * show the "how many minutes?" prompt, bounce the user out with a "locked"
 * overlay, or do nothing (session in progress / not a monitored app).
 *
 * All methods are suspend and expected to run on a background dispatcher; overlay
 * calls internally hop to the main thread.
 */
class LimiterEngine(context: Context) {

    private val appContext = context.applicationContext
    private val repo = LimiterRepository.get(appContext)

    /** React to [pkg] coming to the foreground. */
    suspend fun onForeground(pkg: String) {
        val monitored = repo.getMonitored(pkg)
        if (monitored == null || !monitored.isEnabled) {
            // Left the monitored app (e.g. went to the launcher). Dismiss only the
            // "how many minutes?" prompt, since its target app is no longer up.
            // The locked overlay is intentionally left alone: it is shown *over*
            // the home screen after a session ends and manages its own lifecycle
            // (its button / the cooldown timer), so a launcher event must not tear
            // it down.
            OverlayManager.dismissPrompt()
            return
        }

        val now = System.currentTimeMillis()

        // 1. Cooldown takes precedence over everything.
        val cooldown = repo.getCooldown(pkg)
        if (cooldown != null) {
            if (now < cooldown.unlockTimestamp) {
                goHome()
                OverlayManager.showLocked(
                    appContext, pkg, monitored.appName, cooldown.unlockTimestamp,
                ) { goHome() }
                return
            }
            repo.clearCooldown(pkg)
        }

        // 2. An active timed session: allow use until it expires.
        val session = repo.getSession(pkg)
        if (session != null) {
            if (now < session.endTimestamp) {
                OverlayManager.dismissAll()
                return
            }
            // Session already expired but wasn't cleaned up (e.g. service killed).
            expireSession(pkg)
            return
        }

        // 3. Fresh open, no cooldown, no session: ask for a duration.
        OverlayManager.dismissLocked()
        OverlayManager.showPrompt(
            appContext, pkg, monitored.appName,
        ) { p, minutes -> beginSession(p, minutes) }
    }

    /** Called from the prompt's OK button: start a timed session + countdown. */
    fun beginSession(pkg: String, minutes: Int) {
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        CountdownService.start(appContext, pkg, endAt)
    }

    /**
     * Enforce end-of-session: clear the session, open the cooldown window, send
     * the user home and show the locked overlay. Safe to call repeatedly.
     */
    suspend fun expireSession(pkg: String) {
        val monitored = repo.getMonitored(pkg)
        repo.clearSession(pkg)
        val cooldownMinutes = repo.effectiveCooldownMinutes(pkg)
        val unlockAt = System.currentTimeMillis() + cooldownMinutes * 60_000L
        repo.startCooldown(pkg, unlockAt)
        CountdownService.stop(appContext)
        goHome()
        OverlayManager.showLocked(
            appContext, pkg, monitored?.appName ?: pkg, unlockAt,
        ) { goHome() }
    }

    fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(intent)
    }
}
