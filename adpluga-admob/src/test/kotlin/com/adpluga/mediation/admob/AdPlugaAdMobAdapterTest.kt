package com.adpluga.mediation.admob

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.adpluga.AdPluga
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdPlugaAdMobAdapterTest {

    private lateinit var server: MockWebServer
    private val context: Context = ApplicationProvider.getApplicationContext()

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
    fun `adapter version folds the fourth part into micro`() {
        val cases = mapOf(
            "0.7.6.0" to Triple(0, 7, 600),
            "0.7.6.12" to Triple(0, 7, 612),
            "1.2.3" to Triple(1, 2, 3),
            "1.x.3" to Triple(0, 0, 0),
            "" to Triple(0, 0, 0),
        )
        for ((raw, want) in cases) {
            val v: VersionInfo = AdPlugaAdMobAdapter.parseVersion(raw)
            assertEquals(raw, want, Triple(v.majorVersion, v.minorVersion, v.microVersion))
        }
    }

    @Test
    fun `a parameter that names no slot fails every format`() {
        val banner = Recorder<MediationBannerAd, MediationBannerAdCallback>(MediationBannerAdCallback::class.java)
        AdPlugaAdMobAdapter().loadBannerAd(bannerConfig(""), banner)
        assertEquals(AdPlugaAdMobErrors.INVALID_PARAMETER, banner.failure?.code)

        val rewarded = Recorder<MediationRewardedAd, MediationRewardedAdCallback>(MediationRewardedAdCallback::class.java)
        AdPlugaAdMobAdapter().loadRewardedAd(rewardedConfig("{not json"), rewarded)
        assertEquals(AdPlugaAdMobErrors.INVALID_PARAMETER, rewarded.failure?.code)
    }

    @Test
    fun `the house fallback is reported as no fill so the waterfall moves on`() {
        initialize()
        server.enqueue(MockResponse().setBody(serveBody("house")))
        val rec = Recorder<MediationInterstitialAd, MediationInterstitialAdCallback>(MediationInterstitialAdCallback::class.java)
        AdPlugaAdMobAdapter().loadInterstitialAd(interstitialConfig("slot_1"), rec)
        awaitResult(rec)
        assertEquals(AdPlugaAdMobErrors.NO_FILL, rec.failure?.code)
        assertNull(rec.success)
    }

    @Test
    fun `paid demand fills the line item`() {
        initialize()
        server.enqueue(MockResponse().setBody(serveBody("pool")))
        val rec = Recorder<MediationInterstitialAd, MediationInterstitialAdCallback>(MediationInterstitialAdCallback::class.java)
        AdPlugaAdMobAdapter().loadInterstitialAd(interstitialConfig("slot_1"), rec)
        awaitResult(rec)
        assertNull(rec.failure)
        assertNotNull(rec.success)
        assertEquals("slot_1", server.takeRequest().requestUrl?.queryParameter("slot"))
    }

    private fun initialize() {
        AdPluga.initialize(publisherKey = "pk_test_abcdef123", endpoint = server.url("/").toString().trimEnd('/'))
    }

    private fun serveBody(source: String) = """
        {"ad":{"id":"a1","type":"image","asset_url":"https://cdn.example/a.png","width":320,"height":480},
         "click_url":"https://edge.example/c","track_token":"t","source":"$source"}
    """.trimIndent()

    private fun params(raw: String) = Bundle().apply { putString(AdPlugaAdMobAdapter.PARAMETER, raw) }

    private fun bannerConfig(raw: String) = MediationBannerAdConfiguration(
        context, "", params(raw), Bundle(), true, null, 0, 0, "", AdSize.BANNER, "",
    )

    private fun interstitialConfig(raw: String) = MediationInterstitialAdConfiguration(
        context, "", params(raw), Bundle(), true, null, 0, 0, "", "",
    )

    private fun rewardedConfig(raw: String) = MediationRewardedAdConfiguration(
        context, "", params(raw), Bundle(), true, null, 0, 0, "", "",
    )

    private fun awaitResult(rec: Recorder<*, *>) {
        val deadline = System.currentTimeMillis() + 5_000
        while (rec.success == null && rec.failure == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private class Recorder<A : Any, C : Any>(private val callbackType: Class<C>) : MediationAdLoadCallback<A, C> {
        @Volatile var success: A? = null
        @Volatile var failure: AdError? = null

        @Suppress("UNCHECKED_CAST")
        override fun onSuccess(ad: A): C {
            success = ad
            return Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, _, _ -> null } as C
        }

        override fun onFailure(error: AdError) {
            failure = error
        }

        override fun onFailure(error: String) {
            failure = AdError(0, error, "")
        }
    }
}
