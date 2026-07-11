package com.adpluga.config

import com.adpluga.BuildConfig

internal object Constants {
    const val SDK_PLATFORM: String = BuildConfig.SDK_PLATFORM
    const val SDK_VERSION: String = BuildConfig.SDK_VERSION
    const val DEFAULT_ENDPOINT: String = "https://edge.adpluga.com"

    const val VIEWABILITY_THRESHOLD: Double = 0.5
    const val VIEWABILITY_DURATION_MS: Long = 1_000L
    const val VIEWABILITY_TICK_MS: Long = 200L

    const val TELEMETRY_FLUSH_INTERVAL_MS: Long = 300_000L
    const val TELEMETRY_FLUSH_ON_COUNT: Int = 100
    const val TELEMETRY_LATENCY_SAMPLE_CAP: Int = 128
    const val TELEMETRY_MAX_EVENTS_PER_BATCH: Int = 256

    const val FEATURES_REVALIDATE_MS: Long = 300_000L
    const val FEATURES_MIN_INTERVAL_MS: Long = 30_000L

    const val NETWORK_SERVE_TIMEOUT_MS: Long = 3_000L
    const val NETWORK_TRACK_TIMEOUT_MS: Long = 5_000L
    const val NETWORK_RETRY_MAX_ATTEMPTS: Int = 2
    const val NETWORK_RETRY_BASE_BACKOFF_MS: Long = 200L

    const val KEY_HEADER: String = "X-AdPluga-Key"
    const val PLATFORM_HEADER: String = "X-Adpluga-Sdk-Platform"
    const val VERSION_HEADER: String = "X-Adpluga-Sdk-Version"
    const val UPGRADE_HEADER: String = "X-Adpluga-Min-Sdk"

    val KEY_PATTERN: Regex = Regex("^pk_(live|test)_[A-Za-z0-9_-]{8,}$")
}
