package com.adpluga.model

import com.adpluga.errors.AdPlugaError

public enum class AdKind(public val wire: String) {
    IMAGE("image"),
    HTML("html"),
    NATIVE("native"),
    TEMPLATE("template"),
    VIDEO("video"),
    VIDEO_REWARDED("video_rewarded"),
    AUDIO("audio");

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

public data class Ad(
    public val id: String,
    public val kind: AdKind,
    public val source: AdSource,
    public val assetUrl: String?,
    public val html: String?,
    public val nativeAssets: Map<String, String?>?,
    public val width: Int,
    public val height: Int,
    public val durationMs: Int,
    public val skippableAfterMs: Int,
    public val rewardAmount: Int,
    public val rewardCurrency: String,
    public val format: String?,
    public val advertiserName: String?,
)

public data class ServeResponse(
    public val ad: Ad,
    public val impressionUrl: String?,
    public val clickUrl: String?,
    public val conversionUrl: String?,
    public val trackToken: String,
    public val conversionToken: String?,
    public val quartilePings: Map<String, String>?,
)
