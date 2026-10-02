package com.ironsource.adapters.custom.adpluga

import android.app.Activity
import android.os.Looper
import com.adpluga.AdPluga
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.adunit.adapter.listener.InterstitialAdListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrorType
import com.ironsource.mediationsdk.model.NetworkSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdPlugaLevelPlayTest {

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
    fun `smart banner follows LevelPlay's width rule`() {
        assertEquals(320 to 50, AdPlugaLevelPlay.dimensions(ISBannerSize.SMART, 411))
        assertEquals(728 to 90, AdPlugaLevelPlay.dimensions(ISBannerSize.SMART, 800))
        assertEquals(300 to 250, AdPlugaLevelPlay.dimensions(ISBannerSize.RECTANGLE, 411))
    }

    @Test
    fun `an instance without a slot cannot serve`() {
        assertNull(AdPlugaLevelPlay.slot(adData(slot = null)))
        assertNull(AdPlugaLevelPlay.slot(adData(slot = "slot_1")))
    }

    @Test
    fun `the house fallback is no fill so the waterfall moves on`() {
        AdPluga.initialize(publisherKey = "pk_test_abcdef123", endpoint = server.url("/").toString().trimEnd('/'))
        server.enqueue(MockResponse().setBody(body("house")))
        val events = Collections.synchronizedList(mutableListOf<Pair<String, Any?>>())
        val listener = Proxy.newProxyInstance(
            InterstitialAdListener::class.java.classLoader, arrayOf(InterstitialAdListener::class.java),
        ) { _, m, args -> events += m.name to args?.firstOrNull(); null } as InterstitialAdListener
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        AdPlugaCustomInterstitial(NetworkSettings("AdPluga")).loadAd(adData(slot = "slot_1"), activity, listener)

        val deadline = System.currentTimeMillis() + 5_000
        while (events.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(listOf("onAdLoadFailed" to AdapterErrorType.ADAPTER_ERROR_TYPE_NO_FILL), events)
    }

    private fun adData(slot: String?) = AdData(
        "",
        buildMap<String, Any> { if (slot != null) put(AdPlugaLevelPlay.SLOT_ID, slot) },
        emptyMap(),
    )

    private fun body(source: String) = """
        {"ad":{"id":"a1","type":"image","asset_url":"https://cdn.example/a.png","width":320,"height":480},
         "click_url":"https://edge.example/c","track_token":"t","source":"$source"}
    """.trimIndent()
}
