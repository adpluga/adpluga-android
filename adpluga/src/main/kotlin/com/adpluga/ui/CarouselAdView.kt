package com.adpluga.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.adpluga.consent.AppContextHolder
import com.adpluga.model.Slide

private const val CAPTION_BACKGROUND = "#D1111827"
private const val CTA_BACKGROUND = "#FFFFFF"
private const val CTA_FOREGROUND = "#111827"
private const val SETTLE_DELAY_MS = 60L

/**
 * Renders a carousel deck as a snapping horizontal track. Built on the
 * framework's own [HorizontalScrollView] so the SDK keeps its dependency-free
 * footprint — an ad SDK that drags ViewPager2 into every host app is a cost
 * the integrator did not ask for.
 *
 * The deck is one advertiser and one auction: every card reports the same tap
 * through [onClick] and the view never requests another creative. [onSwipe]
 * fires whenever the settled card changes so the host can hold off a scheduled
 * rotation instead of yanking the deck out from under the reader.
 */
internal class CarouselAdView(
    context: Context,
    private val slides: List<Slide>,
    private val fallbackLabel: String,
    private val onClick: () -> Unit,
    private val onSwipe: () -> Unit,
) : HorizontalScrollView(context) {

    private val track = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    private val cards = mutableListOf<FrameLayout>()
    private var page = 0
    private val settle = Runnable { snapToNearest() }

    init {
        AppContextHolder.remember(context)
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(
            track,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(bitmaps: List<Bitmap?>) {
        track.removeAllViews()
        cards.clear()
        for (i in slides.indices) {
            val card = buildCard(slides[i], bitmaps.getOrNull(i))
            cards += card
            track.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Each card is exactly one viewport wide, which is what makes the
        // snap arithmetic below a plain division.
        for (card in cards) {
            val lp = card.layoutParams as LinearLayout.LayoutParams
            if (lp.width != w) {
                lp.width = w
                card.layoutParams = lp
            }
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            removeCallbacks(settle)
            postDelayed(settle, SETTLE_DELAY_MS)
        }
        return handled
    }

    override fun fling(velocityX: Int) {
        // A free fling would land between cards. Advance exactly one card in
        // the direction of the gesture instead, so the deck always rests on a
        // whole creative.
        val step = if (velocityX > 0) 1 else -1
        scrollToPage((page + step).coerceIn(0, (cards.size - 1).coerceAtLeast(0)))
    }

    private fun snapToNearest() {
        val w = width
        if (w <= 0 || cards.isEmpty()) return
        scrollToPage(((scrollX + w / 2) / w).coerceIn(0, cards.size - 1))
    }

    private fun scrollToPage(target: Int) {
        val w = width
        if (w <= 0) return
        smoothScrollTo(target * w, 0)
        if (target != page) {
            page = target
            onSwipe()
        }
    }

    private fun buildCard(slide: Slide, bitmap: Bitmap?): FrameLayout {
        val card = FrameLayout(context)
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = slide.title?.takeIf { it.isNotBlank() } ?: fallbackLabel
            if (bitmap != null) setImageBitmap(bitmap)
        }
        card.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        buildCaption(slide)?.let {
            card.addView(
                it,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.BOTTOM },
            )
        }
        card.isClickable = true
        card.setOnClickListener { onClick() }
        return card
    }

    private fun buildCaption(slide: Slide): View? {
        val title = slide.title?.takeIf { it.isNotEmpty() }
        val body = slide.body?.takeIf { it.isNotEmpty() }
        val cta = slide.ctaText?.takeIf { it.isNotEmpty() }
        if (title == null && body == null && cta == null) return null

        val density = resources.displayMetrics.density
        val pad = (8 * density).toInt()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(CAPTION_BACKGROUND))
            setPadding(pad, (6 * density).toInt(), pad, (6 * density).toInt())
            isClickable = false
            isFocusable = false
        }
        if (title != null) {
            box.addView(
                TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                },
            )
        }
        if (body != null) {
            box.addView(
                TextView(context).apply {
                    text = body
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    maxLines = 2
                },
            )
        }
        if (cta != null) {
            box.addView(
                TextView(context).apply {
                    text = cta
                    setTextColor(Color.parseColor(CTA_FOREGROUND))
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    val h = (6 * density).toInt()
                    val v = (2 * density).toInt()
                    setPadding(h, v, h, v)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(CTA_BACKGROUND))
                        cornerRadius = 3f * density
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (4 * density).toInt() },
            )
        }
        return box
    }
}
