package com.adpluga.mediation.admob

import android.app.Activity
import android.content.Context
import com.adpluga.AdListener
import com.adpluga.errors.AdPlugaError
import com.adpluga.mediation.MediationSlot
import com.adpluga.model.AdSource
import com.adpluga.ui.InterstitialAd
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration

internal class AdPlugaInterstitialLoader(
    private val configuration: MediationInterstitialAdConfiguration,
    private val loadCallback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
) : MediationInterstitialAd {

    private var ad: InterstitialAd? = null
    private var callback: MediationInterstitialAdCallback? = null

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
        InterstitialAd.load(slot.slotId, callback = object : InterstitialAd.LoadCallback {
            override fun onLoaded(ad: InterstitialAd) {
                if (ad.source == AdSource.HOUSE) {
                    loadCallback.onFailure(AdPlugaAdMobErrors.from(AdPlugaError.NoFill))
                    return
                }
                this@AdPlugaInterstitialLoader.ad = ad
                callback = loadCallback.onSuccess(this@AdPlugaInterstitialLoader)
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
        loaded.show(activity, object : AdListener {
            override fun onImpression() {
                callback?.reportAdImpression()
            }

            override fun onClick() {
                callback?.reportAdClicked()
                callback?.onAdLeftApplication()
            }

            override fun onDismiss() {
                callback?.onAdClosed()
            }

            override fun onError(error: Throwable) {
                callback?.onAdFailedToShow(AdPlugaAdMobErrors.from(error))
            }
        })
        callback?.onAdOpened()
    }
}
