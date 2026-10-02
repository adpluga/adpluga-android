package com.ironsource.adapters.custom.adpluga

import com.adpluga.errors.AdPlugaError
import com.adpluga.mediation.MediationSlot
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrorType
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrors
import com.ironsource.mediationsdk.adunit.adapter.internal.listener.AdapterAdListener

/** Field names and mappings shared by the AdPluga LevelPlay ad units. */
internal object AdPlugaLevelPlay {
    /** App-level field: the AdPluga publisher key. */
    const val PUBLISHER_KEY: String = "publisher_key"

    /** Instance-level field: the AdPluga slot. */
    const val SLOT_ID: String = "slot_id"

    fun publisherKey(adData: AdData): String? = adData.getString(PUBLISHER_KEY)?.trim()?.takeIf { it.isNotEmpty() }

    /** The slot named by the instance, with AdPluga initialised; null when it cannot serve. */
    fun slot(adData: AdData): MediationSlot? {
        val slot = MediationSlot.parse(adData.getString(SLOT_ID)) ?: return null
        val withKey = if (slot.publisherKey == null) slot.copy(publisherKey = publisherKey(adData)) else slot
        return try {
            withKey.ensureInitialized()
            withKey
        } catch (_: Throwable) {
            null
        }
    }

    fun missingParams(listener: AdapterAdListener) = listener.onAdLoadFailed(
        AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL,
        AdapterErrors.ADAPTER_ERROR_MISSING_PARAMS,
        "the instance names no AdPluga slot, or AdPluga is not initialised",
    )

    fun loadFailed(listener: AdapterAdListener, t: Throwable) = if (t is AdPlugaError.NoFill) {
        listener.onAdLoadFailed(AdapterErrorType.ADAPTER_ERROR_TYPE_NO_FILL, AdapterErrors.ADAPTER_ERROR_INTERNAL, "no paid AdPluga ad")
    } else {
        listener.onAdLoadFailed(AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL, AdapterErrors.ADAPTER_ERROR_INTERNAL, t.message)
    }

    /** Dimensions in dp; SMART follows LevelPlay's own rule (leaderboard above 720dp). */
    fun dimensions(size: ISBannerSize, screenWidthDp: Int): Pair<Int, Int> = when {
        size.isSmart -> if (screenWidthDp > 720) 728 to 90 else 320 to 50
        size.width > 0 && size.height > 0 -> size.width to size.height
        else -> 320 to 50
    }
}
