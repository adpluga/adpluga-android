package com.adpluga.mediation

import com.adpluga.AdPluga
import com.adpluga.errors.AdPlugaError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What an AdPluga line item in another SDK's waterfall points at, read from
 * the parameter the publisher types into AdMob, MAX or LevelPlay.
 *
 * Two shapes are accepted:
 * - `{"publisher_key":"pk_live_…","slot_id":"…"}` — the adapter initialises
 *   AdPluga itself;
 * - a bare slot id — the app has already called [AdPluga.initialize].
 */
public data class MediationSlot(
    public val slotId: String,
    public val publisherKey: String? = null,
) {

    /** Returns the live instance, initialising it from [publisherKey] when needed. */
    public fun ensureInitialized(): AdPluga {
        AdPluga.maybeInstance?.let { return it }
        val key = publisherKey ?: throw AdPlugaError.NotInitialized
        return AdPluga.initialize(publisherKey = key)
    }

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses the waterfall parameter; null when it names no slot. */
        @JvmStatic
        public fun parse(raw: String?): MediationSlot? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (!text.startsWith("{")) return MediationSlot(slotId = text)
            val obj = try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return null
            val slot = obj["slot_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (slot.isEmpty()) return null
            val key = obj["publisher_key"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            return MediationSlot(slotId = slot, publisherKey = key)
        }
    }
}
