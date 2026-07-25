package com.sociallimiter.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.sociallimiter.app.R
import java.util.Locale
import kotlin.math.ceil

/**
 * Owns the two WindowManager-added overlay views (the "how many minutes?" prompt
 * and the "locked" screen). All view manipulation is marshalled onto the main
 * thread. Both overlays are full-screen, opaque and focus-grabbing so the app
 * underneath is fully blocked, and their root [BlockingFrameLayout] eats the BACK
 * key so they cannot be dismissed except through the button we expose.
 */
object OverlayManager {

    private val main = Handler(Looper.getMainLooper())

    private var promptView: View? = null
    private var promptPackage: String? = null

    private var lockedView: View? = null
    private var lockedPackage: String? = null
    private var lockedUnlockAt: Long = 0L
    private val lockedTicker = object : Runnable {
        override fun run() {
            val view = lockedView ?: return
            val remaining = lockedUnlockAt - System.currentTimeMillis()
            if (remaining <= 0) {
                dismissLocked()
                return
            }
            view.findViewById<TextView>(R.id.lockedRemaining)?.text = formatMs(remaining)
            main.postDelayed(this, 1000L)
        }
    }

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    private fun wm(context: Context): WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun baseParams(focusable: Boolean): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    /** Callback invoked with (packageName, minutes) when the user confirms the prompt. */
    fun interface OnMinutesConfirmed {
        fun onConfirmed(packageName: String, minutes: Int)
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    fun showPrompt(
        context: Context,
        packageName: String,
        appName: String,
        maxMinutes: Int,
        remainingTodayMinutes: Int,
        onConfirmed: OnMinutesConfirmed,
    ) {
        main.post {
            if (!canDrawOverlays(context)) return@post
            // Already showing for the same package: leave it be.
            if (promptView != null && promptPackage == packageName) return@post
            dismissPromptInternal(context)

            val cap = maxMinutes.coerceIn(1, MAX_MINUTES)
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_prompt, null)
            view.findViewById<TextView>(R.id.promptAppName).text = appName
            view.findViewById<TextView>(R.id.promptBudget).text =
                "$remainingTodayMinutes min left today"
            val input = view.findViewById<EditText>(R.id.promptMinutes)
            val error = view.findViewById<TextView>(R.id.promptError)
            error.text = "Enter 1-$cap minutes"
            view.findViewById<Button>(R.id.promptOk).setOnClickListener {
                val minutes = input.text.toString().toIntOrNull()
                if (minutes == null || minutes < 1 || minutes > cap) {
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                dismissPrompt()
                onConfirmed.onConfirmed(packageName, minutes)
            }
            try {
                wm(context).addView(view, baseParams(focusable = true))
                promptView = view
                promptPackage = packageName
                input.requestFocus()
            } catch (_: Exception) {
                // View may already be attached, or permission revoked mid-flight.
            }
        }
    }

    fun dismissPrompt() {
        main.post { promptView?.let { dismissPromptInternal(it.context) } }
    }

    private fun dismissPromptInternal(context: Context) {
        promptView?.let {
            try {
                wm(context).removeView(it)
            } catch (_: Exception) {
            }
        }
        promptView = null
        promptPackage = null
    }

    @SuppressLint("InflateParams")
    fun showLocked(
        context: Context,
        packageName: String,
        appName: String,
        title: String,
        subtitle: String,
        unlockAt: Long,
        onHome: () -> Unit,
    ) {
        main.post {
            if (!canDrawOverlays(context)) return@post
            lockedUnlockAt = unlockAt
            if (lockedView != null && lockedPackage == packageName) {
                // Refresh text + countdown target and keep the same view.
                lockedView?.let {
                    it.findViewById<TextView>(R.id.lockedTitle).text = title
                    it.findViewById<TextView>(R.id.lockedSubtitle).text = subtitle
                    it.findViewById<TextView>(R.id.lockedAppName).text = appName
                }
                main.removeCallbacks(lockedTicker)
                main.post(lockedTicker)
                return@post
            }
            dismissLockedInternal(context)

            val view = LayoutInflater.from(context).inflate(R.layout.overlay_locked, null)
            view.findViewById<TextView>(R.id.lockedTitle).text = title
            view.findViewById<TextView>(R.id.lockedSubtitle).text = subtitle
            view.findViewById<TextView>(R.id.lockedAppName).text = appName
            view.findViewById<TextView>(R.id.lockedRemaining).text =
                formatMs(unlockAt - System.currentTimeMillis())
            view.findViewById<Button>(R.id.lockedHome).setOnClickListener {
                onHome()
                dismissLocked()
            }
            try {
                wm(context).addView(view, baseParams(focusable = false))
                lockedView = view
                lockedPackage = packageName
                main.post(lockedTicker)
            } catch (_: Exception) {
            }
        }
    }

    fun dismissLocked() {
        main.post { lockedView?.let { dismissLockedInternal(it.context) } }
    }

    private fun dismissLockedInternal(context: Context) {
        main.removeCallbacks(lockedTicker)
        lockedView?.let {
            try {
                wm(context).removeView(it)
            } catch (_: Exception) {
            }
        }
        lockedView = null
        lockedPackage = null
    }

    /** Dismiss any overlay currently attached to [exceptPackage] is kept. */
    fun dismissAll() {
        dismissPrompt()
        dismissLocked()
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private const val MAX_MINUTES = 180
}
