package com.adpluga.model

import com.adpluga.errors.AdPlugaError

public enum class AdKind(public val wire: String) {
    IMAGE("image"),
    HTML("html"),
    NATIVE("native"),
    TEMPLATE("template"),
    VIDEO("video"),
    VIDEO_REWARDED("video_rewarded"),
    AUDIO("audio"),
    CAROUSEL("carousel");

    public companion object {
        public fun fromWire(wire: String): AdKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw AdPlugaError.UnsupportedFormat(wire)
    }
}

public enum class AdSource(public val wire: String) {
    POOL("pool"),
    DIRETO("direto"),
    HOUSE("house"),
    DEAL("deal"),
    MEDIATION("mediation"),
    TEST("test");

    public companion object {
        public fun fromWire(wire: String): AdSource =
            entries.firstOrNull { it.wire == wire } ?: HOUSE
    }
}

/**
 * One card of a carousel. The whole deck shares the ad's click token and its
 * single impression, so swiping never mints or spends anything extra.
 */
public data class Slide(
    public val assetUrl: String,
    public val title: String? = null,
    public val body: String? = null,
    public val ctaText: String? = null,
)

public data class Ad(
    public val id: String,
    public val kind: AdKind,
    public val source: AdSource,
    public val assetUrl: String?,
    public val html: String?,
    public val billingUrl: String?,
    public val nativeAssets: Map<String, String?>?,
    public val width: Int,
    public val height: Int,
    public val durationMs: Int,
    public val skippableAfterMs: Int,
    public val rewardAmount: Int,
    public val rewardCurrency: String,
    public val format: String?,
    public val advertiserName: String?,

    /**
     * Announced by a screen reader in place of the creative. An ad is never
     * decorative, so a view with nothing here falls back to the title rather
     * than leaving the image unlabelled.
     */
    public val altText: String? = null,
    public val slides: List<Slide> = emptyList(),
    public val isTest: Boolean = false,
)

public data class ServeResponse(
    public val ad: Ad,
    public val impressionUrl: String?,
    public val clickUrl: String?,
    public val conversionUrl: String?,
    public val trackToken: String,
    public val conversionToken: String?,
    public val quartilePings: Map<String, String>?,
    /**
     * Publisher-configured rotation cadence for this slot, in seconds.
     * 0 means the slot must not rotate.
     */
    public val refreshAfterSeconds: Int = 0,
)
