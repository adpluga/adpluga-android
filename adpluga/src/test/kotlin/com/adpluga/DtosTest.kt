package com.adpluga

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
}
