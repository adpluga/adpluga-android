package com.adpluga.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.MainThread
import com.adpluga.AdListener
import com.adpluga.AdPluga
import com.adpluga.config.Constants
import com.adpluga.errors.AdPlugaError
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.model.Ad
import com.adpluga.model.AdKind
import com.adpluga.model.ServeResponse
import com.adpluga.viewability.ViewabilityTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL

public class AdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imageView: ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
    }
    private var htmlView: HtmlAdView? = null
    private var videoView: VideoAdView? = null
    private var carouselView: CarouselAdView? = null

    private var listener: AdListener? = null
    private var loadJob: Job? = null
    private var viewabilityHandle: Int = 0
    private var boundResponse: ServeResponse? = null
    private var slotId: String? = null
    private var format: String? = null
    private var impressionFired: Boolean = false
    private var refreshSeq: Int = 0
    private var refreshRunnable: Runnable? = null
    private var testBadge: View? = null
    private var lastDeckSwipeAt: Long = 0L

    init {
        addView(
            imageView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    @MainThread
    public fun load(slotId: String, format: String? = null, listener: AdListener? = null) {
        cancelInternal()
        this.slotId = slotId
        this.format = format
        this.listener = listener
        refreshSeq = 0
        reload()
    }

    /** Re-runs the current slot request, carrying the rotation index. */
    private fun reload() {
        val slotId = this.slotId ?: return
        val format = this.format
        val listener = this.listener
        val pluga = AdPluga.maybeInstance
        if (pluga == null) {
            listener?.onError(AdPlugaError.NotInitialized)
            return
        }
        loadJob = pluga.internalScope.launch {
            try {
                val response = pluga.serve(slotId, format, refreshSeq = refreshSeq)
                if (response == null) {
                    withContext(Dispatchers.Main) {
                        listener?.onError(AdPlugaError.Network(0, "no fill"))
                    }
                    return@launch
                }
                if (response.ad.kind !in RENDERABLE_KINDS) {
                    withContext(Dispatchers.Main) {
                        listener?.onError(AdPlugaError.UnsupportedFormat(response.ad.kind.wire))
                    }
                    return@launch
                }
                when (response.ad.kind) {
                    AdKind.HTML -> withContext(Dispatchers.Main) {
                        renderHtml(pluga, response)
                    }
                    AdKind.VIDEO, AdKind.VIDEO_REWARDED, AdKind.AUDIO -> withContext(Dispatchers.Main) {
                        renderVideo(pluga, response)
                    }
                    AdKind.CAROUSEL -> {
                        val bitmaps = response.ad.slides.map { safeLoadBitmap(it.assetUrl) }
                        withContext(Dispatchers.Main) {
                            renderCarousel(pluga, response, bitmaps)
                            listener?.onLoaded()
                            scheduleRefresh(response)
                        }
                    }
                    else -> {
                        val bitmap = response.ad.assetUrl?.let { safeLoadBitmap(it) }
                        withContext(Dispatchers.Main) {
                            boundResponse = response
                            impressionFired = false
                            teardownHtml()
                            teardownVideo()
                            teardownCarousel()
                            imageView.visibility = View.VISIBLE
                            if (bitmap != null) imageView.setImageBitmap(bitmap)
                            setSelfTestBadge(response.ad.isTest)
                            listener?.onLoaded()
                            setOnClickListener { fireClick(pluga, response) }
                            tryAttachViewability(pluga, response)
                            scheduleRefresh(response)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AdPlugaLogger.warn("AdView load failed slot=$slotId", t)
                withContext(Dispatchers.Main) { listener?.onError(t) }
            }
        }
    }

    @MainThread
    public fun bind(response: ServeResponse) {
        cancelInternal()
        val pluga = AdPluga.maybeInstance ?: return
        boundResponse = response
        impressionFired = false
        val slot = slotId ?: response.ad.id
        if (response.ad.kind == AdKind.HTML) {
            renderHtml(pluga, response)
            return
        }
        if (response.ad.kind == AdKind.VIDEO || response.ad.kind == AdKind.VIDEO_REWARDED || response.ad.kind == AdKind.AUDIO) {
            renderVideo(pluga, response)
            return
        }
        if (response.ad.kind == AdKind.CAROUSEL) {
            pluga.internalScope.launch {
                val bitmaps = response.ad.slides.map { safeLoadBitmap(it.assetUrl) }
                withContext(Dispatchers.Main) { renderCarousel(pluga, response, bitmaps) }
            }
            return
        }
        pluga.internalScope.launch {
            val bitmap = response.ad.assetUrl?.let { safeLoadBitmap(it) }
            withContext(Dispatchers.Main) {
                teardownHtml()
                teardownVideo()
                teardownCarousel()
                imageView.visibility = View.VISIBLE
                if (bitmap != null) imageView.setImageBitmap(bitmap)
                setSelfTestBadge(response.ad.isTest)
                setOnClickListener { fireClick(pluga, response) }
                tryAttachViewability(pluga, response)
            }
        }
    }

    @MainThread
    private fun renderCarousel(
        pluga: AdPluga,
        response: ServeResponse,
        bitmaps: List<android.graphics.Bitmap?>,
    ) {
        boundResponse = response
        impressionFired = false
        imageView.visibility = View.GONE
        teardownHtml()
        teardownVideo()
        teardownCarousel()
        setOnClickListener(null)
        isClickable = false
        val deck = CarouselAdView(
            context,
            response.ad.slides,
            onClick = { fireClick(pluga, response) },
            onSwipe = { lastDeckSwipeAt = System.currentTimeMillis() },
        )
        carouselView = deck
        addView(deck, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        deck.bind(bitmaps)
        setSelfTestBadge(response.ad.isTest)
        tryAttachViewability(pluga, response)
    }

    @MainThread
    private fun teardownCarousel() {
        carouselView?.let { removeView(it) }
        carouselView = null
        lastDeckSwipeAt = 0L
    }

    @MainThread
    private fun renderHtml(pluga: AdPluga, response: ServeResponse) {
        boundResponse = response
        impressionFired = false
        imageView.visibility = View.GONE
        teardownVideo()
        teardownCarousel()
        val view = htmlView ?: HtmlAdView(context).also { child ->
            addView(
                child,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                },
            )
            htmlView = child
        }
        view.onClick = { fireClick(pluga, response) }
        view.load(html = response.ad.html, assetUrl = response.ad.assetUrl)
        setSelfTestBadge(false)
        view.showTestBadge(response.ad.isTest)
        setOnClickListener(null)
        listener?.onLoaded()
        tryAttachViewability(pluga, response)
        scheduleRefresh(response)
    }

    @MainThread
    private fun renderVideo(pluga: AdPluga, response: ServeResponse) {
        boundResponse = response
        impressionFired = false
        imageView.visibility = View.GONE
        teardownHtml()
        teardownCarousel()
        val view = videoView ?: VideoAdView(context).also { child ->
            addView(
                child,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                },
            )
            videoView = child
        }
        view.clickThroughUrl = response.clickUrl
        view.openClickExternally = true
        view.onClick = { fireClick(pluga, response) }
        view.load(
            videoUrl = response.ad.assetUrl,
            quartilePings = response.quartilePings,
        )
        setSelfTestBadge(false)
        view.showTestBadge(response.ad.isTest)
        setOnClickListener(null)
        listener?.onLoaded()
        tryAttachViewability(pluga, response)
        scheduleRefresh(response)
    }

    private fun setSelfTestBadge(show: Boolean) {
        testBadge?.let { removeView(it) }
        testBadge = if (show) addTestBadge(this) else null
    }

    private fun teardownHtml() {
        htmlView?.let {
            it.onClick = null
            it.destroy()
            removeView(it)
        }
        htmlView = null
    }

    private fun teardownVideo() {
        videoView?.let {
            it.onClick = null
            it.onComplete = null
            it.onProgress = null
            it.destroy()
            removeView(it)
        }
        videoView = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val pluga = AdPluga.maybeInstance ?: return
        val response = boundResponse ?: return
        tryAttachViewability(pluga, response)
    }

    override fun onDetachedFromWindow() {
        cancelInternal()
        teardownHtml()
        teardownVideo()
        teardownCarousel()
        super.onDetachedFromWindow()
    }

    private fun tryAttachViewability(pluga: AdPluga, response: ServeResponse) {
        if (viewabilityHandle != 0 || impressionFired) return
        viewabilityHandle = ViewabilityTracker.register(this) {
            if (impressionFired) return@register
            impressionFired = true
            listener?.onImpression()
            pluga.fireImpression(
                slotId = slotId.orEmpty(),
                ad = response.ad,
                impressionUrl = response.impressionUrl,
                trackToken = response.trackToken,
            )
            pluga.fireViewable(
                slotId = slotId.orEmpty(),
                ad = response.ad,
                trackToken = response.trackToken,
            )
        }
    }

    private fun fireClick(pluga: AdPluga, response: ServeResponse) {
        listener?.onClick()
        pluga.fireClick(
            slotId = slotId.orEmpty(),
            ad = response.ad,
            clickUrl = response.clickUrl,
            trackToken = response.trackToken,
        )
    }

    private fun cancelInternal() {
        cancelRefresh()
        loadJob?.cancel()
        loadJob = null
        if (viewabilityHandle != 0) {
            ViewabilityTracker.unregister(viewabilityHandle)
            viewabilityHandle = 0
        }
    }

    /**
     * Arms the next rotation for the cadence the server published for this
     * slot. Nothing is scheduled when the slot has no cadence or the value is
     * below the industry floor.
     */
    @MainThread
    private fun scheduleRefresh(response: ServeResponse) {
        cancelRefresh()
        val secs = response.refreshAfterSeconds
        if (secs <= 0) return
        val floor = if (response.ad.isTest) {
            Constants.MIN_REFRESH_SECONDS_TEST
        } else {
            Constants.MIN_REFRESH_SECONDS
        }
        val task = Runnable { onRefreshTick() }
        refreshRunnable = task
        postDelayed(task, maxOf(secs, floor) * 1_000L)
    }

    @MainThread
    private fun cancelRefresh() {
        refreshRunnable?.let { removeCallbacks(it) }
        refreshRunnable = null
    }

    @MainThread
    private fun onRefreshTick() {
        refreshRunnable = null
        val response = boundResponse ?: return
        // Rotating an off-screen ad would spend a decision on an impression the
        // MRC guidelines classify as non-viewable: wait for it to come back
        // into view instead, re-arming on the same cadence.
        if (!ViewabilityTracker.isVisible(this)) {
            scheduleRefresh(response)
            return
        }
        // A deck the reader is still swiping through keeps the slot; rotation
        // resumes one full cadence after the last swipe.
        val secs = response.refreshAfterSeconds
        if (lastDeckSwipeAt > 0L &&
            secs > 0 &&
            System.currentTimeMillis() - lastDeckSwipeAt < secs * 1_000L
        ) {
            scheduleRefresh(response)
            return
        }
        refreshSeq += 1
        reload()
    }

    private companion object {
        val RENDERABLE_KINDS = setOf(
            AdKind.IMAGE,
            AdKind.TEMPLATE,
            AdKind.HTML,
            AdKind.VIDEO,
            AdKind.VIDEO_REWARDED,
            AdKind.AUDIO,
            AdKind.CAROUSEL,
        )

        suspend fun safeLoadBitmap(url: String): android.graphics.Bitmap? =
            withContext(Dispatchers.IO) {
                try {
                    val conn = URL(url).openConnection()
                    conn.connectTimeout = 3_000
                    conn.readTimeout = 5_000
                    val stream: InputStream = conn.getInputStream()
                    stream.use { BitmapFactory.decodeStream(it) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    AdPlugaLogger.debug("bitmap decode failed url=$url", t)
                    null
                }
            }
    }

    internal fun renderedAd(): Ad? = boundResponse?.ad
}
