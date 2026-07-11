package com.adpluga.model

public class FeaturesView internal constructor(
    private val flags: Map<String, Boolean>,
    public val sdkMinVersion: String?,
) {
    public fun flag(key: String, fallback: Boolean = false): Boolean = flags[key] ?: fallback

    public fun snapshot(): Map<String, Boolean> = flags.toMap()

    public companion object {
        public val EMPTY: FeaturesView = FeaturesView(emptyMap(), null)
    }
}
