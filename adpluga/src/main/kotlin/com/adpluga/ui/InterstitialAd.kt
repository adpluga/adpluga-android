package com.adpluga.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import com.adpluga.AdListener
import com.adpluga.AdPluga
import com.adpluga.errors.AdPlugaError
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.model.AdKind
import com.adpluga.model.ServeResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

public class InterstitialAd internal constructor(
    private val response: ServeResponse,
    private val slotId: String,
) {

    @MainThread
    public fun show(activity: Activity, listener: AdListener? = null) {
        val pluga = AdPluga.maybeInstance
        if (pluga == null) {
            listener?.onError(AdPlugaError.NotInitialized)
            return
        }
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = buildFullscreenRoot(activity)
        val clickHandler = {
            listener?.onClick()
            pluga.fireClick(
                slotId = slotId,
                ad = response.ad,
                clickUrl = response.clickUrl,
                trackToken = response.trackToken,
            )
        }
        if (response.ad.kind == AdKind.HTML) {
            val html = HtmlAdView(activity).apply {
                onClick = { clickHandler() }
            }
            root.addView(
                html,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { gravity = Gravity.CENTER },
            )
            html.load(html = response.ad.html, assetUrl = response.ad.assetUrl)
        } else if (response.ad.kind == AdKind.VIDEO) {
            val video = VideoAdView(activity).apply {
                clickThroughUrl = response.clickUrl
                openClickExternally = true
                onClick = { clickHandler() }
            }
            root.addView(
                video,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { gravity = Gravity.CENTER },
            )
            video.load(
                videoUrl = response.ad.assetUrl,
                quartilePings = response.quartilePings,
            )
        } else {
            val image = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setOnClickListener { clickHandler() }
            }
            root.addView(
                image,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { gravity = Gravity.CENTER },
            )
            pluga.internalScope.launch {
                val bmp = response.ad.assetUrl?.let { safeLoadBitmap(it) }
                if (bmp != null) {
                    withContext(Dispatchers.Main) { image.setImageBitmap(bmp) }
                }
            }
        }
        addCloseButton(activity, root) {
            dialog.dismiss()
            listener?.onDismiss()
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
        }
        dialog.show()
    }

    public interface LoadCallback {
        public fun onLoaded(ad: InterstitialAd)
        public fun onError(error: Throwable)
    }

    public companion object {
        private val SUPPORTED = setOf(AdKind.IMAGE, AdKind.TEMPLATE, AdKind.HTML, AdKind.VIDEO)

        @JvmStatic
        @JvmOverloads
        public fun load(slotId: String, format: String? = "interstitial", callback: LoadCallback) {
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
                        callback.onLoaded(InterstitialAd(response, slotId))
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

internal fun buildFullscreenRoot(activity: Activity): FrameLayout =
    FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

internal fun addCloseButton(activity: Activity, root: FrameLayout, onClick: () -> Unit): TextView {
    val close = TextView(activity).apply {
        text = "\u2715"
        textSize = 24f
        setTextColor(Color.WHITE)
        val density = resources.displayMetrics.density
        val padPx = (16 * density).toInt()
        setPadding(padPx, padPx, padPx, padPx)
        setOnClickListener { onClick() }
    }
    root.addView(
        close,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.TOP or Gravity.END },
    )
    return close
}

internal suspend fun safeLoadBitmap(url: String): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection()
            conn.connectTimeout = 3_000
            conn.readTimeout = 5_000
            conn.getInputStream().use { BitmapFactory.decodeStream(it) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AdPlugaLogger.debug("bitmap decode failed url=$url", t)
            null
        }
    }
