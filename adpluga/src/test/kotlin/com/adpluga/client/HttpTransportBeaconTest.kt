package com.adpluga.client

import com.adpluga.consent.ConsentState
import com.adpluga.consent.ConsentStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HttpTransportBeaconTest {

    private lateinit var api: MockWebServer
    private lateinit var ssp: MockWebServer
    private lateinit var transport: HttpTransport

    @Before
    fun setUp() {
        api = MockWebServer().apply { start() }
        ssp = MockWebServer().apply { start() }
        transport = HttpTransport(
            publisherKey = "pk_test_abcdef123",
            endpoint = "http://127.0.0.1:${api.port}",
            consent = ConsentStore(ConsentState()),
            tcfSource = { null },
            userAgentSource = { DEVICE_UA },
        )
    }

    @After
    fun tearDown() {
        transport.close()
        api.shutdown()
        ssp.shutdown()
    }

    @Test
    fun `serve sends the device user agent`() = runTest {
        api.enqueue(MockResponse().setResponseCode(400))
        transport.serve(slotId = "slot_1", format = null, userHash = null)
        assertEquals(DEVICE_UA, api.takeRequest().getHeader("X-Device-User-Agent"))
    }

    @Test
    fun `third-party pixel gets the device UA and never the publisher key`() = runTest {
        ssp.enqueue(MockResponse().setResponseCode(204))
        transport.beacon("http://localhost:${ssp.port}/imp")
        val recorded = ssp.takeRequest()
        assertNull(recorded.getHeader("X-AdPluga-Key"))
        assertNull(recorded.getHeader("X-Adpluga-Sdk-Platform"))
        assertEquals(DEVICE_UA, recorded.getHeader("User-Agent"))
    }

    @Test
    fun `first-party beacon keeps the key headers`() = runTest {
        api.enqueue(MockResponse().setResponseCode(204))
        transport.beacon("/v1/imp?t=x")
        assertEquals("pk_test_abcdef123", api.takeRequest().getHeader("X-AdPluga-Key"))
    }

    private companion object {
        const val DEVICE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
    }
}
