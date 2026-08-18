package com.adpluga.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal val AdPlugaJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

@Serializable
internal data class AdDto(
    val id: String,
    val type: String,
    @SerialName("asset_url") val assetUrl: String? = null,
    val html: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("cta_text") val ctaText: String? = null,
    @SerialName("sponsored_by") val sponsoredBy: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("main_image_url") val mainImageUrl: String? = null,
    // Deprecated: native assets arrive as flat fields above; kept as a
    // defensive fallback for legacy responses.
    val native: Map<String, JsonElement>? = null,
    @SerialName("vast_url") val vastUrl: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("skippable_after_ms") val skippableAfterMs: Int = 0,
    @SerialName("reward_amount") val rewardAmount: Int = 0,
    @SerialName("reward_currency") val rewardCurrency: String = "USD",
    val format: String? = null,
    @SerialName("advertiser_name") val advertiserName: String? = null,
) {
    fun toModel(source: AdSource): Ad = Ad(
        id = id,
        kind = AdKind.fromWire(type),
        source = source,
        assetUrl = assetUrl ?: videoUrl ?: audioUrl ?: vastUrl,
        html = html,
        nativeAssets = buildNativeAssets(),
        width = width,
        height = height,
        durationMs = durationMs,
        skippableAfterMs = skippableAfterMs,
        rewardAmount = rewardAmount,
        rewardCurrency = rewardCurrency,
        format = format,
        advertiserName = advertiserName,
    )

    // buildNativeAssets prefers the flat contract fields and falls back to
    // the legacy nested `native` object. Flat values win on key collision.
    private fun buildNativeAssets(): Map<String, String?>? {
        val nested = native?.mapValues { (_, v) -> v.asPrimitiveOrNull() } ?: emptyMap()
        val flat = buildMap {
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            ctaText?.let { put("cta_text", it) }
            sponsoredBy?.let { put("sponsored_by", it) }
            iconUrl?.let { put("icon_url", it) }
            mainImageUrl?.let { put("main_image_url", it) }
        }
        val merged = nested + flat
        return merged.ifEmpty { null }
    }
}

@Serializable
internal data class ServeResponseDto(
    val ad: AdDto,
    @SerialName("impression_url") val impressionUrl: String? = null,
    @SerialName("click_url") val clickUrl: String? = null,
    @SerialName("conversion_url") val conversionUrl: String? = null,
    @SerialName("track_token") val trackToken: String,
    @SerialName("conversion_token") val conversionToken: String? = null,
    val source: String,
    @SerialName("quartile_pings") val quartilePings: Map<String, String>? = null,
) {
    fun toModel(): ServeResponse {
        val src = AdSource.fromWire(source)
        return ServeResponse(
            ad = ad.toModel(src),
            impressionUrl = impressionUrl,
            clickUrl = clickUrl,
            conversionUrl = conversionUrl,
            trackToken = trackToken,
            conversionToken = conversionToken,
            quartilePings = quartilePings,
        )
    }
}

@Serializable
internal data class FeaturesDto(
    val flags: Map<String, Boolean> = emptyMap(),
    @SerialName("sdk_min_version") val sdkMinVersion: String? = null,
)

@Serializable
internal data class TelemetryPayloadDto(
    val sdk: SdkInfo,
    val nonce: String,
    val events: List<TelemetryEventDto>,
)

@Serializable
internal data class SdkInfo(
    val platform: String,
    val version: String,
)

@Serializable
internal data class TelemetryEventDto(
    val type: String,
    val count: Int,
    val p50: Int? = null,
    val p95: Int? = null,
    val p99: Int? = null,
)

private fun JsonElement.asPrimitiveOrNull(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> this.contentOrNullIfBlank()
    is JsonObject -> this.toString()
    else -> this.toString()
}

private fun JsonPrimitive.contentOrNullIfBlank(): String? {
    val c = jsonPrimitive.content
    return c.ifBlank { null }
}
