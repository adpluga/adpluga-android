package com.ironsource.adapters.custom.adpluga

import android.app.Activity
import com.adpluga.AdListener
import com.adpluga.errors.AdPlugaError
import com.adpluga.model.AdSource
import com.adpluga.ui.InterstitialAd
import com.ironsource.mediationsdk.adunit.adapter.BaseInterstitial
import com.ironsource.mediationsdk.adunit.adapter.listener.InterstitialAdListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrors
import com.ironsource.mediationsdk.model.NetworkSettings

public class AdPlugaCustomInterstitial(settings: NetworkSettings) : BaseInterstitial<AdPlugaCustomAdapter>(settings) {

    private var ad: InterstitialAd? = null
    private var activity: Activity? = null

    override fun loadAd(adData: AdData, activity: Activity, listener: InterstitialAdListener) {
        val slot = AdPlugaLevelPlay.slot(adData) ?: return AdPlugaLevelPlay.missingParams(listener)
        this.activity = activity
        InterstitialAd.load(slot.slotId, callback = object : InterstitialAd.LoadCallback {
            override fun onLoaded(ad: InterstitialAd) {
                if (ad.source == AdSource.HOUSE) return AdPlugaLevelPlay.loadFailed(listener, AdPlugaError.NoFill)
                this@AdPlugaCustomInterstitial.ad = ad
                listener.onAdLoadSuccess()
            }

            override fun onError(error: Throwable) = AdPlugaLevelPlay.loadFailed(listener, error)
        })
    }

    override fun isAdAvailable(adData: AdData): Boolean = ad != null

    override fun showAd(adData: AdData, listener: InterstitialAdListener) {
        val loaded = ad
        val host = activity
        if (loaded == null || host == null) {
            listener.onAdShowFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, "no AdPluga ad loaded")
            return
        }
        ad = null
        loaded.show(host, object : AdListener {
            override fun onImpression() {
                listener.onAdOpened()
                listener.onAdShowSuccess()
            }

            override fun onClick() = listener.onAdClicked()

            override fun onDismiss() {
                activity = null
                listener.onAdClosed()
            }

            override fun onError(error: Throwable) = listener.onAdShowFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, error.message)
        })
    }
}
