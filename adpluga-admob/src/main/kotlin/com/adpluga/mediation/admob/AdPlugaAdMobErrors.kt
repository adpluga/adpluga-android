package com.adpluga.mediation.admob

import com.adpluga.errors.AdPlugaError
import com.google.android.gms.ads.AdError

internal object AdPlugaAdMobErrors {
    const val DOMAIN: String = "com.adpluga.mediation.admob"

    const val INVALID_PARAMETER: Int = 101
    const val NO_FILL: Int = 102
    const val LOAD_FAILED: Int = 103
    const val NO_ACTIVITY: Int = 104

    fun invalidParameter(): AdError =
        AdError(INVALID_PARAMETER, "The custom event parameter names no AdPluga slot", DOMAIN)

    fun noActivity(): AdError =
        AdError(NO_ACTIVITY, "AdPluga needs an Activity to show a full-screen ad", DOMAIN)

    fun from(t: Throwable): AdError = when (t) {
        is AdPlugaError.NoFill -> AdError(NO_FILL, "AdPluga has no paid ad for this slot", DOMAIN)
        else -> AdError(LOAD_FAILED, t.message ?: "AdPluga failed to load", DOMAIN)
    }
}
