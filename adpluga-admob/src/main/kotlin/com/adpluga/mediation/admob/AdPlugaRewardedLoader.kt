package com.adpluga.mediation.admob

import android.app.Activity
import android.content.Context
import com.adpluga.AdListener
import com.adpluga.RewardListener
import com.adpluga.errors.AdPlugaError
import com.adpluga.mediation.MediationSlot
import com.adpluga.model.AdSource
import com.adpluga.ui.RewardedAd
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import com.google.android.gms.ads.rewarded.RewardItem

internal class AdPlugaRewardedLoader(
    private val configuration: MediationRewardedAdConfiguration,
    private val loadCallback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
) : MediationRewardedAd {

    private var ad: RewardedAd? = null
    private var callback: MediationRewardedAdCallback? = null

    fun load() {
        val slot = MediationSlot.parse(configuration.serverParameters.getString(AdPlugaAdMobAdapter.PARAMETER))
        if (slot == null) {
            loadCallback.onFailure(AdPlugaAdMobErrors.invalidParameter())
            return
        }
        try {
            slot.ensureInitialized()
        } catch (t: Throwable) {
            loadCallback.onFailure(AdPlugaAdMobErrors.from(t))
            return
        }
        RewardedAd.load(slot.slotId, callback = object : RewardedAd.LoadCallback {
            override fun onLoaded(ad: RewardedAd) {
                if (ad.source == AdSource.HOUSE) {
                    loadCallback.onFailure(AdPlugaAdMobErrors.from(AdPlugaError.NoFill))
                    return
                }
                this@AdPlugaRewardedLoader.ad = ad
                callback = loadCallback.onSuccess(this@AdPlugaRewardedLoader)
            }

            override fun onError(error: Throwable) {
                loadCallback.onFailure(AdPlugaAdMobErrors.from(error))
            }
        })
    }

    override fun showAd(context: Context) {
        val activity = context as? Activity
        val loaded = ad
        if (activity == null || loaded == null) {
            callback?.onAdFailedToShow(AdPlugaAdMobErrors.noActivity())
            return
        }
        val listener = object : AdListener {
            override fun onImpression() {
                callback?.reportAdImpression()
                callback?.onVideoStart()
            }

            override fun onClick() {
                callback?.reportAdClicked()
            }

            override fun onDismiss() {
                callback?.onAdClosed()
            }

            override fun onError(error: Throwable) {
                callback?.onAdFailedToShow(AdPlugaAdMobErrors.from(error))
            }
        }
        loaded.show(activity, listener, RewardListener { amount, currency ->
            callback?.onVideoComplete()
            callback?.onUserEarnedReward(reward(currency, amount))
        })
        callback?.onAdOpened()
    }

    private fun reward(type: String, amount: Int): RewardItem = object : RewardItem {
        override fun getType(): String = type
        override fun getAmount(): Int = amount
    }
}
