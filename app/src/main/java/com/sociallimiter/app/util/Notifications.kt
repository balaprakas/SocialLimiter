package com.sociallimiter.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.sociallimiter.app.R
import com.sociallimiter.app.ui.MainActivity

object Notifications {

    const val COUNTDOWN_CHANNEL_ID = "countdown"
    const val COUNTDOWN_NOTIFICATION_ID = 1001

    const val STATUS_CHANNEL_ID = "status"
    const val STATUS_NOTIFICATION_ID = 1002

    /** Broadcast action fired by the status notification's Pause/Resume button. */
    const val ACTION_TOGGLE_PAUSE = "com.sociallimiter.app.action.TOGGLE_PAUSE"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(COUNTDOWN_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                COUNTDOWN_CHANNEL_ID,
                context.getString(R.string.channel_countdown),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_countdown_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(STATUS_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                context.getString(R.string.channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_status_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Posts/updates the persistent Pause/Resume control notification. */
    fun postStatus(context: Context, paused: Boolean) {
        ensureChannels(context)
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.notify(STATUS_NOTIFICATION_ID, buildStatusNotification(context, paused))
    }

    fun cancelStatus(context: Context) {
        context.getSystemService<NotificationManager>()?.cancel(STATUS_NOTIFICATION_ID)
    }

    private fun buildStatusNotification(context: Context, paused: Boolean) =
        NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(
                context.getString(
                    if (paused) R.string.status_paused_title else R.string.status_active_title,
                ),
            )
            .setContentText(
                context.getString(
                    if (paused) R.string.status_paused_text else R.string.status_active_text,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .addAction(
                0,
                context.getString(
                    if (paused) R.string.status_action_resume else R.string.status_action_pause,
                ),
                toggleIntent(context),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun toggleIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_TOGGLE_PAUSE).apply {
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 1, intent, flags)
    }

    fun buildCountdownNotification(context: Context, appLabel: String, remaining: String) =
        NotificationCompat.Builder(context, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.countdown_title, appLabel))
            .setContentText(remaining)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun updateCountdown(context: Context, appLabel: String, remaining: String) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.notify(
            COUNTDOWN_NOTIFICATION_ID,
            buildCountdownNotification(context, appLabel, remaining),
        )
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}
