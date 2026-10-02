package com.adpluga.mediation.applovin

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.adpluga.AdListener
import com.adpluga.RewardListener
import com.adpluga.errors.AdPlugaError
import com.adpluga.mediation.MediationSlot
import com.adpluga.model.AdSource
import com.adpluga.ui.AdView
import com.adpluga.ui.InterstitialAd
import com.adpluga.ui.RewardedAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.sdk.AppLovinSdkUtils

/**
 * The AdPluga side of the MAX adapter, kept apart from MediationAdapterBase
 * (which needs a live AppLovin SDK) so it can be exercised on its own.
 */
public class AdPlugaMaxBridge {

    private var adView: AdView? = null
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    public fun loadAdView(
        placementId: String?,
        appId: String?,
        format: MaxAdFormat,
        context: Context,
        listener: MaxAdViewAdapterListener,
    ) {
        val slot = slotFor(placementId, appId) ?: return listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
        val size = format.size
        val view = AdView(context).apply {
            mediated = true
            layoutParams = ViewGroup.LayoutParams(
                AppLovinSdkUtils.dpToPx(context, size.width),
                AppLovinSdkUtils.dpToPx(context, size.height),
            )
        }
        adView = view
        var loaded = false
        view.load(slot.slotId, format = "${size.width}x${size.height}", listener = object : AdListener {
            override fun onLoaded() {
                if (loaded) return
                loaded = true
                listener.onAdViewAdLoaded(view)
            }

            override fun onImpression() = listener.onAdViewAdDisplayed()

            override fun onClick() = listener.onAdViewAdClicked()

            override fun onError(error: Throwable) {
                if (!loaded) listener.onAdViewAdLoadFailed(maxError(error))
            }
        })
    }

    public fun loadInterstitial(placementId: String?, appId: String?, listener: MaxInterstitialAdapterListener) {
        val slot = slotFor(placementId, appId) ?: return listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
        InterstitialAd.load(slot.slotId, callback = object : InterstitialAd.LoadCallback {
            override fun onLoaded(ad: InterstitialAd) {
                if (ad.source == AdSource.HOUSE) return listener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL)
                interstitial = ad
                listener.onInterstitialAdLoaded()
            }

            override fun onError(error: Throwable) = listener.onInterstitialAdLoadFailed(maxError(error))
        })
    }

    public fun showInterstitial(activity: Activity?, listener: MaxInterstitialAdapterListener) {
        val ad = interstitial
        if (ad == null || activity == null) return listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
        ad.show(activity, object : AdListener {
            override fun onImpression() = listener.onInterstitialAdDisplayed()
            override fun onClick() = listener.onInterstitialAdClicked()
            override fun onDismiss() = listener.onInterstitialAdHidden()
            override fun onError(error: Throwable) = listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED)
        })
    }

    public fun loadRewarded(placementId: String?, appId: String?, listener: MaxRewardedAdapterListener) {
        val slot = slotFor(placementId, appId) ?: return listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
        RewardedAd.load(slot.slotId, callback = object : RewardedAd.LoadCallback {
            override fun onLoaded(ad: RewardedAd) {
                if (ad.source == AdSource.HOUSE) return listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL)
                rewarded = ad
                listener.onRewardedAdLoaded()
            }

            override fun onError(error: Throwable) = listener.onRewardedAdLoadFailed(maxError(error))
        })
    }

    /**
     * [onHidden] runs when the ad closes with whether the reward was earned;
     * MAX wants the reward reported before the hide callback.
     */
    public fun showRewarded(activity: Activity?, listener: MaxRewardedAdapterListener, onHidden: (earned: Boolean) -> Unit) {
        val ad = rewarded
        if (ad == null || activity == null) return listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
        var earned = false
        ad.show(
            activity,
            object : AdListener {
                override fun onImpression() = listener.onRewardedAdDisplayed()
                override fun onClick() = listener.onRewardedAdClicked()
                override fun onDismiss() = onHidden(earned)
                override fun onError(error: Throwable) = listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED)
            },
            RewardListener { _, _ -> earned = true },
        )
    }

    public fun destroy() {
        adView = null
        interstitial = null
        rewarded = null
    }

    public companion object {
        /** The key MAX stores the custom network's App ID under. */
        public const val APP_ID: String = "app_id"

        /**
         * The Placement ID names the slot (or carries the JSON form); the App
         * ID, when set, is the publisher key used to initialise AdPluga.
         */
        @JvmStatic
        public fun slotFor(placementId: String?, appId: String?): MediationSlot? {
            val slot = MediationSlot.parse(placementId) ?: return null
            val key = appId?.trim()?.takeIf { it.isNotEmpty() }
            val withKey = if (slot.publisherKey == null && key != null) slot.copy(publisherKey = key) else slot
            return try {
                withKey.ensureInitialized()
                withKey
            } catch (_: Throwable) {
                null
            }
        }

        @JvmStatic
        public fun maxError(t: Throwable): MaxAdapterError = when (t) {
            is AdPlugaError.NoFill -> MaxAdapterError.NO_FILL
            is AdPlugaError.NotInitialized -> MaxAdapterError.NOT_INITIALIZED
            is AdPlugaError.Network -> MaxAdapterError.NO_CONNECTION
            else -> MaxAdapterError.UNSPECIFIED
        }
    }
}
