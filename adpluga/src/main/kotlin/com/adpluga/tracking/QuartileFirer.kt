package com.adpluga.tracking

import com.adpluga.logger.AdPlugaLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap.newKeySet

internal class QuartileFirer(
    private val pings: Map<String, String>?,
    private val scope: CoroutineScope,
    private val endpoint: String? = null,
) {
    private val fired = newKeySet<String>()

    fun update(positionMs: Long, durationMs: Long) {
        val map = pings
        if (map.isNullOrEmpty() || durationMs <= 0) return
        for (t in THRESHOLDS) {
            if (fired.contains(t.key)) continue
            val threshold = (durationMs * t.ratio).toLong()
            if (positionMs < threshold) continue
            fired.add(t.key)
            val url = map[t.key] ?: continue
            if (url.isEmpty()) continue
            fire(url)
        }
    }

    fun reset() = fired.clear()

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = endpoint ?: return raw
        return try {
            URL(URL(base), raw).toString()
        } catch (_: Throwable) {
            raw
        }
    }

    private fun fire(url: String) {
        val resolved = resolveUrl(url)
        scope.launch(Dispatchers.IO) {
            try {
                val conn = URL(resolved).openConnection() as HttpURLConnection
                conn.connectTimeout = 3_000
                conn.readTimeout = 3_000
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                conn.connect()
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AdPlugaLogger.debug("quartile beacon failed url=$resolved", t)
            }
        }
    }

    private data class Threshold(val key: String, val ratio: Double)

    private companion object {
        val THRESHOLDS = listOf(
            Threshold("start", 0.0),
            Threshold("first_quartile", 0.25),
            Threshold("midpoint", 0.5),
            Threshold("third_quartile", 0.75),
            Threshold("complete", 1.0),
        )
    }
}
