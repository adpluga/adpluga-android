package com.adpluga

import com.adpluga.consent.ConsentState
import com.adpluga.errors.AdPlugaError
import com.adpluga.events.SdkEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdPlugaTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        AdPluga.maybeInstance?.destroy()
        server.shutdown()
    }

    @Test(expected = AdPlugaError.InvalidKey::class)
    fun `initialize rejects invalid publisher key`() {
        AdPluga.initialize(publisherKey = "nope")
    }

    @Test
    fun `serve returns parsed response with kind and source`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(displayFixture))
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        val response = pluga.serve(slotId = "slot_1", format = "banner_320x100")
        assertNotNull(response)
        assertEquals("ad_1", response!!.ad.id)
        assertEquals(com.adpluga.model.AdKind.IMAGE, response.ad.kind)
        assertEquals(com.adpluga.model.AdSource.HOUSE, response.ad.source)
        val recorded = server.takeRequest()
        assertEquals("pk_test_abcdef123", recorded.getHeader("X-AdPluga-Key"))
        assertEquals("android", recorded.getHeader("X-Adpluga-Sdk-Platform"))
    }

    @Test
    fun `serve short-circuits after 426 upgrade required`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(426).setHeader("X-Adpluga-Min-Sdk", "0.9.0")
        )
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        val first = pluga.serve("slot_1", "banner_320x100")
        assertNull(first)
        assertTrue(pluga.isUpgradeBlocked)
        val second = pluga.serve("slot_1", "banner_320x100")
        assertNull(second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `consent flip adds non_personalized query parameter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(displayFixture))
        server.enqueue(MockResponse().setResponseCode(200).setBody(displayFixture))
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        pluga.serve("slot_1")
        pluga.setConsent(ConsentState(gdpr = true, adPersonalization = false))
        pluga.serve("slot_1")
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue(first.path?.contains("non_personalized") == false)
        assertTrue(second.path?.contains("non_personalized=true") == true)
    }

    @Test
    fun `ensureFeatures reflects remote flag flip`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"flags":{"sdk_telemetry":true}}""")
        )
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        val view = pluga.ensureFeatures()
        assertTrue(view.flag("sdk_telemetry", fallback = false))
        assertEquals(false, view.flag("unknown", fallback = false))
    }

    @Test
    fun `serve accepts html format response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(htmlFixture))
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        val response = pluga.serve(slotId = "slot_html", format = "html")
        assertNotNull(response)
        assertEquals(com.adpluga.model.AdKind.HTML, response!!.ad.kind)
        assertNotNull(response.ad.html)
    }

    @Test
    fun `serve accepts video format with quartile pings`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(videoFixture))
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        val response = pluga.serve(slotId = "slot_video", format = "video")
        assertNotNull(response)
        assertEquals(com.adpluga.model.AdKind.VIDEO, response!!.ad.kind)
        assertEquals("https://cdn.adpluga.example/creatives/ad.mp4", response.ad.assetUrl)
        assertEquals(15_000, response.ad.durationMs)
        assertNotNull(response.quartilePings)
        assertEquals(5, response.quartilePings!!.size)
    }

    @Test
    fun `serve accepts video_rewarded format with skippable window`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(videoRewardedFixture))
        val pluga = AdPluga.initialize(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
        )
        val response = pluga.serve(slotId = "slot_rw", format = "video_rewarded")
        assertNotNull(response)
        assertEquals(com.adpluga.model.AdKind.VIDEO_REWARDED, response!!.ad.kind)
        assertEquals(30_000, response.ad.durationMs)
        assertEquals(5_000, response.ad.skippableAfterMs)
        assertEquals(10, response.ad.rewardAmount)
        assertEquals("COIN", response.ad.rewardCurrency)
    }

    private val displayFixture = """
        {
          "ad": {
            "id": "ad_1",
            "type": "image",
            "asset_url": "https://example.com/creative.png",
            "html": null,
            "native": null,
            "vast_url": null,
            "audio_url": null,
            "video_url": null,
            "width": 320,
            "height": 100,
            "duration_ms": 0,
            "skippable_after_ms": 0,
            "reward_amount": 0,
            "reward_currency": "USD",
            "format": "display"
          },
          "impression_url": "https://edge.adpluga.example/v1/imp?t=abc",
          "click_url": "https://edge.adpluga.example/v1/click?t=abc",
          "conversion_url": null,
          "track_token": "tok_abc",
          "conversion_token": null,
          "source": "house",
          "quartile_pings": null
        }
    """.trimIndent()

    private val htmlFixture = """
        {
          "ad": {
            "id": "ad_html_1",
            "type": "html",
            "asset_url": null,
            "html": "<html><body style='margin:0'><a href='https://landing.example/x'>promo</a></body></html>",
            "native": null,
            "vast_url": null,
            "audio_url": null,
            "video_url": null,
            "width": 320,
            "height": 250,
            "duration_ms": 0,
            "skippable_after_ms": 0,
            "reward_amount": 0,
            "reward_currency": "USD",
            "format": "html"
          },
          "impression_url": "https://edge.adpluga.example/v1/imp?t=html",
          "click_url": "https://edge.adpluga.example/v1/click?t=html",
          "conversion_url": null,
          "track_token": "tok_html",
          "conversion_token": null,
          "source": "house",
          "quartile_pings": null
        }
    """.trimIndent()

    private val videoFixture = """
        {
          "ad": {
            "id": "ad_video_1",
            "type": "video",
            "asset_url": null,
            "html": null,
            "native": null,
            "vast_url": null,
            "audio_url": null,
            "video_url": "https://cdn.adpluga.example/creatives/ad.mp4",
            "width": 640,
            "height": 360,
            "duration_ms": 15000,
            "skippable_after_ms": 0,
            "reward_amount": 0,
            "reward_currency": "USD",
            "format": "video"
          },
          "impression_url": "https://edge.adpluga.example/v1/imp?t=vid",
          "click_url": "https://edge.adpluga.example/v1/click?t=vid",
          "conversion_url": null,
          "track_token": "tok_vid",
          "conversion_token": null,
          "source": "house",
          "quartile_pings": {
            "start": "https://edge.adpluga.example/vast/start",
            "first_quartile": "https://edge.adpluga.example/vast/q1",
            "midpoint": "https://edge.adpluga.example/vast/q2",
            "third_quartile": "https://edge.adpluga.example/vast/q3",
            "complete": "https://edge.adpluga.example/vast/complete"
          }
        }
    """.trimIndent()

    private val videoRewardedFixture = """
        {
          "ad": {
            "id": "ad_video_rw_1",
            "type": "video_rewarded",
            "asset_url": null,
            "html": null,
            "native": null,
            "vast_url": null,
            "audio_url": null,
            "video_url": "https://cdn.adpluga.example/creatives/rw.mp4",
            "width": 640,
            "height": 360,
            "duration_ms": 30000,
            "skippable_after_ms": 5000,
            "reward_amount": 10,
            "reward_currency": "COIN",
            "format": "video_rewarded"
          },
          "impression_url": "https://edge.adpluga.example/v1/imp?t=rw",
          "click_url": "https://edge.adpluga.example/v1/click?t=rw",
          "conversion_url": null,
          "track_token": "tok_rw",
          "conversion_token": null,
          "source": "house",
          "quartile_pings": null
        }
    """.trimIndent()
}
