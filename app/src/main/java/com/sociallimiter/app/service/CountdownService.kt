package com.sociallimiter.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.sociallimiter.app.data.ActiveSession
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.util.Notifications
import com.sociallimiter.app.util.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.ceil

/**
 * Foreground service that owns a single running usage session's countdown. It
 * shows a persistent notification with the remaining time (so it survives Doze /
 * background limits) and, when the timer reaches zero, asks [LimiterEngine] to
 * enforce the end-of-session cooldown.
 *
 * The countdown only runs while the monitored app is actually in the foreground.
 * When the user leaves the app [LimiterEngine] calls [requestPause], which freezes
 * the remaining time (and stops accruing daily-budget usage); reopening the app
 * revives the countdown from that remaining time via [start].
 *
 * The active session is persisted to Room (running or paused), so [BootReceiver]
 * can revive this service after a reboot or process death without losing time.
 */
class CountdownService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private lateinit var repo: LimiterRepository
    private lateinit var engine: LimiterEngine

    @Volatile private var currentPackage: String? = null
    private var currentLabel: String = ""
    @Volatile private var endAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        repo = LimiterRepository.get(this)
        engine = LimiterEngine(this)
        Notifications.ensureChannels(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            else -> {
                val pkg = intent?.getStringExtra(EXTRA_PACKAGE)
                val end = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L
                if (pkg == null || end <= 0L) {
                    stopSelfSafely()
                    return START_NOT_STICKY
                }
                startCountdown(pkg, end)
            }
        }
        return START_STICKY
    }

    private fun startCountdown(pkg: String, end: Long) {
        currentPackage = pkg
        currentLabel = PackageUtils.labelFor(packageManager, pkg)
        endAt = end
        isPaused = false
        activePackage = pkg
        startForeground(
            Notifications.COUNTDOWN_NOTIFICATION_ID,
            Notifications.buildCountdownNotification(this, currentLabel, remainingText(end)),
        )

        scope.launch { repo.startSession(pkg, end) }

        tickerJob?.cancel()
        tickerJob = scope.launch {
            // Accrue real elapsed foreground time toward the shared daily budget.
            // Only runs while the countdown is active, so time spent with the app
            // backgrounded (paused) is not charged against the budget.
            var lastAccrual = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                repo.addUsage(now - lastAccrual)
                lastAccrual = now

                // Daily budget exhausted mid-session: end now, block until midnight.
                if (repo.remainingBudgetMillis() <= 0L) {
                    engine.expireSession(pkg, LimiterEngine.EndReason.DAILY_BUDGET)
                    stopSelfSafely()
                    return@launch
                }
                if (now >= endAt) {
                    engine.expireSession(pkg, LimiterEngine.EndReason.TIMER)
                    stopSelfSafely()
                    return@launch
                }
                Notifications.updateCountdown(this@CountdownService, currentLabel, remainingText(endAt))
                delay(1000L)
            }
        }
    }

    /**
     * Freeze the countdown for the app the user just left. Persists the remaining
     * time so it can be resumed later (including after process death), stops the
     * ticker (halting budget accrual) and keeps the service alive showing a paused
     * notification. No-op if already paused or nothing is running.
     */
    private fun pauseInternal() {
        val pkg = currentPackage ?: return
        if (isPaused) return
        val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        tickerJob?.cancel()
        isPaused = true
        // Persist synchronously so a kill right after pausing doesn't lose the
        // paused remaining and silently resume a full-length session.
        runBlocking {
            repo.saveSession(ActiveSession(pkg, endAt, pausedRemainingMillis = remaining))
        }
        Notifications.updateCountdown(this, currentLabel, pausedText(remaining))
    }

    private fun remainingText(end: Long): String {
        val totalSeconds = ceil((end - System.currentTimeMillis()).coerceAtLeast(0) / 1000.0).toLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d remaining", minutes, seconds)
    }

    private fun pausedText(remainingMillis: Long): String {
        val totalSeconds = ceil(remainingMillis.coerceAtLeast(0) / 1000.0).toLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "Paused \u2014 %02d:%02d left", minutes, seconds)
    }

    private fun stopSelfSafely() {
        tickerJob?.cancel()
        activePackage = null
        isPaused = false
        currentPackage = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_END_AT = "extra_end_at"
        private const val ACTION_STOP = "com.sociallimiter.app.action.STOP_COUNTDOWN"

        @Volatile
        private var instance: CountdownService? = null

        /** Package whose countdown the service currently owns (running or paused). */
        @Volatile
        var activePackage: String? = null
            private set

        /** True when [activePackage]'s countdown is frozen (app backgrounded). */
        @Volatile
        var isPaused: Boolean = false
            private set

        /**
         * Start (or restart/resume) the countdown for [pkg] ending at [endAt]. Used
         * both for a fresh session and to revive a paused/persisted one from its
         * remaining time.
         */
        fun start(context: Context, pkg: String, endAt: Long) {
            val intent = Intent(context, CountdownService::class.java).apply {
                putExtra(EXTRA_PACKAGE, pkg)
                putExtra(EXTRA_END_AT, endAt)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CountdownService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Freeze the running countdown (the user left the monitored app). Handled
         * in-process on the live service instance so it can read the live deadline;
         * a no-op if the service isn't running.
         */
        fun requestPause() {
            instance?.pauseInternal()
        }
    }
}
