package com.adpluga.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

// addTestBadge draws a small, non-interactive "TEST" marker in the top-left of
// the surface so sandbox creatives (served by a pk_test_ key) are visually
// distinguishable. The badge never intercepts touches, so clicks fall through
// to the creative below.
internal fun addTestBadge(parent: FrameLayout): TextView {
    val density = parent.resources.displayMetrics.density
    val badge = TextView(parent.context).apply {
        text = "TEST"
        setTextColor(Color.WHITE)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        isClickable = false
        isFocusable = false
        val padH = (6 * density).toInt()
        val padV = (2 * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#B45309"))
            cornerRadius = 4f * density
        }
    }
    val margin = (4 * density).toInt()
    parent.addView(
        badge,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(margin, margin, margin, margin)
        },
    )
    return badge
}
