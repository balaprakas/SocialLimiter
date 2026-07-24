package com.sociallimiter.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A launchable installed app, used to populate the settings list. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

object PackageUtils {

    /**
     * All launchable apps except our own, sorted by label. This is what feeds the
     * settings screen — the user toggles any of these into the monitored list, so
     * adding "more apps later" needs no code change.
     */
    fun listLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        return resolved
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { pkg -> InstalledApp(pkg, labelFor(pm, pkg)) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun labelFor(pm: PackageManager, packageName: String): String =
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
}
