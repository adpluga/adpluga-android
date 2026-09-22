package com.adpluga

import com.adpluga.model.AdKind
import com.adpluga.model.AdPlugaJson
import com.adpluga.model.ServeResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtosTest {
    @Test
    fun `native assets are read from flat contract fields`() {
        val json = """
            {
              "ad": {
                "id": "ad-1",
                "type": "native",
                "title": "Promo",
                "body": "Descontos",
                "cta_text": "Comprar",
                "sponsored_by": "AdPluga",
                "icon_url": "https://cdn/icon.png",
                "main_image_url": "https://cdn/main.png"
              },
              "track_token": "tok",
              "source": "pool"
            }
        """.trimIndent()

        val model = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel()
        val assets = model.ad.nativeAssets
        assertEquals("Promo", assets?.get("title"))
        assertEquals("Descontos", assets?.get("body"))
        assertEquals("Comprar", assets?.get("cta_text"))
        assertEquals("AdPluga", assets?.get("sponsored_by"))
        assertEquals("https://cdn/icon.png", assets?.get("icon_url"))
        assertEquals("https://cdn/main.png", assets?.get("main_image_url"))
    }

    @Test
    fun `flat fields win over legacy nested native`() {
        val json = """
            {
              "ad": {
                "id": "ad-2",
                "type": "native",
                "title": "Flat",
                "native": { "title": "Nested" }
              },
              "track_token": "tok",
              "source": "pool"
            }
        """.trimIndent()

        val model = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel()
        assertEquals("Flat", model.ad.nativeAssets?.get("title"))
    }

    @Test
    fun `no native fields yields null assets`() {
        val json = """
            { "ad": { "id": "ad-3", "type": "image" }, "track_token": "tok", "source": "house" }
        """.trimIndent()
        val model = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel()
        assertNull(model.ad.nativeAssets)
    }

    @Test
    fun `test flag is parsed from serve response`() {
        val json = """
            { "ad": { "id": "ad-t", "type": "image", "test": true }, "track_token": "tok", "source": "test" }
        """.trimIndent()
        val model = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel()
        assertTrue(model.ad.isTest)
    }

    @Test
    fun `test flag defaults to false when absent`() {
        val json = """
            { "ad": { "id": "ad-t2", "type": "image" }, "track_token": "tok", "source": "house" }
        """.trimIndent()
        val model = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel()
        assertFalse(model.ad.isTest)
    }

    @Test
    fun `rotation cadence is parsed from the serve response`() {
        val json = """
            {
              "ad": { "id": "ad-1", "type": "image" },
              "track_token": "tok",
              "source": "house",
              "refresh_after_seconds": 60
            }
        """.trimIndent()
        val dto = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json)
        assertEquals(60, dto.toModel().refreshAfterSeconds)
    }

    @Test
    fun `slot without a cadence never rotates`() {
        val json = """
            {
              "ad": { "id": "ad-1", "type": "image" },
              "track_token": "tok",
              "source": "house"
            }
        """.trimIndent()
        val dto = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json)
        assertEquals(0, dto.toModel().refreshAfterSeconds)
    }

    @Test
    fun `carousel deck is parsed in order and slides without a creative are dropped`() {
        val json = """
            {
              "ad": {
                "id": "ad-2",
                "type": "carousel",
                "width": 300,
                "height": 250,
                "slides": [
                  {"asset_url": "https://cdn/1.png", "title": "Card 1", "cta_text": "Ver"},
                  {"asset_url": ""},
                  {"asset_url": "https://cdn/2.png", "body": "Segundo"}
                ]
              },
              "track_token": "tok",
              "source": "pool"
            }
        """.trimIndent()

        val ad = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel().ad
        assertEquals(AdKind.CAROUSEL, ad.kind)
        assertEquals(2, ad.slides.size)
        assertEquals("https://cdn/1.png", ad.slides[0].assetUrl)
        assertEquals("Card 1", ad.slides[0].title)
        assertEquals("Ver", ad.slides[0].ctaText)
        assertEquals("https://cdn/2.png", ad.slides[1].assetUrl)
        assertEquals("Segundo", ad.slides[1].body)
    }

    @Test
    fun `every other creative type carries an empty deck`() {
        val json = """
            {
              "ad": {"id": "ad-3", "type": "image", "asset_url": "https://cdn/b.png"},
              "track_token": "tok",
              "source": "pool"
            }
        """.trimIndent()

        val ad = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), json).toModel().ad
        assertTrue(ad.slides.isEmpty())
    }

    // An unlabelled ad image is a link with no accessible name, so the label
    // has to survive the wire and fall back when the advertiser wrote nothing.
    @Test
    fun `alternative text is parsed and falls back to the title`() {
        val withAlt = """
            {
              "ad": {
                "id": "ad-1",
                "type": "image",
                "asset_url": "https://cdn.example/b.png",
                "title": "Promo",
                "alt_text": "Perfumes a 20 por cento"
              },
              "source": "direto",
              "track_token": "t"
            }
        """.trimIndent()
        val parsed = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), withAlt)
            .toModel()
        assertEquals("Perfumes a 20 por cento", parsed.ad.altText)

        val withoutAlt = """
            {
              "ad": {
                "id": "ad-2",
                "type": "image",
                "asset_url": "https://cdn.example/b.png",
                "title": "Promo"
              },
              "source": "direto",
              "track_token": "t"
            }
        """.trimIndent()
        val bare = AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), withoutAlt)
            .toModel()
        assertNull(bare.ad.altText)
    }
}
