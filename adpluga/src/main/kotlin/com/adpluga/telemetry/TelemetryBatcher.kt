package com.adpluga.telemetry

import com.adpluga.client.HttpTransport
import com.adpluga.config.Constants
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.model.AdPlugaJson
import com.adpluga.model.SdkInfo
import com.adpluga.model.TelemetryEventDto
import com.adpluga.model.TelemetryPayloadDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlin.random.Random

public enum class SdkEventType(public val wire: String) {
    Init("init"),
    ServeRequest("serve_request"),
    Impression("impression"),
    Click("click"),
    Error("error"),
    UpgradeRequired("upgrade_required"),
}

internal class TelemetryBatcher(
    private val transport: HttpTransport,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {

    private val buckets = HashMap<SdkEventType, Bucket>()
    private val mutex = Mutex()
    private var flushJob: Job? = null

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var totalRecorded: Int = 0

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            scope.launch { clear() }
        }
    }

    fun start() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (true) {
                delay(Constants.TELEMETRY_FLUSH_INTERVAL_MS)
                try {
                    flush()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    AdPlugaLogger.debug("telemetry flush failed", t)
                }
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
    }

    suspend fun record(type: SdkEventType, latencyMs: Int? = null) {
        if (!enabled) return
        mutex.withLock {
            val bucket = buckets.getOrPut(type) { Bucket() }
            bucket.count += 1
            if (latencyMs != null) {
                val samples = bucket.latencies
                if (samples.size < Constants.TELEMETRY_LATENCY_SAMPLE_CAP) {
                    samples.add(latencyMs)
                } else {
                    val idx = random.nextInt(bucket.count)
                    if (idx < Constants.TELEMETRY_LATENCY_SAMPLE_CAP) {
                        samples[idx] = latencyMs
                    }
                }
            }
            totalRecorded += 1
        }
    }

    suspend fun flush() {
        if (!enabled) {
            clear()
            return
        }
        val snapshot: List<TelemetryEventDto>
        mutex.withLock {
            if (buckets.isEmpty()) return
            snapshot = buckets.entries.take(Constants.TELEMETRY_MAX_EVENTS_PER_BATCH)
                .map { (type, bucket) -> bucket.toDto(type) }
            buckets.clear()
            totalRecorded = 0
        }
        if (snapshot.isEmpty()) return
        val payload = TelemetryPayloadDto(
            sdk = SdkInfo(platform = Constants.SDK_PLATFORM, version = Constants.SDK_VERSION),
            nonce = randomNonce(),
            events = snapshot,
        )
        val body = AdPlugaJson.encodeToString(TelemetryPayloadDto.serializer(), payload)
        transport.postTelemetry(body)
    }

    suspend fun clear() {
        mutex.withLock {
            buckets.clear()
            totalRecorded = 0
        }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        val sb = StringBuilder(32)
        for (b in bytes) {
            sb.append(HEX[(b.toInt() shr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private class Bucket {
        var count: Int = 0
        val latencies: ArrayList<Int> = ArrayList(Constants.TELEMETRY_LATENCY_SAMPLE_CAP)

        fun toDto(type: SdkEventType): TelemetryEventDto {
            if (latencies.isEmpty()) {
                return TelemetryEventDto(type = type.wire, count = count)
            }
            val sorted = latencies.toIntArray().also { it.sort() }
            return TelemetryEventDto(
                type = type.wire,
                count = count,
                p50 = percentile(sorted, 50),
                p95 = percentile(sorted, 95),
                p99 = percentile(sorted, 99),
            )
        }
    }

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()
        private val LIST_SERIALIZER = ListSerializer(TelemetryEventDto.serializer())

        private fun percentile(sorted: IntArray, p: Int): Int {
            if (sorted.isEmpty()) return 0
            val idx = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
    }
}
