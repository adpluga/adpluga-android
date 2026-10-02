package com.adpluga

import androidx.annotation.MainThread
import com.adpluga.client.HttpTransport
import com.adpluga.config.Constants
import com.adpluga.consent.ConsentState
import com.adpluga.consent.ConsentStore
import com.adpluga.errors.AdPlugaError
import com.adpluga.events.SdkEvent
import com.adpluga.features.FeaturesCache
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.model.Ad
import com.adpluga.model.FeaturesView
import com.adpluga.model.ServeResponse
import com.adpluga.telemetry.SdkEventType
import com.adpluga.telemetry.TelemetryBatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

public class AdPluga private constructor(
    public val publisherKey: String,
    public val endpoint: String,
    initialConsent: ConsentState,
    telemetryEnabled: Boolean,
    private val onUpgradeRequired: ((String) -> Unit)?,
    clientProvider: () -> OkHttpClient,
) {

    internal val consentStore: ConsentStore = ConsentStore(initialConsent)
    internal val internalScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + rootExceptionHandler)

    private val transport: HttpTransport = HttpTransport(
        publisherKey = publisherKey,
        endpoint = endpoint,
        consent = consentStore,
        clientProvider = clientProvider,
    )
    private val features: FeaturesCache = FeaturesCache(
        transport = transport,
        scope = internalScope,
        onUpgradeRequired = ::handleUpgrade,
    )
    private val telemetry: TelemetryBatcher = TelemetryBatcher(
        transport = transport,
        scope = internalScope,
    )

    private val _events = MutableSharedFlow<SdkEvent>(extraBufferCapacity = 128)
    public val events: SharedFlow<SdkEvent> = _events.asSharedFlow()

    private val upgradeBlocked = AtomicBoolean(false)
    private val upgradeNotified = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    private val userTelemetryEnabled: Boolean = telemetryEnabled

    public val isUpgradeBlocked: Boolean get() = upgradeBlocked.get()

    public val consent: ConsentState get() = consentStore.state

    private val installId = AtomicReference<String?>(null)

    /**
     * First-party install id used for frequency capping and first-party
     * audiences. Only released when the current consent state allows
     * personalisation; without it the request carries no user at all and the
     * server skips both gates. Held in memory for the process lifetime — pass
     * `userHash` explicitly to key the daily cap across app launches.
     */
    private fun resolvedUserId(): String? {
        if (!consentStore.state.isPersonalized) return null
        installId.get()?.let { return it }
        val generated = UUID.randomUUID().toString().replace("-", "")
        return if (installId.compareAndSet(null, generated)) generated else installId.get()
    }

    public val featuresView: FeaturesView get() = features.view

    private fun start() {
        telemetry.setEnabled(userTelemetryEnabled)
        telemetry.start()
        features.addListener { view ->
            val remote = view.flag("sdk_telemetry", fallback = true)
            telemetry.setEnabled(remote && userTelemetryEnabled)
            emit(SdkEvent.FeaturesUpdated(view.snapshot()))
        }
        features.start()
        internalScope.launch {
            try {
                telemetry.record(SdkEventType.Init)
            } catch (t: Throwable) {
                AdPlugaLogger.debug("initial telemetry record failed", t)
            }
        }
        emit(SdkEvent.InitCompleted())
    }

    public suspend fun serve(
        slotId: String,
        format: String? = null,
        userHash: String? = null,
        refreshSeq: Int = 0,
    ): ServeResponse? {
        if (destroyed.get()) throw AdPlugaError.NotInitialized
        if (upgradeBlocked.get()) return null
        val started = System.currentTimeMillis()
        return try {
            val response = transport.serve(slotId, format, userHash ?: resolvedUserId(), refreshSeq)
            val latency = (System.currentTimeMillis() - started).toInt()
            telemetry.record(SdkEventType.ServeRequest, latency)
            if (response != null) {
                emit(SdkEvent.AdServed(slotId = slotId, ad = response.ad))
            }
            response
        } catch (upgrade: AdPlugaError.UpgradeRequired) {
            handleUpgrade(upgrade.minVersion)
            null
        } catch (t: Throwable) {
            telemetry.record(SdkEventType.Error)
            emit(SdkEvent.AdFailed(slotId = slotId, cause = t))
            AdPlugaLogger.warn("serve failed slot=$slotId", t)
            null
        }
    }

    /**
     * True while running against a sandbox key. Used for the cadence floor
     * before any response has arrived.
     */
    public val isTestKey: Boolean get() = publisherKey.startsWith("pk_test_")

    public fun fireImpression(slotId: String, ad: Ad, impressionUrl: String?, trackToken: String) {
        if (destroyed.get()) return
        internalScope.launch {
            try {
                if (!impressionUrl.isNullOrBlank()) {
                    transport.beacon(impressionUrl)
                } else {
                    transport.postTrack(
                        path = "v1/track",
                        token = trackToken,
                        payload = mapOf("event" to "impression", "slot" to slotId, "ad" to ad.id),
                    )
                }
                ad.impressionTrackers.forEach { transport.beacon(it) }
                telemetry.record(SdkEventType.Impression)
                emit(SdkEvent.Impression(slotId = slotId, adId = ad.id))
            } catch (t: Throwable) {
                AdPlugaLogger.debug("impression fire failed slot=$slotId", t)
            }
        }
    }

    public fun fireViewable(slotId: String, ad: Ad, trackToken: String) {
        if (destroyed.get()) return
        internalScope.launch {
            try {
                // Mediation fills carry no AdPluga track token: the billable
                // impression is reported to the bidder by firing its burl once.
                // First-party fills report the viewable to /track/viewable.
                val billingUrl = ad.billingUrl
                if (!billingUrl.isNullOrBlank()) {
                    transport.beacon(billingUrl)
                }
                if (trackToken.isNotBlank()) {
                    transport.postTrack(
                        path = "v1/track/viewable",
                        token = trackToken,
                        payload = mapOf("event" to "viewable", "slot" to slotId),
                    )
                }
            } catch (t: Throwable) {
                AdPlugaLogger.debug("viewable fire failed slot=$slotId", t)
            }
        }
    }

    public fun fireClick(slotId: String, ad: Ad, clickUrl: String?, trackToken: String) {
        if (destroyed.get()) return
        internalScope.launch {
            try {
                if (!clickUrl.isNullOrBlank()) {
                    transport.beacon(clickUrl)
                } else {
                    transport.postTrack(
                        path = "v1/click",
                        token = trackToken,
                        payload = mapOf("event" to "click", "slot" to slotId, "ad" to ad.id),
                    )
                }
                ad.clickTrackers.forEach { transport.beacon(it) }
                telemetry.record(SdkEventType.Click)
                emit(SdkEvent.Click(slotId = slotId, adId = ad.id))
            } catch (t: Throwable) {
                AdPlugaLogger.debug("click fire failed slot=$slotId", t)
            }
        }
    }

    public fun conversion(token: String, payload: Map<String, String> = emptyMap()) {
        if (destroyed.get()) return
        internalScope.launch {
            try {
                transport.postTrack(path = "v1/conversion", token = token, payload = payload)
            } catch (t: Throwable) {
                AdPlugaLogger.debug("conversion fire failed", t)
            }
        }
    }

    public fun setConsent(state: ConsentState) {
        val changed = consentStore.update(state)
        if (changed) emit(SdkEvent.ConsentChanged(state))
    }

    public suspend fun ensureFeatures(): FeaturesView = features.ensure(force = true)

    public suspend fun flushTelemetry(): Unit = telemetry.flush()

    @MainThread
    public fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        try {
            features.stop()
            telemetry.stop()
            internalScope.cancel()
            transport.close()
        } catch (t: Throwable) {
            AdPlugaLogger.debug("destroy error", t)
        }
        synchronized(lock) {
            if (instanceRef === this) instanceRef = null
        }
    }

    private fun handleUpgrade(minVersion: String) {
        upgradeBlocked.set(true)
        if (upgradeNotified.compareAndSet(false, true)) {
            emit(SdkEvent.UpgradeRequiredEvent(minVersion))
            try {
                onUpgradeRequired?.invoke(minVersion)
            } catch (t: Throwable) {
                AdPlugaLogger.warn("onUpgradeRequired callback failed", t)
            }
        }
    }

    private fun emit(event: SdkEvent) {
        _events.tryEmit(event)
    }

    internal fun buildBannerBinding(slotId: String, response: ServeResponse): BannerBinding =
        BannerBinding(
            adPluga = this,
            slotId = slotId,
            response = response,
        )

    public companion object {
        private val lock = Any()

        @Volatile
        private var instanceRef: AdPluga? = null

        public val maybeInstance: AdPluga? get() = instanceRef

        public val instance: AdPluga
            get() = instanceRef ?: throw AdPlugaError.NotInitialized

        /**
         * The endpoint used when none is passed. Exposed so a host that reads
         * the endpoint from its own configuration can fall back to it without
         * hardcoding an AdPluga hostname.
         */
        public const val DEFAULT_ENDPOINT: String = Constants.DEFAULT_ENDPOINT

        /** This SDK's version, reported by the mediation adapters to their host. */
        public const val SDK_VERSION: String = Constants.SDK_VERSION

        @JvmStatic
        @JvmOverloads
        public fun initialize(
            publisherKey: String,
            endpoint: String = Constants.DEFAULT_ENDPOINT,
            consent: ConsentState = ConsentState(),
            telemetryEnabled: Boolean = true,
            onUpgradeRequired: ((String) -> Unit)? = null,
            clientProvider: () -> OkHttpClient = { defaultClient() },
        ): AdPluga {
            synchronized(lock) {
                instanceRef?.let { existing ->
                    // Rotating a key revokes the previous one at once, so
                    // silently keeping the old instance would leave the app
                    // serving with a dead key and nothing to signal it.
                    if (existing.publisherKey != publisherKey) {
                        throw AdPlugaError.AlreadyInitialized(existing.publisherKey, publisherKey)
                    }
                    return existing
                }
                if (!Constants.KEY_PATTERN.matches(publisherKey)) {
                    throw AdPlugaError.InvalidKey(publisherKey)
                }
                val normalized = endpoint.trimEnd('/')
                val inst = AdPluga(
                    publisherKey = publisherKey,
                    endpoint = normalized,
                    initialConsent = consent,
                    telemetryEnabled = telemetryEnabled,
                    onUpgradeRequired = onUpgradeRequired,
                    clientProvider = clientProvider,
                )
                instanceRef = inst
                inst.start()
                return inst
            }
        }

        private val rootExceptionHandler: kotlinx.coroutines.CoroutineExceptionHandler =
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                AdPlugaLogger.warn("uncaught coroutine error", throwable)
            }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build()
    }
}

internal data class BannerBinding(
    val adPluga: AdPluga,
    val slotId: String,
    val response: ServeResponse,
)
