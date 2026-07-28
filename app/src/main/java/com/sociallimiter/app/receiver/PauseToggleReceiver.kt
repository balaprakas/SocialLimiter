package com.sociallimiter.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the Pause/Resume action from the persistent status notification. It only
 * flips the global [LimiterRepository.setEnforcementPaused] flag (and refreshes the
 * notification for instant feedback); the accessibility service observes that flag
 * and applies the actual pause/resume side effects. Nothing else is touched, so all
 * saved config — monitored apps, schedules, budget, cooldowns, in-progress session —
 * survives and resumes exactly as it was.
 */
class PauseToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_TOGGLE_PAUSE) return

        val appContext = context.applicationContext
        val pending = goAsync()
        val repo = LimiterRepository.get(appContext)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val nowPaused = !repo.isEnforcementPaused()
                repo.setEnforcementPaused(nowPaused)
                Notifications.postStatus(appContext, nowPaused)
            } finally {
                pending.finish()
            }
        }
    }
}
