package com.ironsource.adapters.custom.adpluga

import android.content.Context
import com.adpluga.AdPluga
import com.adpluga.mediation.levelplay.BuildConfig
import com.ironsource.mediationsdk.adunit.adapter.BaseAdapter
import com.ironsource.mediationsdk.adunit.adapter.listener.NetworkInitializationListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrors

/**
 * LevelPlay custom network adapter for AdPluga. Register the network with an
 * app-level field `publisher_key` and an instance-level field `slot_id`.
 * Banner, interstitial and rewarded video.
 */
public class AdPlugaCustomAdapter : BaseAdapter() {

    override fun init(adData: AdData, context: Context, listener: NetworkInitializationListener?) {
        try {
            val key = AdPlugaLevelPlay.publisherKey(adData)
            if (AdPluga.maybeInstance == null && key != null) AdPluga.initialize(publisherKey = key)
            listener?.onInitSuccess()
        } catch (t: Throwable) {
            listener?.onInitFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, t.message)
        }
    }

    override fun getNetworkSDKVersion(): String = AdPluga.SDK_VERSION

    override fun getAdapterVersion(): String = BuildConfig.ADAPTER_VERSION
}
