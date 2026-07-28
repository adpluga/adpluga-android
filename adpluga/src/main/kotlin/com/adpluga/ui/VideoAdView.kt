package com.adpluga.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import com.adpluga.AdPluga
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.tracking.QuartileFirer

public class VideoAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    public var onClick: (() -> Unit)? = null
    public var onComplete: (() -> Unit)? = null
    public var onProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    public var openClickExternally: Boolean = true
    public var clickThroughUrl: String? = null

    private val surfaceView: SurfaceView = SurfaceView(context)
    private var mediaPlayer: MediaPlayer? = null
    private var quartileFirer: QuartileFirer? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var prepared: Boolean = false
    private var destroyed: Boolean = false
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var pendingUrl: String? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            val url = pendingUrl
            if (url != null && mediaPlayer == null) {
                preparePlayer(url)
            } else {
                mediaPlayer?.setDisplay(holder)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            mediaPlayer?.setDisplay(null)
        }
    }

    init {
        setBackgroundColor(0xFF000000.toInt())
        surfaceView.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        addView(surfaceView)
        surfaceView.holder.addCallback(surfaceCallback)
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                handleTap()
            }
            true
        }
    }

    @MainThread
    public fun load(videoUrl: String?, quartilePings: Map<String, String>? = null) {
        if (destroyed) return
        val pluga = AdPluga.maybeInstance ?: return
        val url = videoUrl?.takeIf { it.isNotEmpty() } ?: return
        quartileFirer = QuartileFirer(quartilePings, pluga.internalScope, pluga.endpoint)
        pendingUrl = url
        if (surfaceView.holder.surface?.isValid == true) {
            preparePlayer(url)
        }
    }

    private fun preparePlayer(url: String) {
        releasePlayer()
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(context, Uri.parse(url))
            player.setDisplay(surfaceView.holder)
            player.setOnPreparedListener {
                if (destroyed) return@setOnPreparedListener
                prepared = true
                videoWidth = it.videoWidth
                videoHeight = it.videoHeight
                requestLayout()
                it.setVolume(0f, 0f)
                it.isLooping = false
                it.start()
                progressHandler.postDelayed(pollProgress, POLL_INTERVAL_MS)
            }
            player.setOnCompletionListener {
                fireProgressOnce()
                onComplete?.invoke()
            }
            player.setOnErrorListener { _, what, extra ->
                AdPlugaLogger.warn("MediaPlayer error what=$what extra=$extra")
                true
            }
            player.prepareAsync()
        } catch (t: Throwable) {
            AdPlugaLogger.warn("VideoAdView setDataSource failed url=$url", t)
            releasePlayer()
        }
    }

    private fun fireProgressOnce() {
        val player = mediaPlayer ?: return
        if (!prepared) return
        val duration = safeDuration(player)
        if (duration <= 0) return
        quartileFirer?.update(duration.toLong(), duration.toLong())
        onProgress?.invoke(duration.toLong(), duration.toLong())
    }

    private val pollProgress = object : Runnable {
        override fun run() {
            if (destroyed) return
            val player = mediaPlayer ?: return
            if (!prepared) {
                progressHandler.postDelayed(this, POLL_INTERVAL_MS)
                return
            }
            val duration = safeDuration(player)
            val position = safePosition(player)
            if (duration > 0) {
                quartileFirer?.update(position.toLong(), duration.toLong())
                onProgress?.invoke(position.toLong(), duration.toLong())
            }
            progressHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun safeDuration(player: MediaPlayer): Int =
        try {
            player.duration.coerceAtLeast(0)
        } catch (_: IllegalStateException) {
            0
        }

    private fun safePosition(player: MediaPlayer): Int =
        try {
            player.currentPosition.coerceAtLeast(0)
        } catch (_: IllegalStateException) {
            0
        }

    private fun handleTap() {
        if (destroyed) return
        onClick?.invoke()
        val target = clickThroughUrl
        if (!openClickExternally || target.isNullOrEmpty()) return
        val uri = runCatching { Uri.parse(target) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { AdPlugaLogger.warn("video click open failed url=$uri", it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val vw = videoWidth
        val vh = videoHeight
        if (vw <= 0 || vh <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val availableW = MeasureSpec.getSize(widthMeasureSpec)
        val availableH = MeasureSpec.getSize(heightMeasureSpec)
        val ratioView = availableW.toFloat() / availableH.toFloat().coerceAtLeast(1f)
        val ratioVideo = vw.toFloat() / vh.toFloat()
        val (w, h) = if (ratioVideo > ratioView) {
            availableW to (availableW / ratioVideo).toInt()
        } else {
            (availableH * ratioVideo).toInt() to availableH
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    @MainThread
    public fun destroy() {
        if (destroyed) return
        destroyed = true
        progressHandler.removeCallbacks(pollProgress)
        releasePlayer()
        surfaceView.holder.removeCallback(surfaceCallback)
        visibility = View.GONE
    }

    private fun releasePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        prepared = false
        if (player != null) {
            try {
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                player.stop()
            } catch (_: IllegalStateException) {
            } catch (t: Throwable) {
                AdPlugaLogger.debug("MediaPlayer stop failed", t)
            }
            try {
                player.reset()
            } catch (t: Throwable) {
                AdPlugaLogger.debug("MediaPlayer reset failed", t)
            }
            try {
                player.release()
            } catch (t: Throwable) {
                AdPlugaLogger.debug("MediaPlayer release failed", t)
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS: Long = 200
    }
}
