package com.adpluga.mediation.applovin

import android.os.Looper
import com.adpluga.AdPluga
import com.adpluga.errors.AdPlugaError
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdPlugaMaxBridgeTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        AdPluga.maybeInstance?.destroy()
        server.shutdown()
    }

    @Test
    fun `errors map to the MAX codes the waterfall acts on`() {
        assertEquals(MaxAdapterError.NO_FILL, AdPlugaMaxBridge.maxError(AdPlugaError.NoFill))
        assertEquals(MaxAdapterError.NOT_INITIALIZED, AdPlugaMaxBridge.maxError(AdPlugaError.NotInitialized))
        assertEquals(MaxAdapterError.UNSPECIFIED, AdPlugaMaxBridge.maxError(IllegalStateException("x")))
    }

    @Test
    fun `the App ID initialises AdPluga`() {
        val slot = AdPlugaMaxBridge.slotFor("slot_1", " pk_test_abcdef123 ")
        assertEquals("slot_1", slot?.slotId)
        assertNotNull(AdPluga.maybeInstance)
    }

    @Test
    fun `placement without a key fails until AdPluga is initialised`() {
        assertNull(AdPlugaMaxBridge.slotFor("slot_1", null))
        assertNull(AdPlugaMaxBridge.slotFor("", "pk_test_abcdef123"))
    }

    @Test
    fun `the house fallback is no fill so the waterfall moves on`() {
        server.enqueue(MockResponse().setBody(body("house")))
        initialize()
        val calls = record<MaxInterstitialAdapterListener>()
        AdPlugaMaxBridge().loadInterstitial("slot_1", null, calls.listener)
        calls.await()
        assertEquals(listOf("onInterstitialAdLoadFailed" to MaxAdapterError.NO_FILL), calls.events)
    }

    @Test
    fun `paid demand loads`() {
        server.enqueue(MockResponse().setBody(body("platform_mediation")))
        initialize()
        val calls = record<MaxInterstitialAdapterListener>()
        AdPlugaMaxBridge().loadInterstitial("slot_1", null, calls.listener)
        calls.await()
        assertEquals("onInterstitialAdLoaded", calls.events.single().first)
        assertNotNull(server.takeRequest().requestUrl?.queryParameter("slot"))
    }

    private fun initialize() {
        AdPluga.initialize(publisherKey = "pk_test_abcdef123", endpoint = server.url("/").toString().trimEnd('/'))
    }

    private fun body(source: String) = """
        {"ad":{"id":"a1","type":"image","asset_url":"https://cdn.example/a.png","width":320,"height":480},
         "click_url":"https://edge.example/c","track_token":"t","source":"$source"}
    """.trimIndent()

    private class Calls<T>(val listener: T, val events: MutableList<Pair<String, Any?>>) {
        fun await() {
            val deadline = System.currentTimeMillis() + 5_000
            while (events.isEmpty() && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
    }

    private inline fun <reified T> record(): Calls<T> {
        val events = Collections.synchronizedList(mutableListOf<Pair<String, Any?>>())
        val proxy = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, args ->
            events += m.name to args?.firstOrNull()
            null
        } as T
        return Calls(proxy, events)
    }
}
