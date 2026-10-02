package com.ironsource.adapters.custom.adpluga

import android.app.Activity
import com.adpluga.AdListener
import com.adpluga.RewardListener
import com.adpluga.errors.AdPlugaError
import com.adpluga.model.AdSource
import com.adpluga.ui.RewardedAd
import com.ironsource.mediationsdk.adunit.adapter.BaseRewardedVideo
import com.ironsource.mediationsdk.adunit.adapter.listener.RewardedVideoAdListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrors
import com.ironsource.mediationsdk.model.NetworkSettings

public class AdPlugaCustomRewardedVideo(settings: NetworkSettings) : BaseRewardedVideo<AdPlugaCustomAdapter>(settings) {

    private var ad: RewardedAd? = null
    private var activity: Activity? = null

    override fun loadAd(adData: AdData, activity: Activity, listener: RewardedVideoAdListener) {
        val slot = AdPlugaLevelPlay.slot(adData) ?: return AdPlugaLevelPlay.missingParams(listener)
        this.activity = activity
        RewardedAd.load(slot.slotId, callback = object : RewardedAd.LoadCallback {
            override fun onLoaded(ad: RewardedAd) {
                if (ad.source == AdSource.HOUSE) return AdPlugaLevelPlay.loadFailed(listener, AdPlugaError.NoFill)
                this@AdPlugaCustomRewardedVideo.ad = ad
                listener.onAdLoadSuccess()
            }

            override fun onError(error: Throwable) = AdPlugaLevelPlay.loadFailed(listener, error)
        })
    }

    override fun isAdAvailable(adData: AdData): Boolean = ad != null

    override fun showAd(adData: AdData, listener: RewardedVideoAdListener) {
        val loaded = ad
        val host = activity
        if (loaded == null || host == null) {
            listener.onAdShowFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, "no AdPluga ad loaded")
            return
        }
        ad = null
        loaded.show(
            host,
            object : AdListener {
                override fun onImpression() {
                    listener.onAdOpened()
                    listener.onAdShowSuccess()
                    listener.onAdStarted()
                }

                override fun onClick() = listener.onAdClicked()

                override fun onDismiss() {
                    activity = null
                    listener.onAdClosed()
                }

                override fun onError(error: Throwable) = listener.onAdShowFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, error.message)
            },
            RewardListener { _, _ ->
                listener.onAdEnded()
                listener.onAdRewarded()
            },
        )
    }
}
