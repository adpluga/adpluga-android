package com.adpluga.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import com.adpluga.AdListener
import com.adpluga.AdPluga
import com.adpluga.RewardListener
import com.adpluga.errors.AdPlugaError
import com.adpluga.model.AdKind
import com.adpluga.model.ServeResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

public class RewardedAd internal constructor(
    private val response: ServeResponse,
    private val slotId: String,
) {

    @MainThread
    public fun show(
        activity: Activity,
        listener: AdListener? = null,
        rewardListener: RewardListener,
    ) {
        val pluga = AdPluga.maybeInstance
        if (pluga == null) {
            listener?.onError(AdPlugaError.NotInitialized)
            return
        }
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = buildFullscreenRoot(activity)
        val isVideo = response.ad.kind == AdKind.VIDEO_REWARDED || response.ad.kind == AdKind.VIDEO
        val clickHandler = {
            listener?.onClick()
            pluga.fireClick(
                slotId = slotId,
                ad = response.ad,
                clickUrl = response.clickUrl,
                trackToken = response.trackToken,
            )
        }

        val closeButton = addCloseButton(activity, root) {
            dialog.dismiss()
            listener?.onDismiss()
        }.apply { visibility = View.GONE }

        val countdown = TextView(activity).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            val density = resources.displayMetrics.density
            val padPx = (12 * density).toInt()
            setPadding(padPx, padPx, padPx, padPx)
        }
        root.addView(
            countdown,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.TOP or Gravity.START },
        )

        val handler = Handler(Looper.getMainLooper())
        var rewarded = false
        val grantReward = {
            if (!rewarded) {
                rewarded = true
                try {
                    rewardListener.onReward(
                        response.ad.rewardAmount.takeIf { it > 0 } ?: 1,
                        response.ad.rewardCurrency,
                    )
                } catch (_: Throwable) {
                }
            }
        }

        if (isVideo) {
            val video = VideoAdView(activity).apply {
                clickThroughUrl = response.clickUrl
                openClickExternally = false
                onClick = { clickHandler() }
            }
            val skippableAfter = response.ad.skippableAfterMs.toLong()
            video.onProgress = { positionMs, durationMs ->
                if (durationMs > 0) {
                    val remainingMs = (durationMs - positionMs).coerceAtLeast(0)
                    val secs = ceil(remainingMs / 1_000.0).toInt()
                    countdown.text = secs.toString()
                    if (skippableAfter > 0 && positionMs >= skippableAfter) {
                        countdown.visibility = View.GONE
                        closeButton.visibility = View.VISIBLE
                    }
                }
            }
            video.onComplete = {
                countdown.visibility = View.GONE
                closeButton.visibility = View.VISIBLE
                grantReward()
            }
            root.addView(
                video,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { gravity = Gravity.CENTER },
            )
            countdown.text = computeDurationSeconds(response).toString()
            dialog.setContentView(root)
            dialog.setCancelable(false)
            dialog.setOnShowListener {
                listener?.onImpression()
                pluga.fireImpression(
                    slotId = slotId,
                    ad = response.ad,
                    impressionUrl = response.impressionUrl,
                    trackToken = response.trackToken,
                )
                pluga.fireViewable(
                    slotId = slotId,
                    trackToken = response.trackToken,
                )
                video.load(
                    videoUrl = response.ad.assetUrl,
                    quartilePings = response.quartilePings,
                )
            }
            dialog.setOnDismissListener {
                video.destroy()
            }
            dialog.show()
            return
        }

        val image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setOnClickListener { clickHandler() }
        }
        root.addView(
            image,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER },
        )

        val durationSeconds = computeDurationSeconds(response)
        var remaining = durationSeconds
        countdown.text = remaining.toString()

        val tickRunnable = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    countdown.visibility = View.GONE
                    closeButton.visibility = View.VISIBLE
                    grantReward()
                } else {
                    countdown.text = remaining.toString()
                    handler.postDelayed(this, 1_000L)
                }
            }
        }

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.setOnShowListener {
            listener?.onImpression()
            pluga.fireImpression(
                slotId = slotId,
                ad = response.ad,
                impressionUrl = response.impressionUrl,
                trackToken = response.trackToken,
            )
            pluga.fireViewable(
                slotId = slotId,
                trackToken = response.trackToken,
            )
            handler.postDelayed(tickRunnable, 1_000L)
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(tickRunnable)
        }
        pluga.internalScope.launch {
            val bmp = response.ad.assetUrl?.let { safeLoadBitmap(it) }
            if (bmp != null) {
                withContext(Dispatchers.Main) { image.setImageBitmap(bmp) }
            }
        }
        dialog.show()
    }

    public interface LoadCallback {
        public fun onLoaded(ad: RewardedAd)
        public fun onError(error: Throwable)
    }

    public companion object {
        private val SUPPORTED = setOf(
            AdKind.IMAGE,
            AdKind.TEMPLATE,
            AdKind.VIDEO,
            AdKind.VIDEO_REWARDED,
        )
        private const val DEFAULT_DURATION_SECONDS = 5

        private fun computeDurationSeconds(response: ServeResponse): Int {
            val ms = response.ad.durationMs
            if (ms <= 0) return DEFAULT_DURATION_SECONDS
            return ceil(ms / 1_000.0).toInt().coerceIn(1, 60)
        }

        @JvmStatic
        @JvmOverloads
        public fun load(slotId: String, format: String? = "video_rewarded", callback: LoadCallback) {
            val pluga = AdPluga.maybeInstance
            if (pluga == null) {
                callback.onError(AdPlugaError.NotInitialized)
                return
            }
            pluga.internalScope.launch {
                try {
                    val response = pluga.serve(slotId, format)
                    if (response == null) {
                        withContext(Dispatchers.Main) {
                            callback.onError(AdPlugaError.Network(0, "no fill"))
                        }
                        return@launch
                    }
                    if (response.ad.kind !in SUPPORTED) {
                        withContext(Dispatchers.Main) {
                            callback.onError(AdPlugaError.UnsupportedFormat(response.ad.kind.wire))
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        callback.onLoaded(RewardedAd(response, slotId))
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { callback.onError(t) }
                }
            }
        }
    }
}
