package com.adpluga.consent

public data class ConsentState(
    val gdpr: Boolean = false,
    val adPersonalization: Boolean = true,
    val limitedTracking: Boolean = false,
    val ccpaOptOut: Boolean = false,
    val tcfString: String? = null,
) {
    public val isPersonalized: Boolean
        get() = adPersonalization && !limitedTracking && !ccpaOptOut
}
