package com.adpluga.features

import com.adpluga.client.HttpTransport
import com.adpluga.config.Constants
import com.adpluga.errors.AdPlugaError
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.model.FeaturesView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArraySet

internal class FeaturesCache(
    private val transport: HttpTransport,
    private val scope: CoroutineScope,
    private val onUpgradeRequired: (String) -> Unit,
) {

    @Volatile
    private var current: FeaturesView = FeaturesView.EMPTY

    @Volatile
    private var etag: String? = null

    @Volatile
    private var lastFetchMs: Long = 0L

    private val fetchLock = Mutex()
    private val listeners = CopyOnWriteArraySet<(FeaturesView) -> Unit>()
    private var revalidatorJob: Job? = null

    val view: FeaturesView get() = current

    fun start() {
        if (revalidatorJob?.isActive == true) return
        revalidatorJob = scope.launch {
            while (true) {
                delay(Constants.FEATURES_REVALIDATE_MS)
                try {
                    ensure(force = true)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    AdPlugaLogger.debug("features revalidate failed", t)
                }
            }
        }
    }

    fun stop() {
        revalidatorJob?.cancel()
        revalidatorJob = null
    }

    fun addListener(listener: (FeaturesView) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (FeaturesView) -> Unit) {
        listeners.remove(listener)
    }

    suspend fun ensure(force: Boolean = false): FeaturesView {
        val now = System.currentTimeMillis()
        if (!force && current !== FeaturesView.EMPTY &&
            now - lastFetchMs < Constants.FEATURES_MIN_INTERVAL_MS
        ) {
            return current
        }
        return fetchLock.withLock {
            val since = System.currentTimeMillis() - lastFetchMs
            if (!force && current !== FeaturesView.EMPTY && since < Constants.FEATURES_MIN_INTERVAL_MS) {
                return@withLock current
            }
            try {
                val result = transport.fetchFeatures(etag)
                lastFetchMs = System.currentTimeMillis()
                when (result) {
                    is HttpTransport.FeaturesResult.NotModified -> current
                    is HttpTransport.FeaturesResult.Updated -> {
                        current = result.view
                        etag = result.etag
                        notify(result.view)
                        current
                    }
                }
            } catch (upgrade: AdPlugaError.UpgradeRequired) {
                onUpgradeRequired(upgrade.minVersion)
                current
            }
        }
    }

    private fun notify(view: FeaturesView) {
        for (listener in listeners) {
            try {
                listener(view)
            } catch (t: Throwable) {
                AdPlugaLogger.warn("features listener failed", t)
            }
        }
    }
}
