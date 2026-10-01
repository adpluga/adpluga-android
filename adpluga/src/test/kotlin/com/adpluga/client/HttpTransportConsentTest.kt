package com.adpluga.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adpluga.consent.AppContextHolder
import com.adpluga.consent.ConsentState
import com.adpluga.consent.ConsentStore
import com.adpluga.consent.IabTcfStorage
import com.adpluga.consent.Tcf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HttpTransportConsentTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private data class Case(
        val name: String,
        val consent: ConsentState,
        val stored: Tcf?,
        val gdpr: String?,
        val header: String?,
    )

    @Test
    fun `serve forwards gdpr and the TCF consent string`() = runTest {
        val cases = listOf(
            Case(
                name = "explicit values win over stored ones",
                consent = ConsentState(gdpr = true, tcfString = "CPexplicit"),
                stored = Tcf(gdprApplies = false, tcString = "CPstored"),
                gdpr = "1",
                header = "CPexplicit",
            ),
            Case(
                name = "not stated falls back to stored values",
                consent = ConsentState(),
                stored = Tcf(gdprApplies = false, tcString = "CPstored"),
                gdpr = "0",
                header = "CPstored",
            ),
            Case(
                name = "explicit gdpr with stored string",
                consent = ConsentState(gdpr = true),
                stored = Tcf(gdprApplies = false, tcString = "CPstored"),
                gdpr = "1",
                header = "CPstored",
            ),
            Case(
                name = "nothing known omits both",
                consent = ConsentState(),
                stored = null,
                gdpr = null,
                header = null,
            ),
            Case(
                name = "blank explicit string falls back to stored",
                consent = ConsentState(tcfString = "  "),
                stored = Tcf(gdprApplies = true, tcString = "CPstored"),
                gdpr = "1",
                header = "CPstored",
            ),
            Case(
                name = "blank everywhere omits the header",
                consent = ConsentState(tcfString = ""),
                stored = IabTcfStorage.parse(null, " "),
                gdpr = null,
                header = null,
            ),
        )
        for (case in cases) {
            server.enqueue(MockResponse().setResponseCode(400))
            val transport = HttpTransport(
                publisherKey = "pk_test_abcdef123",
                endpoint = server.url("/").toString().trimEnd('/'),
                consent = ConsentStore(case.consent),
                tcfSource = { case.stored },
            )
            try {
                transport.serve(slotId = "slot_1", format = null, userHash = null)
            } finally {
                transport.close()
            }
            val recorded = server.takeRequest()
            assertEquals(case.name, case.gdpr, recorded.requestUrl!!.queryParameter("gdpr"))
            assertEquals(case.name, case.header, recorded.getHeader("X-Consent-String"))
        }
    }

    @Test
    fun `IABTCF values are parsed whatever type the CMP stored`() {
        val cases = listOf(
            Triple<Any?, Any?, Tcf?>(1, "CP", Tcf(true, "CP")),
            Triple(0, null, Tcf(false, null)),
            Triple(1L, null, Tcf(true, null)),
            Triple("1", null, Tcf(true, null)),
            Triple("0", "", Tcf(false, null)),
            Triple(-1, null, null),
            Triple(null, "  ", null),
            Triple(null, null, null),
            Triple(true, null, null),
        )
        for ((applies, tc, want) in cases) {
            assertEquals("applies=$applies tc=$tc", want, IabTcfStorage.parse(applies, tc))
        }
    }

    @Test
    fun `IABTCF keys are read from the default shared preferences`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString(IabTcfStorage.KEY_GDPR_APPLIES, "1")
            .putString(IabTcfStorage.KEY_TC_STRING, "CPfromCmp")
            .commit()
        AppContextHolder.remember(app)
        assertEquals(Tcf(true, "CPfromCmp"), IabTcfStorage.read())
    }
}
