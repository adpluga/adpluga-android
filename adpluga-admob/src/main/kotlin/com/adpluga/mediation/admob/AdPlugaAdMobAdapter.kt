package com.adpluga.mediation.admob

import android.content.Context
import androidx.annotation.Keep
import com.adpluga.AdPluga
import com.adpluga.mediation.MediationSlot
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration

/**
 * AdMob custom event for AdPluga. In the AdMob UI, set the class name to
 * `com.adpluga.mediation.admob.AdPlugaAdMobAdapter` and the parameter to the
 * AdPluga slot (see [MediationSlot]). Banner, interstitial and rewarded.
 */
@Keep
public class AdPlugaAdMobAdapter : Adapter() {

    private var banner: AdPlugaBannerLoader? = null
    private var interstitial: AdPlugaInterstitialLoader? = null
    private var rewarded: AdPlugaRewardedLoader? = null

    override fun initialize(
        context: Context,
        callback: InitializationCompleteCallback,
        configurations: List<MediationConfiguration>,
    ) {
        val slot = configurations.firstNotNullOfOrNull { MediationSlot.parse(it.serverParameters.getString(PARAMETER)) }
        try {
            slot?.ensureInitialized()
            callback.onInitializationSucceeded()
        } catch (t: Throwable) {
            callback.onInitializationFailed(t.message ?: "AdPluga initialisation failed")
        }
    }

    override fun getVersionInfo(): VersionInfo = parseVersion(BuildConfig.ADAPTER_VERSION)

    override fun getSDKVersionInfo(): VersionInfo = parseVersion(AdPluga.SDK_VERSION)

    override fun loadBannerAd(
        configuration: MediationBannerAdConfiguration,
        callback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
    ) {
        banner = AdPlugaBannerLoader(configuration, callback).also { it.load() }
    }

    override fun loadInterstitialAd(
        configuration: MediationInterstitialAdConfiguration,
        callback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
    ) {
        interstitial = AdPlugaInterstitialLoader(configuration, callback).also { it.load() }
    }

    override fun loadRewardedAd(
        configuration: MediationRewardedAdConfiguration,
        callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    ) {
        rewarded = AdPlugaRewardedLoader(configuration, callback).also { it.load() }
    }

    override fun loadRewardedInterstitialAd(
        configuration: MediationRewardedAdConfiguration,
        callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    ) {
        loadRewardedAd(configuration, callback)
    }

    internal companion object {
        /** The key AdMob stores the custom event parameter under. */
        const val PARAMETER: String = "parameter"

        /**
         * AdMob reads three numbers. A four-part adapter version (SDK version
         * plus the adapter's own patch) folds the last two into micro, as
         * Google's own adapters do: 0.7.6.1 → 0.7.601.
         */
        fun parseVersion(raw: String): VersionInfo {
            val parts = raw.split('.').map { it.toIntOrNull() ?: return VersionInfo(0, 0, 0) }
            return when {
                parts.size >= 4 -> VersionInfo(parts[0], parts[1], parts[2] * 100 + parts[3])
                parts.size == 3 -> VersionInfo(parts[0], parts[1], parts[2])
                else -> VersionInfo(0, 0, 0)
            }
        }
    }
}
