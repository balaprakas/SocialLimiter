package com.sociallimiter.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.service.CountdownService
import com.sociallimiter.app.service.LimiterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores limiter state after a reboot. Cooldowns live in Room and are simply
 * re-checked the next time an app is opened, so nothing is needed for them here.
 * For any active timed session that survived the reboot we either revive the
 * countdown service (deadline still in the future) or enforce the end-of-session
 * cooldown immediately (deadline already passed while powered off).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        val repo = LimiterRepository.get(appContext)
        val engine = LimiterEngine(appContext)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val now = System.currentTimeMillis()
                repo.clearExpiredCooldowns(now)
                for (session in repo.activeSessionsSnapshot()) {
                    if (session.endTimestamp > now) {
                        CountdownService.start(appContext, session.packageName, session.endTimestamp)
                    } else {
                        engine.expireSession(session.packageName)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
