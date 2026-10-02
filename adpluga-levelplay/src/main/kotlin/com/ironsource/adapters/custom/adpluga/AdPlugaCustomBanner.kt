package com.ironsource.adapters.custom.adpluga

import android.app.Activity
import android.view.Gravity
import android.widget.FrameLayout
import com.adpluga.AdListener
import com.adpluga.ui.AdView
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.adunit.adapter.BaseBanner
import com.ironsource.mediationsdk.adunit.adapter.listener.BannerAdListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.model.NetworkSettings

public class AdPlugaCustomBanner(settings: NetworkSettings) : BaseBanner<AdPlugaCustomAdapter>(settings) {

    private var view: AdView? = null

    override fun loadAd(adData: AdData, activity: Activity, size: ISBannerSize, listener: BannerAdListener) {
        val slot = AdPlugaLevelPlay.slot(adData) ?: return AdPlugaLevelPlay.missingParams(listener)
        val metrics = activity.resources.displayMetrics
        val (w, h) = AdPlugaLevelPlay.dimensions(size, (metrics.widthPixels / metrics.density).toInt())
        val params = FrameLayout.LayoutParams((w * metrics.density).toInt(), (h * metrics.density).toInt(), Gravity.CENTER)
        val adView = AdView(activity).apply { mediated = true }
        view = adView
        var loaded = false
        adView.load(slot.slotId, format = "${w}x$h", listener = object : AdListener {
            override fun onLoaded() {
                if (loaded) return
                loaded = true
                listener.onAdLoadSuccess(adView, params)
            }

            override fun onImpression() = listener.onAdOpened()

            override fun onClick() {
                listener.onAdClicked()
                listener.onAdLeftApplication()
            }

            override fun onError(error: Throwable) {
                if (!loaded) AdPlugaLevelPlay.loadFailed(listener, error)
            }
        })
    }

    override fun destroyAd(adData: AdData) {
        view = null
    }
}
