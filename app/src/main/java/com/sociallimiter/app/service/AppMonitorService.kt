package com.sociallimiter.app.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
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
    private val repo by lazy { LimiterRepository.get(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastHandledPackage: String? = null

    /**
     * Packages that must never count as a foreground app change: our own overlays,
     * the system UI, and any installed input method. Opening the keyboard fires a
     * window-state-changed event for the IME's package; treating that as "left the
     * monitored app" would tear down the prompt (and its keyboard) mid-typing.
     */
    private val ignoredPackages: MutableSet<String> = mutableSetOf(
        "com.android.systemui",
        "android",
    )

    private fun refreshInputMethodPackages() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        runCatching {
            imm.enabledInputMethodList.forEach { ignoredPackages.add(it.packageName) }
            imm.inputMethodList.forEach { ignoredPackages.add(it.packageName) }
        }
    }

    private val poller = object : Runnable {
        override fun run() {
            pollForeground()
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRunning = true
        ignoredPackages.add(packageName)
        refreshInputMethodPackages()
        mainHandler.postDelayed(poller, POLL_INTERVAL_MS)
        observePauseState()
    }

    /**
     * Keeps the persistent Pause/Resume notification in sync with the flag and
     * applies the side effects when it flips (from either the notification action
     * or the in-app switch): pausing freezes a running countdown; resuming
     * re-evaluates the current foreground app so blocking/countdown pick back up.
     */
    private fun observePauseState() {
        scope.launch {
            repo.enforcementPaused.collectLatest { paused ->
                Notifications.postStatus(applicationContext, paused)
                if (paused) {
                    if (CountdownService.activePackage != null && !CountdownService.isPaused) {
                        CountdownService.requestPause()
                    }
                } else {
                    lastHandledPackage = null
                    queryForegroundPackage(this@AppMonitorService)?.let { engine.onForeground(it) }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        handlePackage(pkg)
    }

    private fun pollForeground() {
        val pkg = queryForegroundPackage(this) ?: return
        handlePackage(pkg)
    }

    private fun handlePackage(pkg: String) {
        if (pkg in ignoredPackages) return
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
        Notifications.cancelStatus(applicationContext)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L

        /** How far back to scan usage events when resolving the foreground app. */
        private const val FOREGROUND_LOOKBACK_MS = 60_000L

        @Volatile
        var instanceRunning: Boolean = false
            private set

        /**
         * Best-effort current foreground package via UsageStats (backup path).
         *
         * Uses the usage *event* stream rather than aggregated `queryUsageStats` +
         * `lastTimeUsed`: the latest MOVE_TO_FOREGROUND event reflects the app that
         * is actually on screen right now. The aggregate approach kept reporting a
         * monitored app as foreground for a while after the user had already left
         * it, which caused the prompt to reappear over the home screen.
         */
        fun queryForegroundPackage(context: Context): String? {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - FOREGROUND_LOOKBACK_MS, now) ?: return null
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    event.timeStamp >= latestTime
                ) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            return latestPackage
        }
    }
}
