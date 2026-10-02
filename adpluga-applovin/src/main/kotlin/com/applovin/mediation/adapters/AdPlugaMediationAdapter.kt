package com.applovin.mediation.adapters

import android.app.Activity
import com.adpluga.AdPluga
import com.adpluga.mediation.applovin.AdPlugaMaxBridge
import com.adpluga.mediation.applovin.BuildConfig
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdViewAdapter
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.sdk.AppLovinSdk

/**
 * AppLovin MAX custom network adapter for AdPluga. In the MAX dashboard, add a
 * custom SDK network with the Android class name
 * `com.applovin.mediation.adapters.AdPlugaMediationAdapter`, the AdPluga
 * publisher key as App ID and the slot id as Placement ID. Banner, MREC,
 * leader, interstitial and rewarded.
 */
public class AdPlugaMediationAdapter(sdk: AppLovinSdk) :
    MediationAdapterBase(sdk), MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter {

    private val bridge = AdPlugaMaxBridge()

    override fun initialize(
        parameters: MaxAdapterInitializationParameters,
        activity: Activity?,
        listener: MaxAdapter.OnCompletionListener,
    ) {
        try {
            val key = appId(parameters)
            if (AdPluga.maybeInstance == null && key != null) AdPluga.initialize(publisherKey = key)
            listener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
        } catch (t: Throwable) {
            listener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_FAILURE, t.message)
        }
    }

    override fun getSdkVersion(): String = AdPluga.SDK_VERSION

    override fun getAdapterVersion(): String = BuildConfig.ADAPTER_VERSION

    override fun onDestroy() = bridge.destroy()

    override fun loadAdViewAd(
        parameters: MaxAdapterResponseParameters,
        format: MaxAdFormat,
        activity: Activity?,
        listener: MaxAdViewAdapterListener,
    ) = bridge.loadAdView(
        parameters.thirdPartyAdPlacementId, appId(parameters), format, activity ?: applicationContext, listener,
    )

    override fun loadInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) = bridge.loadInterstitial(parameters.thirdPartyAdPlacementId, appId(parameters), listener)

    override fun showInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) = bridge.showInterstitial(activity, listener)

    override fun loadRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) = bridge.loadRewarded(parameters.thirdPartyAdPlacementId, appId(parameters), listener)

    override fun showRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) {
        configureReward(parameters)
        bridge.showRewarded(activity, listener) { earned ->
            if (earned || shouldAlwaysRewardUser()) listener.onUserRewarded(reward)
            listener.onRewardedAdHidden()
        }
    }

    private fun appId(parameters: MaxAdapterParameters): String? =
        parameters.serverParameters.getString(AdPlugaMaxBridge.APP_ID)?.trim()?.takeIf { it.isNotEmpty() }
}
