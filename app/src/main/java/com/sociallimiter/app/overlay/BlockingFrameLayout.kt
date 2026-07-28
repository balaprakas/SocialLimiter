package com.sociallimiter.app.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Root view for overlays that must not be dismissable by the system BACK button.
 * It swallows BACK (and the older MENU) key events so the only way out of an
 * overlay is the action button we provide (OK / take-me-home).
 */
class BlockingFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_MENU) {
            return true // consume, do nothing
        }
        return super.dispatchKeyEvent(event)
    }
}
