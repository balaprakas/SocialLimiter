package com.sociallimiter.app

import android.app.Application
import com.sociallimiter.app.data.LimiterRepository
import com.sociallimiter.app.data.MonitoredApp
import com.sociallimiter.app.util.Notifications
import com.sociallimiter.app.util.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SocialLimiterApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        seedStarterAppsOnFirstRun()
    }

    /**
     * One-time convenience seed. The monitored list is fully user-managed from the
     * settings screen; this only pre-adds the well-known social apps *if they are
     * installed*, so the app is useful out of the box. It is NOT the extensibility
     * mechanism — users add/remove any app from the UI without code changes.
     */
    private fun seedStarterAppsOnFirstRun() {
        val prefs = getSharedPreferences("bootstrap", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        scope.launch {
            val repo = LimiterRepository.get(this@SocialLimiterApp)
            val installed = PackageUtils.listLaunchableApps(this@SocialLimiterApp)
                .associateBy { it.packageName }
            for (pkg in STARTER_PACKAGES) {
                val app = installed[pkg] ?: continue
                if (repo.getMonitored(pkg) == null) {
                    repo.upsertMonitored(MonitoredApp(pkg, app.label, isEnabled = true))
                }
            }
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    companion object {
        private const val KEY_SEEDED = "starter_seeded"
        private val STARTER_PACKAGES = listOf(
            "com.facebook.katana",   // Facebook
            "com.instagram.android", // Instagram
            "com.reddit.frontpage",  // Reddit
            "com.twitter.android",   // X (Twitter)
            "com.zhiliaoapp.musically", // TikTok (common request)
        )
    }
}
