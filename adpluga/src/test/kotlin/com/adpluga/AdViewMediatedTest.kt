package com.adpluga

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.adpluga.errors.AdPlugaError
import com.adpluga.ui.AdView
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

// Regression: inside another SDK's waterfall the view drew the unpaid house
// creative (taking the slot from the networks below) and kept retrying and
// rotating on its own timer, spending decisions the host never asked for.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdViewMediatedTest {

    private lateinit var server: MockWebServer
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        AdPluga.initialize(publisherKey = "pk_test_abcdef123", endpoint = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        AdPluga.maybeInstance?.destroy()
        server.shutdown()
    }

    @Test
    fun `mediated view reports the house fallback as no fill and never retries`() {
        server.enqueue(MockResponse().setBody(houseBody))
        server.enqueue(MockResponse().setBody(houseBody))
        val errors = mutableListOf<Throwable>()
        var loaded = false
        AdView(context).apply {
            mediated = true
            load("slot_1", listener = object : AdListener {
                override fun onLoaded() { loaded = true }
                override fun onError(error: Throwable) { errors += error }
            })
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (errors.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(listOf<Throwable>(AdPlugaError.NoFill), errors)
        assertTrue(!loaded)
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MINUTES)
        assertEquals("a mediated view must not retry", 1, server.requestCount)
    }

    private val houseBody = """
        {"ad":{"id":"h1","type":"image","asset_url":"https://cdn.example/h.png","width":320,"height":50},
         "click_url":"https://edge.example/c","track_token":"t","source":"house","refresh_after_seconds":30}
    """.trimIndent()
}
