package com.adpluga.mediation.admob

import android.view.View
import android.view.ViewGroup
import com.adpluga.AdListener
import com.adpluga.mediation.MediationSlot
import com.adpluga.ui.AdView
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration

internal class AdPlugaBannerLoader(
    private val configuration: MediationBannerAdConfiguration,
    private val loadCallback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
) : MediationBannerAd, AdListener {

    private var view: AdView? = null
    private var callback: MediationBannerAdCallback? = null

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
        val context = configuration.context
        val size = configuration.adSize
        val adView = AdView(context).apply {
            mediated = true
            layoutParams = ViewGroup.LayoutParams(size.getWidthInPixels(context), size.getHeightInPixels(context))
        }
        view = adView
        adView.load(slot.slotId, format = "${size.width}x${size.height}", listener = this)
    }

    override fun getView(): View = checkNotNull(view)

    override fun onLoaded() {
        if (callback == null) callback = loadCallback.onSuccess(this)
    }

    override fun onImpression() {
        callback?.reportAdImpression()
    }

    override fun onClick() {
        callback?.apply {
            reportAdClicked()
            onAdOpened()
            onAdLeftApplication()
        }
    }

    override fun onError(error: Throwable) {
        if (callback == null) loadCallback.onFailure(AdPlugaAdMobErrors.from(error))
    }
}
