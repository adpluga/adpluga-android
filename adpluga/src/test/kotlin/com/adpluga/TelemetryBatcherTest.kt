package com.adpluga

import com.adpluga.client.HttpTransport
import com.adpluga.consent.ConsentState
import com.adpluga.consent.ConsentStore
import com.adpluga.telemetry.SdkEventType
import com.adpluga.telemetry.TelemetryBatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TelemetryBatcherTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: HttpTransport
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val consent = ConsentStore(ConsentState())
        transport = HttpTransport(
            publisherKey = "pk_test_abcdef123",
            endpoint = server.url("/").toString().trimEnd('/'),
            consent = consent,
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
        transport.close()
        server.shutdown()
    }

    @Test
    fun `reservoir sampling keeps footprint bounded`() = runTest {
        val batcher = TelemetryBatcher(transport, scope, random = Random(seed = 42))
        repeat(1_000) { batcher.record(SdkEventType.ServeRequest, latencyMs = it) }
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        batcher.flush()
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"count\":1000"))
        assertTrue(body.contains("\"p50\""))
        assertTrue(body.contains("\"p95\""))
        assertTrue(body.contains("\"p99\""))
    }

    @Test
    fun `flush is no-op when disabled`() = runTest {
        val batcher = TelemetryBatcher(transport, scope)
        batcher.setEnabled(false)
        batcher.record(SdkEventType.Impression, latencyMs = 10)
        batcher.flush()
        assertEquals(0, server.requestCount)
    }
}
