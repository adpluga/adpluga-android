package com.adpluga

import com.adpluga.mediation.MediationSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class MediationSlotTest {

    @Test
    fun `waterfall parameter is parsed in both shapes`() {
        val cases = listOf(
            "slot_abc" to MediationSlot("slot_abc"),
            "  slot_abc  " to MediationSlot("slot_abc"),
            """{"publisher_key":"pk_live_abcdefgh","slot_id":"s1"}""" to MediationSlot("s1", "pk_live_abcdefgh"),
            """{"slot_id":"s1","extra":true}""" to MediationSlot("s1"),
            """{"publisher_key":"pk_live_abcdefgh"}""" to null,
            """{"slot_id":""}""" to null,
            "{not json" to null,
            "" to null,
            "   " to null,
        )
        for ((raw, want) in cases) {
            assertEquals("raw=$raw", want, MediationSlot.parse(raw))
        }
        assertEquals(null, MediationSlot.parse(null))
    }
}
