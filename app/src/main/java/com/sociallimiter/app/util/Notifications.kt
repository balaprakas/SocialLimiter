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
