package com.sociallimiter.app.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Primary foreground-app detector. Reacts to TYPE_WINDOW_STATE_CHANGED events and
 * hands the foreground package to [LimiterEngine].
 *
 * Because accessibility events can be dropped and the service can be killed, a
 * defensive UsageStats poll runs every [POLL_INTERVAL_MS] as a backup, catching
 * any foreground change the event stream missed.
 */
class AppMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine by lazy { LimiterEngine(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastHandledPackage: String? = null

    private val poller = object : Runnable {
        override fun run() {
            pollForeground()
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRunning = true
        mainHandler.postDelayed(poller, POLL_INTERVAL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // ignore our own overlays
        handlePackage(pkg)
    }

    private fun pollForeground() {
        val pkg = queryForegroundPackage(this) ?: return
        if (pkg == packageName) return
        handlePackage(pkg)
    }

    private fun handlePackage(pkg: String) {
        // Deduplicate rapid repeats of the same package to avoid overlay churn,
        // but always let the engine re-evaluate a genuinely new foreground app.
        if (pkg == lastHandledPackage) return
        lastHandledPackage = pkg
        scope.launch { engine.onForeground(pkg) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instanceRunning = false
        mainHandler.removeCallbacks(poller)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L

        @Volatile
        var instanceRunning: Boolean = false
            private set

        /** Best-effort current foreground package via UsageStats (backup path). */
        fun queryForegroundPackage(context: Context): String? {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000L,
                now,
            ) ?: return null
            return stats.maxByOrNull { it.lastTimeUsed }?.packageName
        }
    }
}
